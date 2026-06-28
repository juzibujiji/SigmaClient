package com.mentalfrostbyte.jello.module.impl.gui.jello.irc;

import com.mentalfrostbyte.Client;
import com.mentalfrostbyte.jello.managers.util.notifs.Notification;
import com.mentalfrostbyte.jello.module.impl.gui.jello.irc.packet.*;

import java.io.*;
import java.net.Socket;

/**
 * IRC客户端连接类（基于Packet系统）
 * 负责连接到服务器并收发消息
 */
public class IRCClientConnection implements PacketHandler {
    private Socket socket;
    private DataInputStream input;
    private DataOutputStream output;
    private volatile boolean connected;
    private Thread receiveThread;
    private Thread heartbeatThread;
    private String serverAddress;
    private int serverPort;
    private String username;

    private static final int HEARTBEAT_INTERVAL_MS = 10000; // 每10秒发一次心跳

    public IRCClientConnection(String serverAddress, int serverPort, String username) {
        this.serverAddress = serverAddress;
        this.serverPort = serverPort;
        this.username = username;
        this.connected = false;
    }

    /**
     * 连接到服务器（异步）
     */
    public boolean connect() {
        if (connected) {
            IRCUtlis.printMessage("[IRC] Already connected to server");
            return false;
        }

        // 在新线程中执行连接，避免阻塞主线程
        Thread connectThread = new Thread(() -> {
            try {
                IRCUtlis.printMessage("[IRC] Connecting to " + serverAddress + ":" + serverPort + "...");

                // 创建Socket并设置超时
                socket = new Socket();
                socket.connect(new java.net.InetSocketAddress(serverAddress, serverPort), 5000); // 5秒超时
                socket.setSoTimeout(30000); // 读取超时30秒

                input = new DataInputStream(socket.getInputStream());
                output = new DataOutputStream(socket.getOutputStream());
                connected = true;

                IRCUtlis.printMessage("[IRC] Connected to server: " + serverAddress + ":" + serverPort);

                // 发送登录包
                PacketLogin loginPacket = new PacketLogin(username, "1.0");
                sendPacket(loginPacket);

                // 启动接收消息的线程
                startReceiving();
                startHeartbeat(); // 关键修复：启动主动心跳，避免读超时误判断线

            } catch (java.net.SocketTimeoutException e) {
                IRCUtlis.printMessage("[IRC] Connection timeout - server not responding");
                connected = false;
            } catch (java.net.UnknownHostException e) {
                IRCUtlis.printMessage("[IRC] Unknown host: " + serverAddress);
                connected = false;
            } catch (java.net.ConnectException e) {
                IRCUtlis.printMessage("[IRC] Connection refused - server may be offline");
                connected = false;
            } catch (IOException e) {
                IRCUtlis.printMessage("[IRC] Failed to connect: " + e.getMessage());
                connected = false;
            }
        });
        connectThread.setDaemon(true);
        connectThread.start();

        return true; // 返回true表示连接尝试已开始
    }

    /**
     * 启动接收消息的线程
     */
    private void startReceiving() {
        receiveThread = new Thread(() -> {
            try {
                while (connected) {
                    IRCPacket packet = PacketRegistry.receivePacket(input);
                    if (packet != null) {
                        packet.handle(this);
                    }
                }
            } catch (java.net.SocketTimeoutException e) {
                // 正常情况下心跳会刷新读取，真正走到这里说明确实长时间没有任何数据（包括心跳）
                if (connected) {
                    IRCUtlis.printMessage("[IRC] Connection timed out (no data/heartbeat received)");
                }
            } catch (IOException e) {
                if (connected) {
                    IRCUtlis.printMessage("[IRC] Error receiving packet: " + e.getMessage());
                }
            } finally {
                disconnect();
            }
        });
        receiveThread.setDaemon(true);
        receiveThread.start();
    }

    /**
     * 主动发送心跳，防止连接因长时间无数据而被读超时判定为断开
     */
    private void startHeartbeat() {
        heartbeatThread = new Thread(() -> {
            while (connected) {
                try {
                    Thread.sleep(HEARTBEAT_INTERVAL_MS);
                    if (connected) {
                        sendPacket(new PacketKeepAlive());
                    }
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
        heartbeatThread.setDaemon(true);
        heartbeatThread.start();
    }

    public void sendPacket(IRCPacket packet) {
        try {
            if (output != null && connected) {
                synchronized (output) {
                    PacketRegistry.sendPacket(packet, output);
                }
            }
        } catch (IOException e) {
            IRCUtlis.printMessage("[IRC] Error sending packet: " + e.getMessage());
            disconnect();
        }
    }

    /**
     * 发送聊天消息
     */
    public void sendMessage(String message) {
        if (!connected) {
            IRCUtlis.printMessage("[IRC] Not connected to server");
            return;
        }

        String safeMessage = IRCChatHistory.sanitizeMessage(message);
        if (safeMessage.isEmpty()) {
            return;
        }

        PacketChatMessage packet = new PacketChatMessage(username, safeMessage);
        sendPacket(packet);
    }

    /**
     * 断开与服务器的连接
     */
    public void disconnect() {
        if (!connected) return;

        try {
            // 关键修复：必须在 connected 仍为 true 时发送登出包，
            // 否则 sendPacket() 内部判断 connected 会导致包发不出去
            PacketLogout logoutPacket = new PacketLogout(username, "User disconnect");
            sendPacket(logoutPacket);

            Thread.sleep(100); // 等待包发送
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            connected = false;

            if (heartbeatThread != null) {
                heartbeatThread.interrupt();
                heartbeatThread = null;
            }

            try {
                if (input != null) input.close();
                if (output != null) output.close();
                if (socket != null) socket.close();
            } catch (IOException e) {
                IRCUtlis.printMessage("[IRC] Error closing socket: " + e.getMessage());
            }

            IRCUtlis.printMessage("[IRC] Disconnected from server");
        }
    }

    public boolean isConnected() {
        return connected && socket != null && !socket.isClosed();
    }

    /**
     * 获取用户名
     */
    public String getUsername() {
        return username;
    }

    /**
     * 获取服务器地址
     */
    public String getServerAddress() {
        return serverAddress + ":" + serverPort;
    }

    // PacketHandler实现

    @Override
    public void handleChatMessage(PacketChatMessage packet) {
        IRCChatHistory.Entry entry = IRCChatHistory.addChat(packet.getUsername(), packet.getMessage(), packet.getTimestamp());
        String msg = "[IRC] " + entry.getUsername() + ": " + entry.getMessage();
        IRCUtlis.printMessage(msg, false);

        if (IRCManager.shouldNotify() && !entry.getUsername().equalsIgnoreCase(IRCChatHistory.sanitizeUsername(this.username))) {
            Client.getInstance().notificationManager.send(new Notification("IRC", shorten(entry.getUsername() + ": " + entry.getMessage(), 120), 4500));
        }
    }

    @Override
    public void handleLogin(PacketLogin packet) {
        IRCUtlis.printMessage("[IRC] " + IRCChatHistory.sanitizeUsername(packet.getUsername()) + " joined the chat");
    }

    @Override
    public void handleLogout(PacketLogout packet) {
        IRCUtlis.printMessage("[IRC] " + IRCChatHistory.sanitizeUsername(packet.getUsername()) + " left: " + IRCChatHistory.sanitizeMessage(packet.getReason()));
    }

    @Override
    public void handleKeepAlive(PacketKeepAlive packet) {
        // 收到对方心跳，不需要再额外回复，避免双方无限互相回应
        // （我们已经有自己的定时心跳线程在主动发送）
    }
<<<<<<< Updated upstream

    private static String shorten(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }

        return text.substring(0, Math.max(0, maxLength - 3)) + "...";
    }
}
=======
}
>>>>>>> Stashed changes
