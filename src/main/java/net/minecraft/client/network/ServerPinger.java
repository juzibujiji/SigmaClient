package net.minecraft.client.network;

import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.mojang.authlib.GameProfile;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.nio.NioSocketChannel;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.multiplayer.ServerAddress;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.network.status.IClientStatusNetHandler;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.ProtocolType;
import net.minecraft.network.ServerStatusResponse;
import net.minecraft.network.handshake.client.CHandshakePacket;
import net.minecraft.network.status.client.CPingPacket;
import net.minecraft.network.status.client.CServerQueryPacket;
import net.minecraft.network.status.server.SPongPacket;
import net.minecraft.network.status.server.SServerInfoPacket;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ServerPinger
{
    private static final Splitter PING_RESPONSE_SPLITTER =
            Splitter.on('\u0000').limit(6);

    private static final Logger LOGGER = LogManager.getLogger();

    private final List<NetworkManager> pingDestinations =
            Collections.synchronizedList(Lists.newArrayList());

    private static ITextComponent func_239171_b_(int online, int max)
    {
        return new StringTextComponent(Integer.toString(online))
                .append(
                        new StringTextComponent("/")
                                .mergeStyle(TextFormatting.DARK_GRAY)
                )
                .appendString(Integer.toString(max))
                .mergeStyle(TextFormatting.GRAY);
    }

    public void ping(
            final ServerData server,
            final Runnable updateCallback
    ) throws UnknownHostException
    {
        ServerAddress serverAddress =
                ServerAddress.fromString(server.serverIP);

        final NetworkManager networkManager =
                NetworkManager.createNetworkManagerAndConnect(
                        InetAddress.getByName(serverAddress.getIP()),
                        serverAddress.getPort(),
                        false
                );

        this.pingDestinations.add(networkManager);

        server.serverMOTD =
                new TranslationTextComponent("multiplayer.status.pinging");
        server.pingToServer = -1L;
        server.playerList = null;

        networkManager.setNetHandler(new IClientStatusNetHandler()
        {
            private boolean successful;
            private boolean receivedStatus;
            private long pingSentAt;

            @Override
            public void handleServerInfo(SServerInfoPacket packet)
            {
                if (this.receivedStatus)
                {
                    networkManager.closeChannel(
                            new TranslationTextComponent(
                                    "multiplayer.status.unrequested"
                            )
                    );

                    return;
                }

                this.receivedStatus = true;

                ServerStatusResponse response = packet.getResponse();

                if (response.getServerDescription() != null)
                {
                    server.serverMOTD = response.getServerDescription();
                }
                else
                {
                    server.serverMOTD = StringTextComponent.EMPTY;
                }

                if (response.getVersion() != null)
                {
                    server.gameVersion =
                            new StringTextComponent(
                                    response.getVersion().getName()
                            );

                    server.version =
                            response.getVersion().getProtocol();
                }
                else
                {
                    server.gameVersion =
                            new TranslationTextComponent(
                                    "multiplayer.status.old"
                            );

                    server.version = 0;
                }

                if (response.getPlayers() != null)
                {
                    server.populationInfo = func_239171_b_(
                            response.getPlayers().getOnlinePlayerCount(),
                            response.getPlayers().getMaxPlayers()
                    );

                    List<ITextComponent> playerList =
                            Lists.newArrayList();

                    if (ArrayUtils.isNotEmpty(
                            response.getPlayers().getPlayers()
                    ))
                    {
                        for (GameProfile profile :
                                response.getPlayers().getPlayers())
                        {
                            playerList.add(
                                    new StringTextComponent(
                                            profile.getName()
                                    )
                            );
                        }

                        int sampleSize =
                                response.getPlayers()
                                        .getPlayers()
                                        .length;

                        int onlinePlayers =
                                response.getPlayers()
                                        .getOnlinePlayerCount();

                        if (sampleSize < onlinePlayers)
                        {
                            playerList.add(
                                    new TranslationTextComponent(
                                            "multiplayer.status.and_more",
                                            onlinePlayers - sampleSize
                                    )
                            );
                        }

                        server.playerList = playerList;
                    }
                }
                else
                {
                    server.populationInfo =
                            new TranslationTextComponent(
                                    "multiplayer.status.unknown"
                            ).mergeStyle(TextFormatting.DARK_GRAY);
                }

                String favicon = null;

                if (response.getFavicon() != null)
                {
                    String faviconData = response.getFavicon();

                    if (faviconData.startsWith(
                            "data:image/png;base64,"
                    ))
                    {
                        favicon = faviconData.substring(
                                "data:image/png;base64,".length()
                        );
                    }
                    else
                    {
                        LOGGER.error(
                                "Invalid server icon (unknown format)"
                        );
                    }
                }

                if (!Objects.equals(
                        favicon,
                        server.getBase64EncodedIconData()
                ))
                {
                    server.setBase64EncodedIconData(favicon);
                    updateCallback.run();
                }

                this.pingSentAt = Util.milliTime();

                networkManager.sendPacket(
                        new CPingPacket(this.pingSentAt)
                );

                this.successful = true;
            }

            @Override
            public void handlePong(SPongPacket packet)
            {
                long now = Util.milliTime();

                server.pingToServer =
                        now - this.pingSentAt;

                networkManager.closeChannel(
                        new TranslationTextComponent(
                                "multiplayer.status.finished"
                        )
                );
            }

            @Override
            public void onDisconnect(ITextComponent reason)
            {
                if (!this.successful)
                {
                    LOGGER.error(
                            "Can't ping {}: {}",
                            server.serverIP,
                            reason.getString()
                    );

                    server.serverMOTD =
                            new TranslationTextComponent(
                                    "multiplayer.status.cannot_connect"
                            ).mergeStyle(TextFormatting.DARK_RED);

                    server.populationInfo =
                            StringTextComponent.EMPTY;

                    ServerPinger.this.tryCompatibilityPing(server);
                }
            }

            @Override
            public NetworkManager getNetworkManager()
            {
                return networkManager;
            }
        });

        try
        {
            networkManager.sendPacket(
                    new CHandshakePacket(
                            serverAddress.getIP(),
                            serverAddress.getPort(),
                            ProtocolType.STATUS
                    )
            );

            networkManager.sendPacket(
                    new CServerQueryPacket()
            );
        }
        catch (Throwable throwable)
        {
            LOGGER.error(
                    "Failed to send server status query to {}",
                    server.serverIP,
                    throwable
            );
        }
    }

    /**
     * 旧版本服务器兼容 Ping。
     *
     * 最小修复：
     * Netty NIO Selector 创建失败时吞掉异常，
     * 避免异常进入 MultiplayerScreen.tick() 导致整个客户端崩溃。
     */
    private void tryCompatibilityPing(final ServerData server)
    {
        try
        {
            final ServerAddress serverAddress =
                    ServerAddress.fromString(server.serverIP);

            new Bootstrap()
                    .group(
                            NetworkManager.CLIENT_NIO_EVENTLOOP
                                    .getValue()
                    )
                    .handler(new ChannelInitializer<Channel>()
                    {
                        @Override
                        protected void initChannel(Channel channel)
                        {
                            try
                            {
                                channel.config().setOption(
                                        ChannelOption.TCP_NODELAY,
                                        true
                                );
                            }
                            catch (ChannelException ignored)
                            {
                            }

                            channel.pipeline().addLast(
                                    new SimpleChannelInboundHandler<ByteBuf>()
                                    {
                                        @Override
                                        public void channelActive(
                                                ChannelHandlerContext context
                                        ) throws Exception
                                        {
                                            super.channelActive(context);

                                            ByteBuf buffer =
                                                    Unpooled.buffer();

                                            try
                                            {
                                                buffer.writeByte(254);
                                                buffer.writeByte(1);
                                                buffer.writeByte(250);

                                                char[] channelName =
                                                        "MC|PingHost"
                                                                .toCharArray();

                                                buffer.writeShort(
                                                        channelName.length
                                                );

                                                for (char character :
                                                        channelName)
                                                {
                                                    buffer.writeChar(
                                                            character
                                                    );
                                                }

                                                String address =
                                                        serverAddress.getIP();

                                                buffer.writeShort(
                                                        7
                                                                + 2
                                                                * address.length()
                                                );

                                                buffer.writeByte(127);

                                                char[] addressCharacters =
                                                        address.toCharArray();

                                                buffer.writeShort(
                                                        addressCharacters.length
                                                );

                                                for (char character :
                                                        addressCharacters)
                                                {
                                                    buffer.writeChar(
                                                            character
                                                    );
                                                }

                                                buffer.writeInt(
                                                        serverAddress.getPort()
                                                );

                                                context.channel()
                                                        .writeAndFlush(buffer)
                                                        .addListener(
                                                                ChannelFutureListener
                                                                        .CLOSE_ON_FAILURE
                                                        );

                                                /*
                                                 * writeAndFlush 成功后，
                                                 * ByteBuf 交由 Netty 管理。
                                                 */
                                                buffer = null;
                                            }
                                            finally
                                            {
                                                /*
                                                 * 只有尚未交给 Netty 时才释放，
                                                 * 避免重复 release。
                                                 */
                                                if (buffer != null)
                                                {
                                                    buffer.release();
                                                }
                                            }
                                        }

                                        @Override
                                        protected void channelRead0(
                                                ChannelHandlerContext context,
                                                ByteBuf buffer
                                        )
                                        {
                                            short packetId =
                                                    buffer.readUnsignedByte();

                                            if (packetId == 255)
                                            {
                                                int stringLength =
                                                        buffer.readShort()
                                                                * 2;

                                                String response =
                                                        new String(
                                                                buffer.readBytes(
                                                                        stringLength
                                                                ).array(),
                                                                StandardCharsets
                                                                        .UTF_16BE
                                                        );

                                                String[] parts =
                                                        Iterables.toArray(
                                                                PING_RESPONSE_SPLITTER
                                                                        .split(
                                                                                response
                                                                        ),
                                                                String.class
                                                        );

                                                if (parts.length >= 6
                                                        && "\u00a71".equals(
                                                                parts[0]
                                                        ))
                                                {
                                                    String version =
                                                            parts[2];

                                                    String motd =
                                                            parts[3];

                                                    int online =
                                                            MathHelper.getInt(
                                                                    parts[4],
                                                                    -1
                                                            );

                                                    int maximum =
                                                            MathHelper.getInt(
                                                                    parts[5],
                                                                    -1
                                                            );

                                                    server.version = -1;

                                                    server.gameVersion =
                                                            new StringTextComponent(
                                                                    version
                                                            );

                                                    server.serverMOTD =
                                                            new StringTextComponent(
                                                                    motd
                                                            );

                                                    server.populationInfo =
                                                            func_239171_b_(
                                                                    online,
                                                                    maximum
                                                            );
                                                }
                                            }

                                            context.close();
                                        }

                                        @Override
                                        public void exceptionCaught(
                                                ChannelHandlerContext context,
                                                Throwable throwable
                                        )
                                        {
                                            LOGGER.debug(
                                                    "Legacy ping channel failed for {}",
                                                    server.serverIP,
                                                    throwable
                                            );

                                            context.close();
                                        }
                                    }
                            );
                        }
                    })
                    .channel(NioSocketChannel.class)
                    .connect(
                            serverAddress.getIP(),
                            serverAddress.getPort()
                    )
                    .addListener(
                            (ChannelFutureListener) future ->
                            {
                                if (!future.isSuccess())
                                {
                                    LOGGER.debug(
                                            "Legacy ping connection failed for {}",
                                            server.serverIP,
                                            future.cause()
                                    );
                                }
                            }
                    );
        }
        catch (Throwable throwable)
        {
            LOGGER.warn(
                    "Compatibility ping could not be started for {}",
                    server.serverIP,
                    throwable
            );

            server.pingToServer = -1L;

            server.serverMOTD =
                    new TranslationTextComponent(
                            "multiplayer.status.cannot_connect"
                    ).mergeStyle(TextFormatting.DARK_RED);

            server.populationInfo =
                    StringTextComponent.EMPTY;
        }
    }

    public void pingPendingNetworks()
    {
        synchronized (this.pingDestinations)
        {
            Iterator<NetworkManager> iterator =
                    this.pingDestinations.iterator();

            while (iterator.hasNext())
            {
                NetworkManager networkManager =
                        iterator.next();

                if (networkManager.isChannelOpen())
                {
                    networkManager.tick();
                }
                else
                {
                    iterator.remove();
                    networkManager.handleDisconnection();
                }
            }
        }
    }

    public void clearPendingNetworks()
    {
        synchronized (this.pingDestinations)
        {
            Iterator<NetworkManager> iterator =
                    this.pingDestinations.iterator();

            while (iterator.hasNext())
            {
                NetworkManager networkManager =
                        iterator.next();

                if (networkManager.isChannelOpen())
                {
                    iterator.remove();

                    networkManager.closeChannel(
                            new TranslationTextComponent(
                                    "multiplayer.status.cancelled"
                            )
                    );
                }
            }
        }
    }
}