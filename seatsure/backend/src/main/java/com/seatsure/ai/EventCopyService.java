package com.seatsure.ai;

import com.seatsure.ai.AiDtos.EventCopy;
import com.seatsure.ai.AiDtos.EventCopyRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Admin helper: drafts an event description. Shows structured output —
 * Spring AI adds a JSON schema for {@link EventCopy} to the prompt and maps
 * the reply onto the record, so the code never parses free text.
 */
@Service
@ConditionalOnProperty(prefix = "seatsure.ai", name = "enabled", havingValue = "true")
public class EventCopyService {

    private static final Logger log = LoggerFactory.getLogger(EventCopyService.class);

    private final ChatClient writer;

    public EventCopyService(@Qualifier("writerClient") ChatClient writer) {
        this.writer = writer;
    }

    public EventCopy draft(EventCopyRequest req) {
        String when = req.startsAt() == null ? "not set yet"
                : DateTimeFormatter.ofPattern("EEEE d MMMM yyyy, h:mm a").withZone(ZoneId.of("Asia/Kolkata")).format(req.startsAt());
        try {
            EventCopy copy = writer.prompt()
                    .system("""
                            You write short, appealing event listings for a ticketing site in India.
                            Use only the facts given. Do not invent performers, speakers, prices or amenities.
                            description: 2 to 3 sentences, at most 600 characters.
                            highlights: 3 short phrases, each under 40 characters.""")
                    .user(u -> u.text("""
                            Title: {title}
                            Venue: {venue}
                            Date: {when}
                            Organiser notes: {notes}""")
                            .param("title", req.title())
                            .param("venue", req.venue())
                            .param("when", when)
                            .param("notes", req.notes() == null ? "none" : req.notes()))
                    .call()
                    .entity(EventCopy.class);
            return sanitize(copy);
        } catch (RuntimeException e) {
            log.error("Event copy generation failed", e);
            throw new AiUnavailableException("Couldn't draft a description right now. Please try again.", e);
        }
    }

    /** Never trust model output blindly: enforce the limits the database and UI rely on. */
    static EventCopy sanitize(EventCopy copy) {
        if (copy == null || copy.description() == null) {
            throw new IllegalStateException("Model returned no description");
        }
        String description = copy.description().strip();
        if (description.length() > 1000) description = description.substring(0, 1000);
        List<String> highlights = copy.highlights() == null ? List.of()
                : copy.highlights().stream().filter(h -> h != null && !h.isBlank())
                        .map(h -> h.length() > 60 ? h.substring(0, 60) : h).limit(5).toList();
        return new EventCopy(description, highlights);
    }
}
