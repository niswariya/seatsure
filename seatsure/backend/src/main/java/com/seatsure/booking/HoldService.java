package com.seatsure.booking;

import com.seatsure.booking.BookingDtos.HoldView;
import com.seatsure.common.ConflictException;
import com.seatsure.common.NotFoundException;
import com.seatsure.config.SeatSureProperties;
import com.seatsure.event.Event;
import com.seatsure.event.EventDtos.SeatView;
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
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class HoldService {

    private final SeatRepository seats;
    private final SeatHoldRepository holds;
    private final EventRepository events;
    private final OutboxWriter outbox;
    private final Duration holdTtl;
    private final int maxSeats;
    private final Counter holdsCreated;
    private final Counter holdConflicts;
    private final Counter holdsExpired;

    public HoldService(SeatRepository seats, SeatHoldRepository holds, EventRepository events,
                       OutboxWriter outbox, SeatSureProperties props, MeterRegistry meters) {
        this.seats = seats;
        this.holds = holds;
        this.events = events;
        this.outbox = outbox;
        this.holdTtl = Duration.ofSeconds(props.hold().ttlSeconds());
        this.maxSeats = props.hold().maxSeats();
        this.holdsCreated = meters.counter("seatsure.holds", "result", "created");
        this.holdConflicts = meters.counter("seatsure.holds", "result", "conflict");
        this.holdsExpired = meters.counter("seatsure.holds", "result", "expired");
    }

    /**
     * Atomically holds ALL requested seats or NONE of them.
     * The guarded UPDATE either claims every seat or the count comes back short,
     * in which case the exception rolls the whole transaction back.
     * READ_COMMITTED avoids InnoDB gap locks, which cuts deadlocks between overlapping requests.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public HoldView hold(Long userId, Long eventId, List<Long> requestedSeatIds) {
        Set<Long> seatIds = new LinkedHashSet<>(requestedSeatIds);
        if (seatIds.size() > maxSeats) {
            throw new IllegalArgumentException("You can hold at most " + maxSeats + " seats at a time");
        }
        Event event = events.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event " + eventId + " not found"));
        Instant now = Instant.now();
        if (!event.getStartsAt().isAfter(now)) {
            throw new ConflictException("This event has already started");
        }
        if (holds.existsByUserIdAndEventIdAndStatusAndExpiresAtAfter(userId, eventId, HoldStatus.ACTIVE, now)) {
            throw new ConflictException("You already have seats on hold for this event. Confirm or release them first.");
        }

        String holdId = UUID.randomUUID().toString();
        SeatHold hold = holds.save(new SeatHold(holdId, eventId, userId, now.plus(holdTtl)));

        int claimed = seats.holdIfAvailable(eventId, seatIds, holdId, SeatStatus.HELD, SeatStatus.AVAILABLE);
        if (claimed != seatIds.size()) {
            holdConflicts.increment();
            throw new ConflictException("Sorry, one or more of those seats was just taken. Please pick again.");
        }

        List<Seat> held = seats.findByHoldId(holdId);
        outbox.seatsChanged(eventId, held);
        holdsCreated.increment();
        return view(hold, held);
    }

    /** User changed their mind: give the seats back immediately. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void release(Long userId, String holdId) {
        SeatHold hold = holds.findForUpdate(holdId)
                .filter(h -> h.getUserId().equals(userId))
                .orElseThrow(() -> new NotFoundException("Hold not found"));
        if (hold.getStatus() != HoldStatus.ACTIVE) {
            return; // already released, expired or confirmed: nothing to do (idempotent)
        }
        // Mark first: the bulk UPDATE in freeSeats flushes pending changes, then clears the context.
        hold.markReleased();
        freeSeats(hold);
    }

    /**
     * Called by {@link HoldExpiryJob}, one hold per transaction. Safe to run on
     * several instances at once: the row lock plus the status check means only
     * one of them does the work.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public boolean expireIfDue(String holdId) {
        SeatHold hold = holds.findForUpdate(holdId).orElse(null);
        if (hold == null || hold.isActiveAt(Instant.now()) || hold.getStatus() != HoldStatus.ACTIVE) {
            return false;
        }
        hold.markExpired();
        freeSeats(hold);
        holdsExpired.increment();
        return true;
    }

    @Transactional(readOnly = true)
    public HoldView get(Long userId, String holdId) {
        SeatHold hold = holds.findById(holdId)
                .filter(h -> h.getUserId().equals(userId))
                .orElseThrow(() -> new NotFoundException("Hold not found"));
        return view(hold, seats.findByHoldId(holdId));
    }

    private void freeSeats(SeatHold hold) {
        List<Long> ids = seats.findByHoldId(hold.getId()).stream()
                .filter(s -> s.getStatus() == SeatStatus.HELD)
                .map(Seat::getId).toList();
        seats.releaseHeldSeats(hold.getId(), SeatStatus.AVAILABLE, SeatStatus.HELD);
        if (!ids.isEmpty()) {
            outbox.seatsChanged(hold.getEventId(), seats.findAllById(ids));
        }
    }

    static HoldView view(SeatHold hold, List<Seat> held) {
        BigDecimal total = held.stream().map(Seat::getPrice).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new HoldView(hold.getId(), hold.getEventId(), hold.getExpiresAt(),
                held.stream().map(SeatView::of).toList(), total);
    }
}
