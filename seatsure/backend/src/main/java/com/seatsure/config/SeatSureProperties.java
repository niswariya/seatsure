package com.seatsure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/** Type-safe binding of the {@code seatsure.*} block in application.yml. */
@ConfigurationProperties(prefix = "seatsure")
public record SeatSureProperties(
        Jwt jwt,
        Hold hold,
        Outbox outbox,
        RateLimit rateLimit,
        Admin admin,
        Cors cors,
        boolean seedDemoEvent,
        Ai ai
) {
    public record Jwt(String secret, long ttlMinutes) {}
    public record Hold(long ttlSeconds, int maxSeats, long expirySweepMs) {}
    public record Outbox(long pollMs) {}
    public record RateLimit(int holdsPerMinute) {}
    public record Admin(String email, String password) {}
    public record Cors(List<String> allowedOrigins) {}
    public record Ai(boolean enabled, int maxInputChars, int memoryMessages, int requestsPerMinute) {}
}
