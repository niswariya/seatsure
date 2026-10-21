package com.seatsure.common;

import org.springframework.security.oauth2.jwt.Jwt;

/** Reads the user id from the JWT subject — no database lookup per request. */
public final class CurrentUser {

    private CurrentUser() {}

    public static Long id(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
