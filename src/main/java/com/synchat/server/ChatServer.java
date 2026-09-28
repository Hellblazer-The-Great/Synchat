package com.synchat.server;

import com.synchat.db.Database;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.SQLException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * CONCURRENCY - the core of the multi-user server.
 *
 * The server listens on ONE accept thread, but every client connection is
 * handed off to a cached THREAD POOL (ExecutorService), each running its
 * own ClientHandler task. This is what lets many users - whether several
 * app instances on this machine, or several devices on the same Wi-Fi
 * network - chat at the same time without blocking one another.
 *
 * Passing `null` as the bind address makes the ServerSocket listen on
 * 0.0.0.0 (all network interfaces), so any device on the current Wi-Fi
 * network can reach it via this machine's local IP address.
 */
public class ChatServer {
    private static ChatServer instance;

    private ServerSocket serverSocket;
    private final ExecutorService clientPool = Executors.newCachedThreadPool();
    /** username -> handler; used to route private messages to online users in real time. */
    private final ConcurrentHashMap<String, ClientHandler> online = new ConcurrentHashMap<>();
    private volatile boolean running = false;

    private ChatServer() {}

    public static synchronized ChatServer getInstance() {
        if (instance == null) instance = new ChatServer();
        return instance;
    }

    public void start(int port) throws IOException, SQLException {
        if (running) return;
        Database.getInstance(); // ensure schema exists before accepting clients
        serverSocket = new ServerSocket(port, 50, null); // bind all interfaces -> reachable over Wi-Fi
        running = true;
        Thread acceptThread = new Thread(this::acceptLoop, "server-accept-thread");
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                ClientHandler handler = new ClientHandler(socket, this);
                clientPool.submit(handler); // hand the connection to the thread pool
            } catch (IOException e) {
                if (running) System.err.println("Accept failed: " + e.getMessage());
            }
        }
    }

    public void registerOnline(String username, ClientHandler handler) {
        online.put(username, handler);
    }

    public void unregisterOnline(String username) {
        online.remove(username);
    }

    public ClientHandler getHandler(String username) {
        return online.get(username);
    }

    public Set<String> getOnlineUsernames() {
        return online.keySet();
    }

    public void stop() {
        running = false;
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException ignored) {
        }
        clientPool.shutdownNow();
    }

    public boolean isRunning() {
        return running;
    }
}
