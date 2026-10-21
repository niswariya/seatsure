package com.seatsure.ai;

import java.util.List;

/**
 * Per-request holder passed to tools through Spring AI's ToolContext.
 * Whatever seat options the tool last produced are returned to the UI as
 * clickable suggestions — alongside the model's text, never parsed out of it.
 */
public final class SuggestionCollector {

    public static final String KEY = "suggestions";

    private volatile Long eventId;
    private volatile List<SeatFinder.Option> options = List.of();

    void record(Long eventId, List<SeatFinder.Option> options) {
        this.eventId = eventId;
        this.options = List.copyOf(options);
    }

    public Long eventId() { return eventId; }
    public List<SeatFinder.Option> options() { return options; }
}
