package com.synchat.model;

/**
 * ADVANCED OOP - ABSTRACT CLASS
 * Captures state and behaviour shared by anything that can take part in a
 * conversation. Only User extends it today, but the abstraction keeps the
 * design open (e.g. a future SystemAccount/Bot participant) without
 * touching existing code - an example of the Open/Closed Principle.
 */
public abstract class ChatParticipant implements JsonConvertible {
    protected int id;
    protected String displayName;

    protected ChatParticipant(int id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public int getId() { return id; }
    public String getDisplayName() { return displayName; }

    /** Every participant type must define how it is labeled in the UI. */
    public abstract String getStatusLabel();
}
