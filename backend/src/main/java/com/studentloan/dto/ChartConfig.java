package com.studentloan.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Chart metadata for a chatbot answer, when a visualisation adds value.
 *
 * The chart never carries its own data: every value is read from the {@code columns} /
 * {@code rows} of the same {@link AiQueryResponse}, so the React UI plots exactly the rows
 * that are displayed underneath the chart and nothing can be invented.
 *
 * Only four types are supported. {@code bar}/{@code line}/{@code scatter} use
 * {@code xColumn} + {@code yColumn}, a {@code pie} uses {@code nameColumn} +
 * {@code valueColumn}; the columns that do not apply to a type are omitted from the JSON
 * (see {@link JsonInclude}).
 *
 * {@code chart} is simply null on {@link AiQueryResponse} when a chart is not appropriate.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChartConfig(
        String type,
        String xColumn,
        String yColumn,
        String nameColumn,
        String valueColumn,
        String title) {

    public static final String BAR = "bar";
    public static final String LINE = "line";
    public static final String PIE = "pie";
    public static final String SCATTER = "scatter";

    /** Chart types the frontend can draw and the backend therefore accepts. */
    public static final java.util.List<String> SUPPORTED_TYPES = java.util.List.of(BAR, LINE, PIE, SCATTER);

    /** A categorical comparison: one category axis and one numeric axis. */
    public static ChartConfig bar(String xColumn, String yColumn, String title) {
        return new ChartConfig(BAR, xColumn, yColumn, null, null, title);
    }

    /** A trend over time (or over an ordered numeric axis). */
    public static ChartConfig line(String xColumn, String yColumn, String title) {
        return new ChartConfig(LINE, xColumn, yColumn, null, null, title);
    }

    /** A part-to-whole distribution: one label column and one numeric slice column. */
    public static ChartConfig pie(String nameColumn, String valueColumn, String title) {
        return new ChartConfig(PIE, null, null, nameColumn, valueColumn, title);
    }

    /** The relationship between two numeric columns. */
    public static ChartConfig scatter(String xColumn, String yColumn, String title) {
        return new ChartConfig(SCATTER, xColumn, yColumn, null, null, title);
    }

    /** True when this type plots an x/y pair rather than name/value slices. */
    public boolean isPairType() {
        return BAR.equals(type) || LINE.equals(type) || SCATTER.equals(type);
    }
}
