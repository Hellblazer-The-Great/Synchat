package com.synchat.model;

import org.json.JSONObject;

/** Represents a single private chat message, a row in the "messages" table. */
public class Message implements JsonConvertible {
    private final int id;
    private final int senderId;
    private final int receiverId;
    private final String content;
    private final String timestamp;
    private boolean read;
    private final boolean edited;

    public Message(int id, int senderId, int receiverId, String content, String timestamp, boolean read, boolean edited) {
        this.id = id;
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.content = content;
        this.timestamp = timestamp;
        this.read = read;
        this.edited = edited;
    }

    public int getId() { return id; }
    public int getSenderId() { return senderId; }
    public int getReceiverId() { return receiverId; }
    public String getContent() { return content; }
    public String getTimestamp() { return timestamp; }
    public boolean isRead() { return read; }
    public void setRead(boolean read) { this.read = read; }
    public boolean isEdited() { return edited; }

    @Override
    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("senderId", senderId);
        o.put("receiverId", receiverId);
        o.put("content", content);
        o.put("timestamp", timestamp == null ? JSONObject.NULL : timestamp);
        o.put("read", read);
        o.put("edited", edited);
        return o;
    }
}
