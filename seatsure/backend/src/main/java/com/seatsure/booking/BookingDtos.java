package com.seatsure.booking;

import com.seatsure.event.EventDtos.SeatView;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class BookingDtos {

    private BookingDtos() {}

    public record CreateHoldRequest(@NotNull Long eventId, @NotEmpty List<Long> seatIds) {}

    public record HoldView(String holdId, Long eventId, Instant expiresAt,
                           List<SeatView> seats, BigDecimal totalAmount) {}

    public record BookingView(String reference, Long eventId, String eventTitle, String venue,
                              Instant startsAt, List<String> seats, BigDecimal totalAmount,
                              String status, Instant bookedAt) {}
}
