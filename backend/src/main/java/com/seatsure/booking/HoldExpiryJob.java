package com.seatsure.booking;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Sweeps expired holds back into inventory. Each hold is released in its own
 * small transaction, so one failure never blocks the rest and locks stay short.
 */
@Component
public class HoldExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(HoldExpiryJob.class);
    private static final int BATCH = 200;

    private final SeatHoldRepository holds;
    private final HoldService holdService;

    public HoldExpiryJob(SeatHoldRepository holds, HoldService holdService) {
        this.holds = holds;
        this.holdService = holdService;
    }

    @Scheduled(fixedDelayString = "${seatsure.hold.expiry-sweep-ms}")
    public void sweep() {
        List<String> due = holds.findExpiredIds(HoldStatus.ACTIVE, Instant.now(), Limit.of(BATCH));
        int released = 0;
        for (String id : due) {
            try {
                if (holdService.expireIfDue(id)) released++;
            } catch (RuntimeException e) {
                log.warn("Could not expire hold {}: {}", id, e.getMessage());
            }
        }
        if (released > 0) {
            log.info("Released {} expired seat hold(s)", released);
        }
    }
}
