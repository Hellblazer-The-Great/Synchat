package com.synchat.repository;

import java.util.List;

/**
 * ADVANCED OOP - GENERIC INTERFACE
 * Establishes a CRUD contract that every repository (Data Access Object)
 * in the app must follow, regardless of what entity type it manages.
 */
public interface CrudRepository<T, ID> {
    T create(T entity) throws Exception;
    T findById(ID id) throws Exception;
    List<T> findAll() throws Exception;
    boolean update(T entity) throws Exception;
    boolean delete(ID id) throws Exception;
}
