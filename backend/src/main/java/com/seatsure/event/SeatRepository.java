package com.seatsure.event;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface SeatRepository extends JpaRepository<Seat, Long> {

    List<Seat> findByEventIdOrderByRowLabelAscSeatNumberAsc(Long eventId);

    List<Seat> findByHoldId(String holdId);

    long countByEventIdAndStatus(Long eventId, SeatStatus status);

    @Query("""
           SELECT new com.seatsure.event.SeatStats(
                    s.eventId,
                    COUNT(s),
                    SUM(CASE WHEN s.status = :available THEN 1L ELSE 0L END),
                    MIN(s.price))
             FROM Seat s
            WHERE s.eventId IN :eventIds
            GROUP BY s.eventId
           """)
    List<SeatStats> statsFor(@Param("eventIds") Collection<Long> eventIds,
                             @Param("available") SeatStatus available);

    /**
     * THE core of the system. A single guarded UPDATE: InnoDB row-locks each
     * matching row, and only rows still AVAILABLE match. If two users race for
     * the same seat, exactly one UPDATE sees it as AVAILABLE; the other gets a
     * smaller row count and its whole transaction rolls back. No SELECT ... FOR
     * UPDATE, no application lock, no distributed lock needed.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
           UPDATE Seat s
              SET s.status = :held, s.holdId = :holdId, s.version = s.version + 1
            WHERE s.eventId = :eventId
              AND s.id IN :seatIds
              AND s.status = :available
           """)
    int holdIfAvailable(@Param("eventId") Long eventId,
                        @Param("seatIds") Collection<Long> seatIds,
                        @Param("holdId") String holdId,
                        @Param("held") SeatStatus held,
                        @Param("available") SeatStatus available);

    /** HELD → BOOKED, only for seats still owned by this hold. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
           UPDATE Seat s
              SET s.status = :booked, s.version = s.version + 1
            WHERE s.holdId = :holdId
              AND s.status = :held
           """)
    int bookHeldSeats(@Param("holdId") String holdId,
                      @Param("booked") SeatStatus booked,
                      @Param("held") SeatStatus held);

    /** HELD → AVAILABLE. Idempotent: running it twice changes nothing the second time. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
           UPDATE Seat s
              SET s.status = :available, s.holdId = NULL, s.version = s.version + 1
            WHERE s.holdId = :holdId
              AND s.status = :held
           """)
    int releaseHeldSeats(@Param("holdId") String holdId,
                         @Param("available") SeatStatus available,
                         @Param("held") SeatStatus held);
}
