package com.seatsure.booking;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "bookings")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 12)
    private String reference;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "hold_id", nullable = false, unique = true, length = 36)
    private String holdId;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "seat_labels", nullable = false)
    private String seatLabels;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Booking() {}

    public Booking(String reference, Long eventId, Long userId, String holdId,
                   BigDecimal totalAmount, String seatLabels) {
        this.reference = reference;
        this.eventId = eventId;
        this.userId = userId;
        this.holdId = holdId;
        this.totalAmount = totalAmount;
        this.seatLabels = seatLabels;
        this.status = "CONFIRMED";
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getReference() { return reference; }
    public Long getEventId() { return eventId; }
    public Long getUserId() { return userId; }
    public String getHoldId() { return holdId; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public String getSeatLabels() { return seatLabels; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}
