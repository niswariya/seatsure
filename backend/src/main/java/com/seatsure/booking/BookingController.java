package com.seatsure.booking;

import com.seatsure.booking.BookingDtos.BookingView;
import com.seatsure.booking.BookingDtos.CreateHoldRequest;
import com.seatsure.booking.BookingDtos.HoldView;
import com.seatsure.common.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class BookingController {

    private final HoldService holds;
    private final BookingService bookings;

    public BookingController(HoldService holds, BookingService bookings) {
        this.holds = holds;
        this.bookings = bookings;
    }

    /** Step 1: claim seats for a few minutes. 409 if any seat is already taken. */
    @PostMapping("/holds")
    @ResponseStatus(HttpStatus.CREATED)
    public HoldView hold(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateHoldRequest req) {
        return holds.hold(CurrentUser.id(jwt), req.eventId(), req.seatIds());
    }

    @GetMapping("/holds/{holdId}")
    public HoldView getHold(@AuthenticationPrincipal Jwt jwt, @PathVariable String holdId) {
        return holds.get(CurrentUser.id(jwt), holdId);
    }

    @DeleteMapping("/holds/{holdId}")
    public ResponseEntity<Void> release(@AuthenticationPrincipal Jwt jwt, @PathVariable String holdId) {
        holds.release(CurrentUser.id(jwt), holdId);
        return ResponseEntity.noContent().build();
    }

    /** Step 2: pay and confirm. Safe to retry with the same Idempotency-Key. */
    @PostMapping("/holds/{holdId}/confirm")
    public BookingView confirm(@AuthenticationPrincipal Jwt jwt,
                               @PathVariable String holdId,
                               @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return bookings.confirm(CurrentUser.id(jwt), holdId, idempotencyKey);
    }

    @GetMapping("/bookings/me")
    public List<BookingView> myBookings(@AuthenticationPrincipal Jwt jwt) {
        return bookings.myBookings(CurrentUser.id(jwt));
    }
}
