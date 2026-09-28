package com.synchat.model;

import org.json.JSONObject;

/** Represents a row in the "friends" table - a relationship between two users. */
public class FriendRequest implements JsonConvertible {
    private final int id;
    private final int userId;
    private final int friendId;
    private FriendStatus status;

    public FriendRequest(int id, int userId, int friendId, FriendStatus status) {
        this.id = id;
        this.userId = userId;
        this.friendId = friendId;
        this.status = status;
    }

    public int getId() { return id; }
    public int getUserId() { return userId; }
    public int getFriendId() { return friendId; }
    public FriendStatus getStatus() { return status; }
    public void setStatus(FriendStatus status) { this.status = status; }

    @Override
    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("userId", userId);
        o.put("friendId", friendId);
        o.put("status", status.name());
        return o;
    }
}
