package com.seatsure.booking;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SeatHoldRepository extends JpaRepository<SeatHold, String> {

    /**
     * SELECT ... FOR UPDATE on the hold row. Serialises concurrent confirm /
     * release / expire calls for the SAME hold, without blocking other holds.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT h FROM SeatHold h WHERE h.id = :id")
    Optional<SeatHold> findForUpdate(@Param("id") String id);

    boolean existsByUserIdAndEventIdAndStatusAndExpiresAtAfter(Long userId, Long eventId,
                                                               HoldStatus status, Instant now);

    @Query("SELECT h.id FROM SeatHold h WHERE h.status = :status AND h.expiresAt < :now ORDER BY h.expiresAt")
    List<String> findExpiredIds(@Param("status") HoldStatus status, @Param("now") Instant now, Limit limit);
}
