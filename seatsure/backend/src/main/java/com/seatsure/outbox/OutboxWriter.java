package com.seatsure.outbox;

import com.seatsure.event.Seat;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

/**
 * Transactional outbox: domain changes and the events describing them are
 * written in the SAME database transaction. Either both commit or neither does,
 * so a seat can never be booked without clients hearing about it (and they
 * never hear about a change that rolled back).
 */
@Component
public class OutboxWriter {

    public static final String SEATS_CHANGED = "SEATS_CHANGED";
    public static final String BOOKING_CONFIRMED = "BOOKING_CONFIRMED";

    private final OutboxRepository repository;
    private final JsonMapper json;

    public OutboxWriter(OutboxRepository repository, JsonMapper json) {
        this.repository = repository;
        this.json = json;
    }

    @Transactional(propagation = Propagation.MANDATORY)  // must join the caller's transaction
    public void seatsChanged(Long eventId, List<Seat> seats) {
        List<Map<String, Object>> changes = seats.stream()
                .map(s -> Map.<String, Object>of(
                        "id", s.getId(),
                        "status", s.getStatus().name(),
                        "version", s.getVersion()))
                .toList();
        write(String.valueOf(eventId), SEATS_CHANGED, Map.of("eventId", eventId, "seats", changes));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void bookingConfirmed(String reference, Long userId, Long eventId) {
        write(reference, BOOKING_CONFIRMED,
                Map.of("reference", reference, "userId", userId, "eventId", eventId));
    }

    private void write(String aggregateId, String type, Map<String, Object> payload) {
        repository.save(new OutboxEvent(aggregateId, type, json.writeValueAsString(payload)));
    }
}
