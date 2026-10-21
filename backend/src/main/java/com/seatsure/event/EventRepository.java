package com.seatsure.event;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface EventRepository extends JpaRepository<Event, Long> {
    List<Event> findByStartsAtAfterOrderByStartsAtAsc(Instant from);
}
