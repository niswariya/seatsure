package com.seatsure.event;

import jakarta.persistence.*;

import java.math.BigDecimal;

/**
 * A seat's status only ever changes through the guarded bulk UPDATEs in
 * {@link SeatRepository}, never through entity setters. That's what makes
 * double booking impossible: the WHERE clause is the lock.
 */
@Entity
@Table(name = "seats")
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "row_label", nullable = false, length = 3)
    private String rowLabel;

    @Column(name = "seat_number", nullable = false)
    private int seatNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SeatTier tier;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SeatStatus status;

    @Column(name = "hold_id", length = 36)
    private String holdId;

    /** Bumped by every status change; lets clients ignore out-of-order WebSocket updates. */
    @Column(nullable = false)
    private long version;

    protected Seat() {}

    public Seat(Long eventId, String rowLabel, int seatNumber, SeatTier tier, BigDecimal price) {
        this.eventId = eventId;
        this.rowLabel = rowLabel;
        this.seatNumber = seatNumber;
        this.tier = tier;
        this.price = price;
        this.status = SeatStatus.AVAILABLE;
    }

    public Long getId() { return id; }
    public Long getEventId() { return eventId; }
    public String getRowLabel() { return rowLabel; }
    public int getSeatNumber() { return seatNumber; }
    public SeatTier getTier() { return tier; }
    public BigDecimal getPrice() { return price; }
    public SeatStatus getStatus() { return status; }
    public String getHoldId() { return holdId; }
    public long getVersion() { return version; }

    public String label() {
        return rowLabel + seatNumber;
    }
}
