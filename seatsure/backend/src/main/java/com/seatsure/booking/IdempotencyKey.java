package com.seatsure.booking;

import jakarta.persistence.*;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * Remembers which booking a client's Idempotency-Key produced, so a retried
 * "confirm" (double click, mobile network retry) returns the same booking
 * instead of charging twice. Keys are scoped per user.
 */
@Entity
@Table(name = "idempotency_keys")
@IdClass(IdempotencyKey.Key.class)
public class IdempotencyKey {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Id
    @Column(name = "idem_key", length = 64)
    private String idemKey;

    @Column(name = "booking_id", nullable = false)
    private Long bookingId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected IdempotencyKey() {}

    public IdempotencyKey(Long userId, String idemKey, Long bookingId) {
        this.userId = userId;
        this.idemKey = idemKey;
        this.bookingId = bookingId;
        this.createdAt = Instant.now();
    }

    public Long getBookingId() { return bookingId; }

    public static class Key implements Serializable {
        private Long userId;
        private String idemKey;

        public Key() {}

        public Key(Long userId, String idemKey) {
            this.userId = userId;
            this.idemKey = idemKey;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(userId, k.userId) && Objects.equals(idemKey, k.idemKey);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, idemKey);
        }
    }
}
