package com.synchat.repository;

import com.synchat.model.FriendRequest;
import com.synchat.model.FriendStatus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * DATA MANIPULATION - full CRUD on the "friends" table.
 *  CREATE -> create()          (send a friend request)
 *  READ   -> findFriendsOf() / findPendingFor()
 *  UPDATE -> update()          (accept / block a request)
 *  DELETE -> delete()          (inherited - remove a friend)
 */
public class FriendRepository extends AbstractRepository<FriendRequest, Integer> {

    public FriendRepository(Connection connection) {
        super(connection);
    }

    @Override protected String tableName() { return "friends"; }
    @Override protected String idColumn() { return "id"; }

    @Override
    protected FriendRequest mapRow(ResultSet rs) throws SQLException {
        return new FriendRequest(
                rs.getInt("id"),
                rs.getInt("user_id"),
                rs.getInt("friend_id"),
                FriendStatus.valueOf(rs.getString("status"))
        );
    }

    /** CREATE */
    @Override
    public FriendRequest create(FriendRequest fr) throws SQLException {
        String sql = "INSERT INTO friends (user_id, friend_id, status) VALUES (?,?,?)";
        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, fr.getUserId());
            ps.setInt(2, fr.getFriendId());
            ps.setString(3, fr.getStatus().name());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return findById(keys.getInt(1));
            }
        }
        return null;
    }

    /** UPDATE */
    @Override
    public boolean update(FriendRequest fr) throws SQLException {
        String sql = "UPDATE friends SET status = ? WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, fr.getStatus().name());
            ps.setInt(2, fr.getId());
            return ps.executeUpdate() > 0;
        }
    }

    /** READ - accepted friends of a given user, relationship is bidirectional. */
    public List<FriendRequest> findFriendsOf(int userId) throws SQLException {
        List<FriendRequest> list = new ArrayList<>();
        String sql = "SELECT * FROM friends WHERE (user_id = ? OR friend_id = ?) AND status = 'ACCEPTED'";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        }
        return list;
    }

    /**
     * READ - the single friendship row between two specific users, in
     * whichever direction it was originally created. Used by unfriend(),
     * since the caller only knows "me" and "the other person," not which
     * of them happened to send the original request.
     */
    public FriendRequest findBetween(int userId, int otherUserId) throws SQLException {
        String sql = "SELECT * FROM friends WHERE (user_id = ? AND friend_id = ?) OR (user_id = ? AND friend_id = ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, otherUserId);
            ps.setInt(3, otherUserId);
            ps.setInt(4, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    /** READ - requests still awaiting this user's response. */
    public List<FriendRequest> findPendingFor(int userId) throws SQLException {
        List<FriendRequest> list = new ArrayList<>();
        String sql = "SELECT * FROM friends WHERE friend_id = ? AND status = 'PENDING'";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        }
        return list;
    }
}
