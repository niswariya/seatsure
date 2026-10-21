package com.seatsure.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OutboxRepository extends JpaRepository<OutboxEvent, Long> {

    /**
     * SKIP LOCKED (MySQL 8+) lets several app instances drain the outbox in
     * parallel: each grabs a different batch instead of waiting on the same rows.
     */
    @Query(value = """
            SELECT * FROM outbox_events
             WHERE published_at IS NULL
             ORDER BY id
             LIMIT :batch
             FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEvent> lockNextBatch(@Param("batch") int batch);

    long countByPublishedAtIsNull();
}
