package com.seatsure.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Relays committed outbox rows to their destination. Delivery is
 * at-least-once: if the app dies after sending but before marking a row,
 * it is sent again. Clients handle that by comparing seat versions.
 */
@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private static final int BATCH = 100;

    private final OutboxRepository repository;
    private final SimpMessagingTemplate messaging;

    public OutboxPublisher(OutboxRepository repository, SimpMessagingTemplate messaging) {
        this.repository = repository;
        this.messaging = messaging;
    }

    @Scheduled(fixedDelayString = "${seatsure.outbox.poll-ms}")
    @Transactional
    public void publishPending() {
        List<OutboxEvent> batch = repository.lockNextBatch(BATCH);
        for (OutboxEvent e : batch) {
            switch (e.getEventType()) {
                case OutboxWriter.SEATS_CHANGED ->
                        messaging.convertAndSend("/topic/events/" + e.getAggregateId() + "/seats", e.getPayload());
                case OutboxWriter.BOOKING_CONFIRMED ->
                        // Hook for e-mail tickets / analytics. In production this goes to SQS or Kafka.
                        log.info("Booking confirmed: {}", e.getPayload());
                default -> log.warn("Unknown outbox event type {}", e.getEventType());
            }
            e.markPublished();
        }
    }
}
