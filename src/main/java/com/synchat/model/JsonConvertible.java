package com.synchat.model;

import org.json.JSONObject;

/**
 * ADVANCED OOP - INTERFACE
 * Implemented by every model class so any entity can be serialized to
 * JSON, both for the client/server network protocol and for future
 * REST-style responses.
 */
public interface JsonConvertible {
    JSONObject toJson();
}
