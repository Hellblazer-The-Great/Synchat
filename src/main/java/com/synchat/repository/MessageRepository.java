package com.synchat.repository;

import com.synchat.model.Message;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * DATA MANIPULATION - full CRUD on the "messages" table.
 *  CREATE -> create()             (a new private message is persisted)
 *  READ   -> findConversation()   (full thread between two users)
 *  UPDATE -> update()             (mark as read)
 *  DELETE -> delete() (inherited) (unsend a message)
 */
public class MessageRepository extends AbstractRepository<Message, Integer> {

    public MessageRepository(Connection connection) {
        super(connection);
    }

    @Override protected String tableName() { return "messages"; }
    @Override protected String idColumn() { return "id"; }

    @Override
    protected Message mapRow(ResultSet rs) throws SQLException {
        return new Message(
                rs.getInt("id"),
                rs.getInt("sender_id"),
                rs.getInt("receiver_id"),
                rs.getString("content"),
                rs.getString("timestamp"),
                rs.getInt("is_read") == 1
        );
    }

    /** CREATE */
    @Override
    public Message create(Message m) throws SQLException {
        String sql = "INSERT INTO messages (sender_id, receiver_id, content, timestamp, is_read) VALUES (?,?,?,?,0)";
        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, m.getSenderId());
            ps.setInt(2, m.getReceiverId());
            ps.setString(3, m.getContent());
            ps.setString(4, LocalDateTime.now().toString());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return findById(keys.getInt(1));
            }
        }
        return null;
    }

    /** UPDATE */
    @Override
    public boolean update(Message m) throws SQLException {
        String sql = "UPDATE messages SET is_read = ? WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, m.isRead() ? 1 : 0);
            ps.setInt(2, m.getId());
            return ps.executeUpdate() > 0;
        }
    }

    /** READ - the full chronological conversation between two users. */
    public List<Message> findConversation(int userA, int userB) throws SQLException {
        List<Message> list = new ArrayList<>();
        String sql = "SELECT * FROM messages " +
                "WHERE (sender_id = ? AND receiver_id = ?) OR (sender_id = ? AND receiver_id = ?) " +
                "ORDER BY id ASC";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, userA);
            ps.setInt(2, userB);
            ps.setInt(3, userB);
            ps.setInt(4, userA);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        }
        return list;
    }
}
