package com.seatsure.booking;

import jakarta.persistence.*;

import java.time.Instant;

/** A short-lived claim on seats while the user pays. Expires automatically. */
@Entity
@Table(name = "seat_holds")
public class SeatHold {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private HoldStatus status;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Version
    private long version;

    protected SeatHold() {}

    public SeatHold(String id, Long eventId, Long userId, Instant expiresAt) {
        this.id = id;
        this.eventId = eventId;
        this.userId = userId;
        this.expiresAt = expiresAt;
        this.status = HoldStatus.ACTIVE;
        this.createdAt = Instant.now();
    }

    public boolean isActiveAt(Instant now) {
        return status == HoldStatus.ACTIVE && now.isBefore(expiresAt);
    }

    public void markConfirmed() { this.status = HoldStatus.CONFIRMED; }
    public void markExpired() { this.status = HoldStatus.EXPIRED; }
    public void markReleased() { this.status = HoldStatus.RELEASED; }

    public String getId() { return id; }
    public Long getEventId() { return eventId; }
    public Long getUserId() { return userId; }
    public HoldStatus getStatus() { return status; }
    public Instant getExpiresAt() { return expiresAt; }
}
