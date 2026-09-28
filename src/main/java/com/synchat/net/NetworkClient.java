package com.synchat.net;

import javafx.application.Platform;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * CONCURRENCY + NETWORKING (client side)
 * Reading from the socket happens on its own background thread so the
 * JavaFX Application Thread is never blocked waiting on the network.
 * Incoming events are marshalled back onto the UI thread with
 * Platform.runLater before the listener is invoked.
 */
public class NetworkClient {
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private final ExecutorService listenerPool = Executors.newSingleThreadExecutor();
    private MessageListener listener;
    private volatile boolean connected = false;

    public void connect(String host, int port, MessageListener listener) throws IOException {
        this.listener = listener;
        socket = new Socket(host, port);
        out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
        connected = true;
        listenerPool.submit(this::listenLoop);
    }

    /** Swap in a new listener once the UI transitions screens (e.g. after login). */
    public void setListener(MessageListener listener) {
        this.listener = listener;
    }

    private void listenLoop() {
        try {
            String line;
            while (connected && (line = in.readLine()) != null) {
                JSONObject json = new JSONObject(line);
                Platform.runLater(() -> {
                    if (listener != null) listener.onServerEvent(json);
                });
            }
        } catch (IOException ignored) {
            // socket closed / connection dropped
        } finally {
            connected = false;
            Platform.runLater(() -> {
                if (listener != null) listener.onDisconnected();
            });
        }
    }

    public synchronized void send(JSONObject json) {
        if (out != null) out.println(json.toString());
    }

    public boolean isConnected() {
        return connected;
    }

    public void close() {
        connected = false;
        try {
            if (socket != null) socket.close();
        } catch (IOException ignored) {
        }
        listenerPool.shutdownNow();
    }
}
