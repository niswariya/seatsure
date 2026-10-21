package com.seatsure.event;

import com.seatsure.common.NotFoundException;
import com.seatsure.event.EventDtos.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class EventService {

    private final EventRepository events;
    private final SeatRepository seats;

    public EventService(EventRepository events, SeatRepository seats) {
        this.events = events;
        this.seats = seats;
    }

    /** Creates the event and its whole seat grid in one transaction. */
    @Transactional
    public EventSummary create(CreateEventRequest req) {
        Event event = events.save(new Event(req.title(), req.venue(), req.startsAt(),
                req.description(), req.seatRows(), req.seatsPerRow()));

        List<Seat> grid = new ArrayList<>(req.seatRows() * req.seatsPerRow());
        for (int r = 0; r < req.seatRows(); r++) {
            String rowLabel = String.valueOf((char) ('A' + r));
            SeatTier tier = tierFor(r, req.seatRows());
            BigDecimal price = switch (tier) {
                case PREMIUM -> req.premiumPrice();
                case STANDARD -> req.standardPrice();
                case ECONOMY -> req.economyPrice();
            };
            for (int n = 1; n <= req.seatsPerRow(); n++) {
                grid.add(new Seat(event.getId(), rowLabel, n, tier, price));
            }
        }
        seats.saveAll(grid);
        return summary(event, grid);
    }

    /** Two queries total, however many events: events, then one GROUP BY for all their seat stats. */
    @Transactional(readOnly = true)
    public List<EventSummary> upcoming() {
        List<Event> list = events.findByStartsAtAfterOrderByStartsAtAsc(Instant.now());
        if (list.isEmpty()) return List.of();
        Map<Long, SeatStats> stats = seats.statsFor(list.stream().map(Event::getId).toList(), SeatStatus.AVAILABLE)
                .stream().collect(Collectors.toMap(SeatStats::eventId, Function.identity()));
        return list.stream().map(e -> {
            SeatStats st = stats.get(e.getId());
            return st == null
                    ? new EventSummary(e.getId(), e.getTitle(), e.getVenue(), e.getStartsAt(), 0, 0, BigDecimal.ZERO)
                    : new EventSummary(e.getId(), e.getTitle(), e.getVenue(), e.getStartsAt(),
                            st.totalSeats().intValue(), st.availableSeats(), st.minPrice());
        }).toList();
    }

    @Transactional(readOnly = true)
    public EventDetail detail(Long eventId) {
        Event e = events.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event " + eventId + " not found"));
        List<SeatView> seatViews = seats.findByEventIdOrderByRowLabelAscSeatNumberAsc(eventId)
                .stream().map(SeatView::of).toList();
        return new EventDetail(e.getId(), e.getTitle(), e.getVenue(), e.getStartsAt(),
                e.getDescription(), e.getSeatRows(), e.getSeatsPerRow(), seatViews);
    }

    /** Front ~20% of rows premium, back ~30% economy, the rest standard. */
    static SeatTier tierFor(int rowIndex, int totalRows) {
        double position = (rowIndex + 0.5) / totalRows;
        if (position <= 0.2) return SeatTier.PREMIUM;
        if (position > 0.7) return SeatTier.ECONOMY;
        return SeatTier.STANDARD;
    }

    private EventSummary summary(Event e, List<Seat> grid) {
        long available = grid.stream().filter(s -> s.getStatus() == SeatStatus.AVAILABLE).count();
        BigDecimal from = grid.stream().map(Seat::getPrice).min(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);
        return new EventSummary(e.getId(), e.getTitle(), e.getVenue(), e.getStartsAt(),
                grid.size(), available, from);
    }
}
