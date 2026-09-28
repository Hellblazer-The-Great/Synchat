package com.synchat.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * ADVANCED OOP - ABSTRACT CLASS + TEMPLATE METHOD PATTERN
 * Implements the READ and DELETE halves of CRUD once, generically, for any
 * table. Concrete subclasses (UserRepository, MessageRepository,
 * FriendRepository) only need to say which table they own and how to turn
 * a ResultSet row into a Java object - the abstract "hook" methods below.
 */
public abstract class AbstractRepository<T, ID> implements CrudRepository<T, ID> {
    protected final Connection connection;

    protected AbstractRepository(Connection connection) {
        this.connection = connection;
    }

    /** Hook methods every subclass must supply. */
    protected abstract String tableName();
    protected abstract String idColumn();
    protected abstract T mapRow(ResultSet rs) throws SQLException;

    @Override
    public T findById(ID id) throws SQLException {
        String sql = "SELECT * FROM " + tableName() + " WHERE " + idColumn() + " = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    @Override
    public List<T> findAll() throws SQLException {
        List<T> results = new ArrayList<>();
        String sql = "SELECT * FROM " + tableName();
        try (Statement st = connection.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) results.add(mapRow(rs));
        }
        return results;
    }

    @Override
    public boolean delete(ID id) throws SQLException {
        String sql = "DELETE FROM " + tableName() + " WHERE " + idColumn() + " = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setObject(1, id);
            return ps.executeUpdate() > 0;
        }
    }
}
