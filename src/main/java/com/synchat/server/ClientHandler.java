package com.synchat.server;

import com.synchat.db.Database;
import com.synchat.model.FriendRequest;
import com.synchat.model.FriendStatus;
import com.synchat.model.Message;
import com.synchat.model.User;
import com.synchat.net.Protocol;
import com.synchat.repository.FriendRepository;
import com.synchat.repository.MessageRepository;
import com.synchat.repository.UserRepository;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.sql.SQLException;

/**
 * CONCURRENCY - one instance of this class runs per connected client on a
 * pooled thread (see ChatServer.clientPool). It owns that client's socket
 * streams and talks to the repositories, so multiple users are served
 * truly in parallel, each on their own thread.
 */
public class ClientHandler implements Runnable {
    private final Socket socket;
    private final ChatServer server;
    private PrintWriter out;
    private BufferedReader in;
    private String username;
    private int userId = -1;

    public ClientHandler(Socket socket, ChatServer server) {
        this.socket = socket;
        this.server = server;
    }

    @Override
    public void run() {
        try {
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));

            String line;
            while ((line = in.readLine()) != null) {
                handle(new JSONObject(line));
            }
        } catch (IOException e) {
            // client disconnected
        } finally {
            cleanup();
        }
    }

    private void handle(JSONObject json) {
        try {
            String type = json.getString("type");
            switch (type) {
                case Protocol.AUTH_REGISTER -> handleRegister(json);
                case Protocol.AUTH_LOGIN -> handleLogin(json);
                case Protocol.FRIEND_REQUEST -> handleFriendRequest(json);
                case Protocol.FRIEND_RESPONSE -> handleFriendResponse(json);
                case Protocol.MESSAGE -> handleMessage(json);
                case Protocol.REQUEST_USER_LIST -> sendUserListToSelf();
                default -> sendError("Unknown command: " + type);
            }
        } catch (SQLException e) {
            sendError("Database error: " + e.getMessage());
        }
    }

    private void handleRegister(JSONObject json) throws SQLException {
        UserRepository users = new UserRepository(Database.getInstance().getConnection());
        String uname = json.getString("username");
        if (users.findByUsername(uname) != null) {
            sendError("Username already taken");
            return;
        }
        User created = users.create(new User(0, uname,
                UserRepository.hashPassword(json.getString("password")), User.Status.ONLINE, null));
        respondAuth(created != null, created);
        if (created != null) {
            this.username = created.getUsername();
            this.userId = created.getId();
            server.registerOnline(username, this);
            broadcastUserList();
        }
    }

    private void handleLogin(JSONObject json) throws SQLException {
        UserRepository users = new UserRepository(Database.getInstance().getConnection());
        User user = users.findByUsername(json.getString("username"));
        boolean ok = user != null
                && user.getPasswordHash().equals(UserRepository.hashPassword(json.getString("password")));
        if (ok) {
            user.setStatus(User.Status.ONLINE);
            users.update(user);
            this.username = user.getUsername();
            this.userId = user.getId();
            server.registerOnline(username, this);
        }
        respondAuth(ok, user);
        if (ok) broadcastUserList();
    }

    private void respondAuth(boolean success, User user) {
        JSONObject resp = new JSONObject();
        resp.put("type", Protocol.AUTH_RESULT);
        resp.put("success", success);
        if (success && user != null) resp.put("user", user.toJson());
        send(resp);
    }

    private void handleFriendRequest(JSONObject json) throws SQLException {
        FriendRepository friends = new FriendRepository(Database.getInstance().getConnection());
        UserRepository users = new UserRepository(Database.getInstance().getConnection());
        User target = users.findByUsername(json.getString("username"));
        if (target == null) {
            sendError("No such user");
            return;
        }
        friends.create(new FriendRequest(0, userId, target.getId(), FriendStatus.PENDING));
        ClientHandler targetHandler = server.getHandler(target.getUsername());
        if (targetHandler != null) {
            JSONObject notice = new JSONObject();
            notice.put("type", Protocol.FRIEND_REQUEST);
            notice.put("fromUsername", username);
            targetHandler.send(notice);
        }
    }

    private void handleFriendResponse(JSONObject json) throws SQLException {
        FriendRepository friends = new FriendRepository(Database.getInstance().getConnection());
        FriendRequest fr = friends.findById(json.getInt("requestId"));
        if (fr == null) {
            sendError("Request not found");
            return;
        }
        fr.setStatus(json.getBoolean("accepted") ? FriendStatus.ACCEPTED : FriendStatus.BLOCKED);
        friends.update(fr);
    }

    private void handleMessage(JSONObject json) throws SQLException {
        MessageRepository messages = new MessageRepository(Database.getInstance().getConnection());
        UserRepository users = new UserRepository(Database.getInstance().getConnection());
        User recipient = users.findByUsername(json.getString("to"));
        if (recipient == null) {
            sendError("Recipient not found");
            return;
        }

        Message saved = messages.create(new Message(0, userId, recipient.getId(), json.getString("content"), null, false));

        JSONObject payload = new JSONObject();
        payload.put("type", Protocol.MESSAGE);
        payload.put("from", username);
        payload.put("content", saved.getContent());
        payload.put("timestamp", saved.getTimestamp());

        ClientHandler recipientHandler = server.getHandler(recipient.getUsername());
        if (recipientHandler != null) recipientHandler.send(payload); // instant delivery if online
        send(payload); // echo back to sender so their own chat log shows it
    }

    /**
     * Pushes the current roster to everyone. This alone is NOT enough to
     * keep every client in sync: a client that is still transitioning from
     * the login screen to the chat screen when this fires can miss it (see
     * sendUserListToSelf() below for why that no longer matters).
     */
    private void broadcastUserList() {
        JSONObject payload = buildUserListPayload();
        for (String uname : server.getOnlineUsernames()) {
            ClientHandler h = server.getHandler(uname);
            if (h != null) h.send(payload);
        }
    }

    /**
     * FIX for the "wrong online count" bug: a client's socket listener is
     * briefly still pointed at the login screen's temporary listener while
     * MainChatView is being constructed (Platform.runLater is queued, not
     * instant). If a USER_LIST broadcast lands in that gap, it's silently
     * dropped, and the new client is stuck showing a stale/empty list until
     * some other unrelated event happens to trigger another broadcast.
     *
     * Rather than trying to close that timing gap, the client explicitly
     * asks for a fresh snapshot (REQUEST_USER_LIST) the moment MainChatView
     * finishes wiring itself up as the listener - guaranteeing it always
     * gets an accurate roster regardless of any earlier missed broadcast.
     */
    private void sendUserListToSelf() {
        send(buildUserListPayload());
    }

    private JSONObject buildUserListPayload() {
        JSONObject payload = new JSONObject();
        payload.put("type", Protocol.USER_LIST);
        payload.put("users", new JSONArray(server.getOnlineUsernames()));
        return payload;
    }

    private void sendError(String message) {
        JSONObject o = new JSONObject();
        o.put("type", Protocol.ERROR);
        o.put("message", message);
        send(o);
    }

    synchronized void send(JSONObject json) {
        if (out != null) out.println(json.toString());
    }

    private void cleanup() {
        if (username != null) {
            server.unregisterOnline(username);
            try {
                UserRepository users = new UserRepository(Database.getInstance().getConnection());
                User u = users.findByUsername(username);
                if (u != null) {
                    u.setStatus(User.Status.OFFLINE);
                    users.update(u);
                }
            } catch (SQLException ignored) {
            }
            broadcastUserList();
        }
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }
}
