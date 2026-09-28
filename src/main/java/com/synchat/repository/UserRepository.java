package com.synchat.repository;

import com.synchat.model.User;

import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;

/**
 * DATA MANIPULATION - full CRUD on the "users" table.
 *  CREATE -> create()
 *  READ   -> findById() / findAll() (inherited) / findByUsername()
 *  UPDATE -> update() (used for presence status changes)
 *  DELETE -> delete() (inherited)
 */
public class UserRepository extends AbstractRepository<User, Integer> {

    public UserRepository(Connection connection) {
        super(connection);
    }

    @Override protected String tableName() { return "users"; }
    @Override protected String idColumn() { return "id"; }

    @Override
    protected User mapRow(ResultSet rs) throws SQLException {
        return new User(
                rs.getInt("id"),
                rs.getString("username"),
                rs.getString("password_hash"),
                User.Status.valueOf(rs.getString("status")),
                rs.getString("created_at")
        );
    }

    /** CREATE */
    @Override
    public User create(User user) throws SQLException {
        String sql = "INSERT INTO users (username, password_hash, status, created_at) VALUES (?,?,?,?)";
        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, user.getUsername());
            ps.setString(2, user.getPasswordHash());
            ps.setString(3, user.getStatus().name());
            ps.setString(4, LocalDateTime.now().toString());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return findById(keys.getInt(1));
            }
        }
        return null;
    }

    /** READ (custom finder, used at login time) */
    public User findByUsername(String username) throws SQLException {
        String sql = "SELECT * FROM users WHERE username = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    /** UPDATE */
    @Override
    public boolean update(User user) throws SQLException {
        String sql = "UPDATE users SET status = ? WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, user.getStatus().name());
            ps.setInt(2, user.getId());
            return ps.executeUpdate() > 0;
        }
    }

    /** Never store plain-text passwords - SHA-256 hash before persisting/comparing. */
    public static String hashPassword(String rawPassword) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawPassword.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
