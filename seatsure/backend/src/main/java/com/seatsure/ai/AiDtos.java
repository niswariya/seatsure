package com.seatsure.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class AiDtos {

    private AiDtos() {}

    public record ChatRequest(
            @NotBlank @Pattern(regexp = "[A-Za-z0-9-]{8,64}", message = "must be 8-64 letters, digits or dashes")
            String conversationId,
            @NotBlank @Size(max = 2000) String message) {}

    /** The model's text plus seat options produced by the findSeats tool (rendered as buttons). */
    public record ChatReply(String reply, Long eventId, List<SeatFinder.Option> suggestions) {}

    public record EventCopyRequest(
            @NotBlank @Size(max = 150) String title,
            @NotBlank @Size(max = 150) String venue,
            Instant startsAt,
            @Size(max = 300) String notes) {}

    /** Structured output: Spring AI maps the model's JSON straight onto this record. */
    public record EventCopy(String description, List<String> highlights) {}
}
