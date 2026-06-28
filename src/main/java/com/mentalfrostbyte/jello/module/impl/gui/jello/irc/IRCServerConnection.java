package com.mentalfrostbyte.jello.module.impl.gui.jello.irc;

import com.mentalfrostbyte.jello.module.impl.gui.jello.irc.packet.*;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * IRC服务端连接类（基于Packet系统）
 * 负责监听端口并接受客户端连接
 */
public class IRCServerConnection {
    private ServerSocket serverSocket;
    private boolean running;
    private int port;
    private Thread serverThread;
    private List<ClientHandler> clients = new CopyOnWriteArrayList<>();

    public IRCServerConnection(int port) {
        this.port = port;
        this.running = false;
    }

    /**
     * 启动服务器
     */
    public void start() {
        if (running) {
            IRCUtlis.printMessage("[IRC] Server already running");
            return;
        }

        serverThread = new Thread(() -> {
            try {
                serverSocket = new ServerSocket(port);
                running = true;
                IRCUtlis.printMessage("[IRC] Server started on port: " + port);

                while (running) {
                    Socket clientSocket = serverSocket.accept();
                    IRCUtlis.printMessage("[IRC] New client connected: " + clientSocket.getInetAddress());
                    
                    ClientHandler handler = new ClientHandler(clientSocket);
                    clients.add(handler);
                    new Thread(handler).start();
                }
            } catch (IOException e) {
                if (running) {
                    IRCUtlis.printMessage("[IRC] Server error: " + e.getMessage());
                }
            }
        });
        serverThread.start();
    }

    /**
     * 停止服务器
     */
    public void stop() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            for (ClientHandler client : clients) {
                client.disconnect();
            }
            clients.clear();
            IRCUtlis.printMessage("[IRC] Server stopped");
        } catch (IOException e) {
            IRCUtlis.printMessage("[IRC] Error stopping server: " + e.getMessage());
        }
    }

    /**
     * 检查服务器是否在运行
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * 广播包给所有客户端
     */
    private void broadcastPacket(IRCPacket packet, ClientHandler sender) {
        for (ClientHandler client : clients) {
            if (/*client != sender && */client.isConnected()) {
                client.sendPacket(packet);
            }
        }
    }

    /**
     * 客户端处理器
     */
    private class ClientHandler implements Runnable, PacketHandler {
        private Thread heartbeatThread;
        private static final int HEARTBEAT_INTERVAL_MS = 10000;
        private Socket socket;
        private DataInputStream input;
        private DataOutputStream output;
        private boolean connected;
        private String username;

        public ClientHandler(Socket socket) {
            this.socket = socket;
            this.connected = true;
            this.username = "Unknown";
        }

        @Override
        public void run() {
            try {
                input = new DataInputStream(socket.getInputStream());
                output = new DataOutputStream(socket.getOutputStream());

                startHeartbeat(); // 新增：服务端也主动发心跳

                while (connected) {
                    IRCPacket packet = PacketRegistry.receivePacket(input);
                    if (packet != null) {
                        packet.handle(this);
                    }
                }
            } catch (IOException e) {
                if (connected) {
                    IRCUtlis.printMessage("[IRC] Client connection error: " + e.getMessage());
                }
            } finally {
                disconnect();
            }
        }

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

        public void disconnect() {
            if (!connected) return;

            connected = false;
            if (heartbeatThread != null) {
                heartbeatThread.interrupt();
                heartbeatThread = null;
            }
            try {
                if (input != null) input.close();
                if (output != null) output.close();
                if (socket != null) socket.close();
                clients.remove(this);

                // 广播用户离开
                PacketLogout logoutPacket = new PacketLogout(username, "Disconnected");
                broadcastPacket(logoutPacket, this);

                IRCUtlis.printMessage("[IRC] Client disconnected: " + username);
            } catch (IOException e) {
                IRCUtlis.printMessage("[IRC] Error disconnecting: " + e.getMessage());
            }
        }

        public boolean isConnected() {
            return connected && socket != null && !socket.isClosed();
        }

        @Override
        public void handleChatMessage(PacketChatMessage packet) {
            IRCUtlis.printMessage("[IRC] " + packet.getUsername() + ": " + packet.getMessage());
            // 广播给所有其他客户端
            broadcastPacket(packet, this);
        }

        @Override
        public void handleLogin(PacketLogin packet) {
            this.username = packet.getUsername();
            IRCUtlis.printMessage("[IRC] " + username + " logged in (version: " + packet.getVersion() + ")");
            
            // 广播用户加入
            PacketChatMessage welcomePacket = new PacketChatMessage("Server", username + " joined the chat");
            broadcastPacket(welcomePacket, this);
            
            // 发送欢迎消息给新用户
            PacketChatMessage privateWelcome = new PacketChatMessage("Server", "Welcome to IRC Server!");
            sendPacket(privateWelcome);
        }

        @Override
        public void handleLogout(PacketLogout packet) {
            IRCUtlis.printMessage("[IRC] " + packet.getUsername() + " logged out: " + packet.getReason());
            disconnect();
        }

        @Override
        public void handleKeepAlive(PacketKeepAlive packet) {
            // 收到客户端心跳即可，不必再回应，服务端自己有独立心跳线程
        }
    }
}
