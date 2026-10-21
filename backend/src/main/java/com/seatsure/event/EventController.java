package com.seatsure.event;

import com.seatsure.event.EventDtos.CreateEventRequest;
import com.seatsure.event.EventDtos.EventDetail;
import com.seatsure.event.EventDtos.EventSummary;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class EventController {

    private final EventService service;

    public EventController(EventService service) {
        this.service = service;
    }

    @GetMapping("/api/events")
    public List<EventSummary> upcoming() {
        return service.upcoming();
    }

    /** Full seat map. Clients load this once, then apply live updates from the WebSocket. */
    @GetMapping("/api/events/{id}")
    public EventDetail detail(@PathVariable Long id) {
        return service.detail(id);
    }

    @PostMapping("/api/admin/events")
    @ResponseStatus(HttpStatus.CREATED)
    public EventSummary create(@Valid @RequestBody CreateEventRequest req) {
        return service.create(req);
    }
}
