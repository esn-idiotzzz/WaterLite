package com.joe.waterlite;

/** A transport failure must never revoke a session. Only the documented expiry code does. */
public final class SessionPolicy {
    private SessionPolicy() {}
    public static boolean expired(int code) { return code == -99; }
}
