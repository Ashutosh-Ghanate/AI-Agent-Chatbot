package com.studentloan.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Request body for POST /api/ai/query.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AiQueryRequest(String question, List<ConversationMessage> conversation) {

    /** Backwards-compatible constructor for clients sending only a question. */
    public AiQueryRequest(String question) {
        this(question, null);
    }
}