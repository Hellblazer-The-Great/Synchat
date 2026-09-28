package com.synchat.net;

import org.json.JSONObject;

/**
 * ADVANCED OOP - INTERFACE (Observer pattern)
 * Callback contract so the JavaFX UI can react to events pushed from the
 * network's background listener thread.
 */
public interface MessageListener {
    void onServerEvent(JSONObject json);
    void onDisconnected();
}
