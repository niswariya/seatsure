package com.seatsure.ai;

import com.seatsure.ai.SeatFinder.Candidate;
import com.seatsure.ai.SeatFinder.Option;
import com.seatsure.event.SeatTier;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Plain unit tests — no Spring, no database, no AWS. */
class SeatFinderTest {

    /** 3 rows x 8 seats. Row A premium ₹3000, B standard ₹2000, C economy ₹1000. */
    private static List<Candidate> hall(Set<String> taken) {
        List<Candidate> seats = new ArrayList<>();
        long id = 1;
        String[] rows = {"A", "B", "C"};
        SeatTier[] tiers = {SeatTier.PREMIUM, SeatTier.STANDARD, SeatTier.ECONOMY};
        int[] prices = {3000, 2000, 1000};
        for (int r = 0; r < 3; r++) {
            for (int n = 1; n <= 8; n++) {
                if (!taken.contains(rows[r] + n)) {
                    seats.add(new Candidate(id, rows[r], n, tiers[r], BigDecimal.valueOf(prices[r])));
                }
                id++;
            }
        }
        return seats;
    }

    @Test
    void prefersFrontAndCentreForAdjacentSeats() {
        List<Option> options = SeatFinder.find(hall(Set.of()), 3, 8, 2, null, null, true);

        assertThat(options).hasSize(3);
        assertThat(options.get(0).seatLabels()).containsExactly("A4", "A5");
        // one option per row, so the user gets real alternatives
        assertThat(options).extracting(o -> o.seatLabels().get(0).substring(0, 1)).containsExactly("A", "B", "C");
    }

    @Test
    void skipsGapsCreatedByTakenSeats() {
        // A4 and A5 are gone; the best adjacent pair in row A is now A2-A3 or A6-A7.
        List<Option> options = SeatFinder.find(hall(Set.of("A4", "A5")), 3, 8, 2, null, SeatTier.PREMIUM, true);

        assertThat(options).hasSize(1);
        assertThat(options.get(0).seatLabels()).isNotEmpty().doesNotContain("A4", "A5");
        int first = Integer.parseInt(options.get(0).seatLabels().get(0).substring(1));
        int second = Integer.parseInt(options.get(0).seatLabels().get(1).substring(1));
        assertThat(second - first).isEqualTo(1);
    }

    @Test
    void respectsBudget() {
        List<Option> options = SeatFinder.find(hall(Set.of()), 3, 8, 3, BigDecimal.valueOf(1500), null, true);

        assertThat(options).hasSize(1);
        assertThat(options.get(0).tier()).isEqualTo(SeatTier.ECONOMY);
        assertThat(options.get(0).totalPrice()).isEqualByComparingTo("3000");
    }

    @Test
    void returnsNothingWhenNoAdjacentRunExists() {
        // Only every other seat is free in row C.
        List<Option> options = SeatFinder.find(hall(Set.of("C2", "C4", "C6", "C8")), 3, 8, 2, null, SeatTier.ECONOMY, true);
        assertThat(options).isEmpty();
    }

    @Test
    void canPickScatteredSeatsWhenTogetherIsNotRequired() {
        List<Option> options = SeatFinder.find(hall(Set.of("C2", "C4", "C6", "C8")), 3, 8, 2, null, SeatTier.ECONOMY, false);
        assertThat(options).hasSize(1);
        assertThat(options.get(0).seatIds()).hasSize(2);
    }

    @Test
    void rejectsSillyCounts() {
        assertThatThrownBy(() -> SeatFinder.find(hall(Set.of()), 3, 8, 0, null, null, true))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
