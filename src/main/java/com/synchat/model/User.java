package com.synchat.model;

import org.json.JSONObject;

/**
 * ADVANCED OOP - CONCRETE SUBCLASS / POLYMORPHISM
 * Extends the abstract ChatParticipant and implements JsonConvertible
 * (inherited contract). Also demonstrates a nested enum used as a
 * type-safe status field instead of a raw String.
 */
public class User extends ChatParticipant {

    public enum Status { ONLINE, OFFLINE, AWAY }

    private final String username;
    private final String passwordHash;
    private Status status;
    private final String createdAt;

    public User(int id, String username, String passwordHash, Status status, String createdAt) {
        super(id, username);
        this.username = username;
        this.passwordHash = passwordHash;
        this.status = status;
        this.createdAt = createdAt;
    }

    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public String getCreatedAt() { return createdAt; }

    @Override
    public String getStatusLabel() {
        return switch (status) {
            case ONLINE -> "Online";
            case AWAY -> "Away";
            case OFFLINE -> "Offline";
        };
    }

    @Override
    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("username", username);
        o.put("status", status.name());
        o.put("createdAt", createdAt == null ? JSONObject.NULL : createdAt);
        return o;
    }
}
