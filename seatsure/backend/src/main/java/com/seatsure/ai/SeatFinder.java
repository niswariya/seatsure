package com.seatsure.ai;

import com.seatsure.event.SeatTier;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Deterministic seat search used by the AI assistant's tool.
 *
 * The LLM decides WHAT the user wants (2 seats, together, under ₹3000);
 * plain Java decides WHICH seats match. Seat data never comes from the model,
 * so it can't hallucinate a seat that doesn't exist or is already sold.
 */
public final class SeatFinder {

    /** An available seat, decoupled from JPA so this class is trivially unit-testable. */
    public record Candidate(long id, String row, int number, SeatTier tier, BigDecimal price) {
        String label() { return row + number; }
    }

    public record Option(String description, List<Long> seatIds, List<String> seatLabels,
                         SeatTier tier, BigDecimal totalPrice) {}

    private SeatFinder() {}

    /**
     * @param available      seats currently AVAILABLE for the event
     * @param totalRows      rows in the hall (row A = front)
     * @param seatsPerRow    seats per row
     * @param count          seats wanted (1..6)
     * @param maxPricePerSeat null = no limit
     * @param tier           null = any tier
     * @param together       seats must be adjacent in the same row
     * @return up to 3 options, best first (front and centre wins)
     */
    public static List<Option> find(List<Candidate> available, int totalRows, int seatsPerRow, int count,
                                    BigDecimal maxPricePerSeat, SeatTier tier, boolean together) {
        if (count < 1 || count > 6) throw new IllegalArgumentException("count must be between 1 and 6");

        List<Candidate> pool = available.stream()
                .filter(c -> maxPricePerSeat == null || c.price().compareTo(maxPricePerSeat) <= 0)
                .filter(c -> tier == null || c.tier() == tier)
                .toList();

        Comparator<List<Candidate>> byScore = Comparator.comparingDouble(g -> score(g, seatsPerRow));

        if (!together) {
            List<Candidate> best = pool.stream()
                    .sorted(Comparator.comparingDouble(c -> score(List.of(c), seatsPerRow)))
                    .limit(count).toList();
            return best.size() == count ? List.of(toOption(best, "Best available seats")) : List.of();
        }

        // Every run of `count` consecutive seat numbers in a row is a candidate group.
        Map<String, List<Candidate>> byRow = pool.stream()
                .collect(Collectors.groupingBy(Candidate::row, TreeMap::new, Collectors.toList()));
        List<List<Candidate>> groups = new ArrayList<>();
        for (List<Candidate> row : byRow.values()) {
            List<Candidate> sorted = row.stream().sorted(Comparator.comparingInt(Candidate::number)).toList();
            for (int i = 0; i + count <= sorted.size(); i++) {
                List<Candidate> window = sorted.subList(i, i + count);
                if (window.get(count - 1).number() - window.get(0).number() == count - 1) {
                    groups.add(window);
                }
            }
        }

        // Best three, at most one per row so the user gets real alternatives.
        List<Option> options = new ArrayList<>();
        Set<String> usedRows = new HashSet<>();
        for (List<Candidate> g : groups.stream().sorted(byScore).toList()) {
            if (usedRows.add(g.get(0).row())) {
                options.add(toOption(g, "Row " + g.get(0).row() + ", seats "
                        + g.get(0).number() + (count > 1 ? "–" + g.get(count - 1).number() : "")));
            }
            if (options.size() == 3) break;
        }
        return options;
    }

    /** Lower is better: front rows first, then closeness to the centre of the row. */
    static double score(List<Candidate> group, int seatsPerRow) {
        double rowIndex = group.get(0).row().charAt(0) - 'A';
        double centre = (seatsPerRow + 1) / 2.0;
        double groupCentre = group.stream().mapToInt(Candidate::number).average().orElse(centre);
        return rowIndex + 2.0 * Math.abs(groupCentre - centre) / seatsPerRow;
    }

    private static Option toOption(List<Candidate> picked, String description) {
        List<Candidate> seats = picked.stream()
                .sorted(Comparator.comparing(Candidate::row).thenComparingInt(Candidate::number)).toList();
        BigDecimal total = seats.stream().map(Candidate::price).reduce(BigDecimal.ZERO, BigDecimal::add);
        SeatTier tier = seats.get(0).tier();
        return new Option(description, seats.stream().map(Candidate::id).toList(),
                seats.stream().map(Candidate::label).toList(), tier, total);
    }
}
