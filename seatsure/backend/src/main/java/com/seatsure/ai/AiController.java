package com.seatsure.ai;

import com.seatsure.ai.AiDtos.ChatReply;
import com.seatsure.ai.AiDtos.ChatRequest;
import com.seatsure.ai.AiDtos.EventCopy;
import com.seatsure.ai.AiDtos.EventCopyRequest;
import com.seatsure.common.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(prefix = "seatsure.ai", name = "enabled", havingValue = "true")
public class AiController {

    private final ConciergeService concierge;
    private final EventCopyService eventCopy;

    public AiController(ConciergeService concierge, EventCopyService eventCopy) {
        this.concierge = concierge;
        this.eventCopy = eventCopy;
    }

    /** Signed-in users only (AI calls cost money), rate limited per user. */
    @PostMapping("/api/ai/chat")
    public ChatReply chat(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ChatRequest req) {
        return concierge.chat(CurrentUser.id(jwt), req.conversationId(), req.message());
    }

    /** Admin only (enforced by SecurityConfig on /api/admin/**). */
    @PostMapping("/api/admin/ai/event-copy")
    public EventCopy draftEventCopy(@Valid @RequestBody EventCopyRequest req) {
        return eventCopy.draft(req);
    }
}
