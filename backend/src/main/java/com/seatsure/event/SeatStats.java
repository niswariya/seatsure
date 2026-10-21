package com.seatsure.event;

import java.math.BigDecimal;

/** Aggregate per event, computed in SQL so the event list needs one query, not one per event. */
public record SeatStats(Long eventId, Long totalSeats, Long availableSeats, BigDecimal minPrice) {}
