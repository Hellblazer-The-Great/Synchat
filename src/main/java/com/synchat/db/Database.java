package com.synchat.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * DATABASE INTEGRATION
 * Singleton holding the single SQLite connection used by the server, plus
 * schema creation. "friends" and "messages" both carry FOREIGN KEY
 * references back to "users", with ON DELETE CASCADE, establishing the
 * relational structure between the three tables.
 */
public final class Database {
    private static Database instance;
    private final Connection connection;

    private Database() throws SQLException {
        connection = DriverManager.getConnection("jdbc:sqlite:synchat.db");
        try (Statement st = connection.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON");
        }
        createSchema();
    }

    public static synchronized Database getInstance() throws SQLException {
        if (instance == null) instance = new Database();
        return instance;
    }

    public Connection getConnection() {
        return connection;
    }

    private void createSchema() throws SQLException {
        try (Statement st = connection.createStatement()) {

            st.execute(
                "CREATE TABLE IF NOT EXISTS users (" +
                "  id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "  username TEXT UNIQUE NOT NULL," +
                "  password_hash TEXT NOT NULL," +
                "  status TEXT NOT NULL DEFAULT 'OFFLINE'," +
                "  created_at TEXT NOT NULL" +
                ")"
            );

            st.execute(
                "CREATE TABLE IF NOT EXISTS friends (" +
                "  id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "  user_id INTEGER NOT NULL," +
                "  friend_id INTEGER NOT NULL," +
                "  status TEXT NOT NULL DEFAULT 'PENDING'," +
                "  UNIQUE(user_id, friend_id)," +
                "  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE," +
                "  FOREIGN KEY (friend_id) REFERENCES users(id) ON DELETE CASCADE" +
                ")"
            );

            st.execute(
                "CREATE TABLE IF NOT EXISTS messages (" +
                "  id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "  sender_id INTEGER NOT NULL," +
                "  receiver_id INTEGER NOT NULL," +
                "  content TEXT NOT NULL," +
                "  timestamp TEXT NOT NULL," +
                "  is_read INTEGER NOT NULL DEFAULT 0," +
                "  edited INTEGER NOT NULL DEFAULT 0," +
                "  FOREIGN KEY (sender_id) REFERENCES users(id) ON DELETE CASCADE," +
                "  FOREIGN KEY (receiver_id) REFERENCES users(id) ON DELETE CASCADE" +
                ")"
            );

            // MIGRATION: databases created before message edit/delete existed
            // won't have this column yet - CREATE TABLE IF NOT EXISTS above is
            // a no-op on an existing table, so add it here if missing. SQLite
            // has no "ADD COLUMN IF NOT EXISTS", so this just swallows the
            // "duplicate column" error on every startup after the first.
            try {
                st.execute("ALTER TABLE messages ADD COLUMN edited INTEGER NOT NULL DEFAULT 0");
            } catch (SQLException alreadyExists) {
                // column already present - nothing to do
            }
        }
    }
}
