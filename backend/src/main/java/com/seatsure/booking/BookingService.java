package com.seatsure.booking;

import com.seatsure.booking.BookingDtos.BookingView;
import com.seatsure.common.ConflictException;
import com.seatsure.common.GoneException;
import com.seatsure.common.NotFoundException;
import com.seatsure.event.Event;
import com.seatsure.event.EventRepository;
import com.seatsure.event.Seat;
import com.seatsure.event.SeatRepository;
import com.seatsure.event.SeatStatus;
import com.seatsure.outbox.OutboxWriter;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class BookingService {

    private static final char[] REF_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray(); // no 0/O/1/I
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SeatHoldRepository holds;
    private final SeatRepository seats;
    private final BookingRepository bookings;
    private final IdempotencyKeyRepository idempotencyKeys;
    private final EventRepository events;
    private final PaymentGateway payments;
    private final OutboxWriter outbox;
    private final Counter confirmed;
    private final Counter replayed;

    public BookingService(SeatHoldRepository holds, SeatRepository seats, BookingRepository bookings,
                          IdempotencyKeyRepository idempotencyKeys, EventRepository events,
                          PaymentGateway payments, OutboxWriter outbox, MeterRegistry meters) {
        this.holds = holds;
        this.seats = seats;
        this.bookings = bookings;
        this.idempotencyKeys = idempotencyKeys;
        this.events = events;
        this.payments = payments;
        this.outbox = outbox;
        this.confirmed = meters.counter("seatsure.bookings", "result", "confirmed");
        this.replayed = meters.counter("seatsure.bookings", "result", "idempotent_replay");
    }

    /**
     * Turns a hold into a booking exactly once.
     *
     * 1. Same Idempotency-Key seen before → return that booking (no second charge).
     * 2. Lock the hold row (SELECT ... FOR UPDATE) so concurrent confirms queue up.
     * 3. Re-check the key after the lock: a parallel request may have just finished.
     * 4. Hold already CONFIRMED → return its booking (natural idempotency).
     * 5. Hold expired → 410 Gone.
     * 6. Charge, flip seats HELD → BOOKED, save booking + key + outbox events. One commit.
     *
     * READ_COMMITTED matters here: under MySQL's default REPEATABLE_READ, a request
     * that waited on the lock would still read its old snapshot and miss the
     * booking the first request just committed.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BookingView confirm(Long userId, String holdId, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 64) {
            throw new IllegalArgumentException("Idempotency-Key header must be 1-64 characters");
        }

        Optional<BookingView> previous = replay(userId, holdId, idempotencyKey);
        if (previous.isPresent()) return previous.get();

        SeatHold hold = holds.findForUpdate(holdId)
                .filter(h -> h.getUserId().equals(userId))
                .orElseThrow(() -> new NotFoundException("Hold not found"));

        previous = replay(userId, holdId, idempotencyKey);
        if (previous.isPresent()) return previous.get();

        if (hold.getStatus() == HoldStatus.CONFIRMED) {
            Booking existing = bookings.findByHoldId(holdId).orElseThrow();
            replayed.increment();
            return view(existing);
        }
        if (!hold.isActiveAt(Instant.now())) {
            throw new GoneException("Your seat hold has expired and the seats were released. Please select again.");
        }

        List<Seat> heldSeats = seats.findByHoldId(holdId).stream()
                .filter(s -> s.getStatus() == SeatStatus.HELD)
                .sorted(Comparator.comparing(Seat::getRowLabel).thenComparingInt(Seat::getSeatNumber))
                .toList();
        if (heldSeats.isEmpty()) {
            throw new GoneException("Your seat hold is no longer valid. Please select again.");
        }
        BigDecimal total = heldSeats.stream().map(Seat::getPrice).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<Long> seatIds = heldSeats.stream().map(Seat::getId).toList();
        String labels = heldSeats.stream().map(Seat::label).collect(Collectors.joining(","));

        payments.charge(userId, total, idempotencyKey);

        // Mark first: the bulk UPDATE below flushes pending changes, then clears the context.
        hold.markConfirmed();
        int booked = seats.bookHeldSeats(holdId, SeatStatus.BOOKED, SeatStatus.HELD);
        if (booked != heldSeats.size()) {
            // Should never happen while we hold the lock; fail loudly and roll back everything.
            throw new ConflictException("Seat state changed during checkout. Please try again.");
        }

        Booking booking = bookings.save(new Booking(newReference(), hold.getEventId(), userId, holdId, total, labels));
        idempotencyKeys.save(new IdempotencyKey(userId, idempotencyKey, booking.getId()));
        outbox.seatsChanged(hold.getEventId(), seats.findAllById(seatIds));
        outbox.bookingConfirmed(booking.getReference(), userId, hold.getEventId());
        confirmed.increment();
        return view(booking);
    }

    @Transactional(readOnly = true)
    public List<BookingView> myBookings(Long userId) {
        List<Booking> list = bookings.findByUserIdOrderByCreatedAtDesc(userId);
        Map<Long, Event> eventsById = events.findAllById(list.stream().map(Booking::getEventId).distinct().toList())
                .stream().collect(Collectors.toMap(Event::getId, Function.identity()));
        return list.stream().map(b -> view(b, eventsById.get(b.getEventId()))).toList();
    }

    private Optional<BookingView> replay(Long userId, String holdId, String key) {
        return idempotencyKeys.findById(new IdempotencyKey.Key(userId, key))
                .flatMap(k -> bookings.findById(k.getBookingId()))
                .map(b -> {
                    if (!b.getHoldId().equals(holdId)) {
                        throw new ConflictException("This Idempotency-Key was already used for a different hold");
                    }
                    replayed.increment();
                    return view(b);
                });
    }

    private BookingView view(Booking b) {
        return view(b, events.findById(b.getEventId()).orElse(null));
    }

    private static BookingView view(Booking b, Event e) {
        return new BookingView(b.getReference(), b.getEventId(),
                e != null ? e.getTitle() : null, e != null ? e.getVenue() : null,
                e != null ? e.getStartsAt() : null,
                Arrays.asList(b.getSeatLabels().split(",")),
                b.getTotalAmount(), b.getStatus(), b.getCreatedAt());
    }

    private static String newReference() {
        StringBuilder sb = new StringBuilder("SS-");
        for (int i = 0; i < 8; i++) sb.append(REF_ALPHABET[RANDOM.nextInt(REF_ALPHABET.length)]);
        return sb.toString();
    }
}
