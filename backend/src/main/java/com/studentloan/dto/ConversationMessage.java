package com.studentloan.dto;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One turn of short-term conversation history sent by the client.
 *
 * <p>Carries just enough structured context for follow-up questions
 * ("them", "only pending ones", "top 5", "show that as a graph"): the
 * natural-language content plus, for assistant turns, the SQL that was
 * generated, the columns returned and a small sample of rows. The SQL is
 * context only - it is never executed; every follow-up generates fresh SQL
 * through the normal guardrailed pipeline.
 *
 * <p>All fields except {@code role}/{@code content} are optional. History is
 * untrusted data and is only ever rendered into a clearly separated prompt
 * section, never treated as system instructions.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ConversationMessage(
        String role,
        String content,
        String sql,
        List<String> columns,
        List<Map<String, Object>> rows,
        ChartConfig chart) {

    /** Convenience constructor for user turns carrying text only. */
    public ConversationMessage(String role, String content) {
        this(role, content, null, null, null, null);
    }
}
