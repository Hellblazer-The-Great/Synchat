package com.synchat.net;

/**
 * NETWORKING - central place for the JSON "type" field values understood
 * by both client and server. Every message sent over the socket is a
 * single-line JSON object, e.g. {"type":"MESSAGE","to":"bob","content":"hi"}.
 */
public final class Protocol {
    private Protocol() {}

    public static final String AUTH_LOGIN = "AUTH_LOGIN";
    public static final String AUTH_REGISTER = "AUTH_REGISTER";
    public static final String AUTH_RESULT = "AUTH_RESULT";
    public static final String FRIEND_REQUEST = "FRIEND_REQUEST";
    public static final String FRIEND_RESPONSE = "FRIEND_RESPONSE";
    public static final String FRIEND_RESPONSE_RESULT = "FRIEND_RESPONSE_RESULT";
    public static final String REQUEST_FRIEND_REQUESTS = "REQUEST_FRIEND_REQUESTS";
    public static final String FRIEND_REQUESTS_LIST = "FRIEND_REQUESTS_LIST";
    public static final String REQUEST_FRIENDS_LIST = "REQUEST_FRIENDS_LIST";
    public static final String FRIENDS_LIST = "FRIENDS_LIST";
    public static final String USER_LIST = "USER_LIST";
    public static final String REQUEST_USER_LIST = "REQUEST_USER_LIST";
    public static final String MESSAGE = "MESSAGE";
    public static final String ERROR = "ERROR";
}
