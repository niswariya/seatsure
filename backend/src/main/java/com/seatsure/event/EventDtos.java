package com.seatsure.event;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class EventDtos {

    private EventDtos() {}

    public record CreateEventRequest(
            @NotBlank @Size(max = 150) String title,
            @NotBlank @Size(max = 150) String venue,
            @NotNull @Future Instant startsAt,
            @Size(max = 1000) String description,
            @Min(1) @Max(26) int seatRows,
            @Min(1) @Max(40) int seatsPerRow,
            @NotNull @DecimalMin("0.01") BigDecimal premiumPrice,
            @NotNull @DecimalMin("0.01") BigDecimal standardPrice,
            @NotNull @DecimalMin("0.01") BigDecimal economyPrice) {}

    public record EventSummary(
            Long id, String title, String venue, Instant startsAt,
            int totalSeats, long availableSeats, BigDecimal fromPrice) {}

    public record SeatView(
            Long id, String row, int number, SeatTier tier, BigDecimal price,
            SeatStatus status, long version) {

        public static SeatView of(Seat s) {
            return new SeatView(s.getId(), s.getRowLabel(), s.getSeatNumber(), s.getTier(),
                    s.getPrice(), s.getStatus(), s.getVersion());
        }
    }

    public record EventDetail(
            Long id, String title, String venue, Instant startsAt, String description,
            int seatRows, int seatsPerRow, List<SeatView> seats) {}
}
