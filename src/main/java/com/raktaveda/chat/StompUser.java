package com.raktaveda.chat;

import com.raktaveda.security.AuthPrincipal;
import java.security.Principal;

/**
 * The logged-in account on one WebSocket connection. The name ("USER:<id>" or "INSTITUTION:<id>")
 * is how the server addresses private messages to this account. tokenHash lets the server
 * re-check that the login token has not been revoked or expired while the connection stays open.
 */
public record StompUser(AuthPrincipal principal, String tokenHash) implements Principal {

    @Override
    public String getName() {
        return name(principal.type().name(), principal.id());
    }

    public static String name(String type, java.util.UUID id) {
        return type + ":" + id;
    }

    @Override
    public String toString() {
        return "StompUser[redacted]"; // never print the token hash
    }
}
