package com.studentloan.dto;

import java.util.List;
import java.util.Map;

/**
 * Response body for POST /api/ai/query.
 *
 * `answer` is the natural-language message the chatbot should display.
 * `columns`/`rows` are the structured PostgreSQL result set the React UI
 * renders as a table.
 * `chart` is optional chart metadata (type + which of those columns to plot) and is
 * null whenever a chart is not appropriate. It carries no data of its own - the chart
 * and the table always describe the same rows.
 * `sql` is included only for debugging/inspection - the frontend must
 * NEVER render it as the chatbot's normal response.
 */
public record AiQueryResponse(
        String question,
        String answer,
        String sql,
        List<String> columns,
        List<Map<String, Object>> rows,
        ChartConfig chart) {

    /**
     * Kept for the callers that have no chart to report (clarifications, rejected SQL,
     * empty results), so adding charts did not change the existing contract.
     */
    public AiQueryResponse(String question, String answer, String sql,
            List<String> columns, List<Map<String, Object>> rows) {
        this(question, answer, sql, columns, rows, null);
    }
}
