package com.studentloan.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Request body for POST /api/ai/chat.
 *
 * <p>{@code conversation} is optional short-term history (most recent turns
 * first or last - order is preserved as sent) that helps resolve follow-up
 * references ("them", "top 5"). Clients sending only {@code message} keep
 * working exactly as before.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AiChatRequest(String message, List<ConversationMessage> conversation) {

    /** Backwards-compatible constructor for clients sending only a message. */
    public AiChatRequest(String message) {
        this(message, null);
    }
}
