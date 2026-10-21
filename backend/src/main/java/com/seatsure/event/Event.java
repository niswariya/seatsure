package com.seatsure.event;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "events")
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(nullable = false, length = 150)
    private String venue;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(length = 1000)
    private String description;

    @Column(name = "seat_rows", nullable = false)
    private int seatRows;

    @Column(name = "seats_per_row", nullable = false)
    private int seatsPerRow;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Event() {}

    public Event(String title, String venue, Instant startsAt, String description, int seatRows, int seatsPerRow) {
        this.title = title;
        this.venue = venue;
        this.startsAt = startsAt;
        this.description = description;
        this.seatRows = seatRows;
        this.seatsPerRow = seatsPerRow;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getVenue() { return venue; }
    public Instant getStartsAt() { return startsAt; }
    public String getDescription() { return description; }
    public int getSeatRows() { return seatRows; }
    public int getSeatsPerRow() { return seatsPerRow; }
}
