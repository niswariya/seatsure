package com.seatsure.ai;

import com.seatsure.event.Event;
import com.seatsure.event.EventDtos.EventSummary;
import com.seatsure.event.EventRepository;
import com.seatsure.event.EventService;
import com.seatsure.event.SeatRepository;
import com.seatsure.event.SeatStatus;
import com.seatsure.event.SeatTier;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

/**
 * Functions the model may call. Each one is read-only: the assistant can look
 * things up and suggest seats, but holding and paying always need a human click.
 * Registered as a bean in {@link AiConfig} only when AI is enabled.
 */
public class SeatTools {

    private final EventService eventService;
    private final EventRepository events;
    private final SeatRepository seats;
    private final VectorStore helpCentre;

    public SeatTools(EventService eventService, EventRepository events, SeatRepository seats, VectorStore helpCentre) {
        this.eventService = eventService;
        this.events = events;
        this.seats = seats;
        this.helpCentre = helpCentre;
    }

    public record EventInfo(long id, String title, String venue, Instant startsAt,
                            BigDecimal cheapestPriceInr, long seatsAvailable) {}

    @Tool(description = """
            List upcoming events. Returns id, title, venue, start time (UTC), the cheapest \
            seat price in INR and how many seats are still available. Call this first to find an event id.""")
    public List<EventInfo> listUpcomingEvents() {
        return eventService.upcoming().stream()
                .map((EventSummary e) -> new EventInfo(e.id(), e.title(), e.venue(), e.startsAt(),
                        e.fromPrice(), e.availableSeats()))
                .toList();
    }

    @Tool(description = """
            Find the best seats that are AVAILABLE right now for one event. Row A is closest to the stage. \
            Returns up to 3 options, best first, each with seat labels and total price in INR. \
            The options are shown to the user as buttons; they select and hold seats themselves.""")
    public List<SeatFinder.Option> findSeats(
            @ToolParam(description = "Event id from listUpcomingEvents") long eventId,
            @ToolParam(description = "How many seats, 1 to 6") int count,
            @ToolParam(description = "Maximum price per seat in INR. Omit for no limit.", required = false)
            Integer maxPricePerSeat,
            @ToolParam(description = "PREMIUM, STANDARD or ECONOMY. Omit for any tier.", required = false)
            String tier,
            @ToolParam(description = "true if the seats must be next to each other", required = false)
            Boolean together,
            ToolContext toolContext) {

        Event event = events.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("No event with id " + eventId));
        List<SeatFinder.Candidate> available = seats.findByEventIdOrderByRowLabelAscSeatNumberAsc(eventId).stream()
                .filter(s -> s.getStatus() == SeatStatus.AVAILABLE)
                .map(s -> new SeatFinder.Candidate(s.getId(), s.getRowLabel(), s.getSeatNumber(), s.getTier(), s.getPrice()))
                .toList();

        List<SeatFinder.Option> options = SeatFinder.find(available, event.getSeatRows(), event.getSeatsPerRow(),
                Math.max(1, Math.min(count, 6)),
                maxPricePerSeat == null || maxPricePerSeat <= 0 ? null : BigDecimal.valueOf(maxPricePerSeat),
                parseTier(tier),
                together == null || together);

        if (toolContext != null && toolContext.getContext().get(SuggestionCollector.KEY) instanceof SuggestionCollector c) {
            c.record(eventId, options);
        }
        return options;
    }

    @Tool(description = """
            Search the SeatSure help centre: refunds and cancellations, entry and ID rules, \
            venue facilities, accessibility, and how holds and payments work. \
            Use this for any policy question and answer only from what it returns.""")
    public List<String> searchHelpCentre(@ToolParam(description = "The user's question, rephrased as a search query") String query) {
        // Retrieval-augmented generation, exposed as a tool ("agentic RAG"):
        // the model decides when it needs the help centre instead of retrieving on every turn.
        List<Document> hits = helpCentre.similaritySearch(SearchRequest.builder()
                .query(query)
                .topK(4)
                .similarityThreshold(0.35)
                .build());
        return hits.stream().map(Document::getText).toList();
    }

    private static SeatTier parseTier(String tier) {
        if (tier == null || tier.isBlank()) return null;
        try {
            return SeatTier.valueOf(tier.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
