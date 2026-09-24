package com.studentloan.service;

import java.math.BigDecimal;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.studentloan.dto.ChartConfig;

/**
 * Turns the model's chart suggestion into the chart the API actually returns.
 *
 * The model may ask for a chart, but nothing it says is trusted: every chart is checked
 * against the {@code columns} and {@code rows} that were really returned, and a chart is
 * only kept when it can be drawn from those columns alone.
 *
 * The checks, applied to the <em>values</em> rather than to column names:
 * <ul>
 *   <li>the chart type is one of bar / line / pie / scatter,</li>
 *   <li>every referenced column exists in the result (case-insensitively),</li>
 *   <li>plotted values are numeric, and a line's x-axis is date/time or ordered numeric,</li>
 *   <li>a bar/pie axis is categorical and lists each category once, so a raw row-level
 *       list is never drawn as an aggregate chart,</li>
 *   <li>the category/slice count is small enough to be readable,</li>
 *   <li>there are at least two rows - a single row is better described by the table.</li>
 * </ul>
 *
 * When the model suggests nothing (or something invalid) but the user explicitly asked for
 * a graph, a chart is derived from the shape of the result; otherwise the answer keeps only
 * its table, i.e. {@code chart} is null.
 */
@Service
public class ChartConfigService {

    private static final Logger log = LoggerFactory.getLogger(ChartConfigService.class);

    /** Below this a chart says nothing the table does not say already. */
    private static final int MIN_ROWS = 2;
    /** Categories/slices beyond this are unreadable, so the table is shown on its own. */
    private static final int MAX_CATEGORIES = 25;
    private static final int MAX_SLICES = 12;
    private static final int MAX_TITLE_LENGTH = 80;

    private static final Pattern ISO_DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}([ T].*)?");
    private static final Pattern CLOCK_TIME = Pattern.compile("\\d{1,2}:\\d{2}(:\\d{2})?(\\.\\d+)?");

    /** Wording that means the user asked for a visualisation in so many words. */
    private static final Pattern CHART_REQUEST = Pattern.compile(
            "(?i)\\b(graph|chart|plot|visuali[sz]e|visuali[sz]ation|pie|donut|distribution|trend)\\b");
    private static final Pattern PIE_REQUEST = Pattern.compile(
            "(?i)\\b(pie|donut|share|percentage|distribution|split|proportion)\\b");
    private static final Pattern LINE_REQUEST = Pattern.compile(
            "(?i)\\b(trend|over time|month|monthly|year|yearly|daily|date|timeline)\\b");
    private static final Pattern SCATTER_REQUEST = Pattern.compile(
            "(?i)\\b(vs|versus|against|relationship|correlat\\w*)\\b");

    /** How the returned values of a column behave, read from the rows themselves. */
    private enum Kind {
        NUMERIC, TEMPORAL, TEXT, MIXED, EMPTY
    }

    /** What the model asked for, before any validation. */
    private record ChartRequest(String type, String x, String y, String name, String value, String title) {
    }

    /**
     * Returns the chart to attach to the answer, or null when the result is not chartable.
     *
     * @param question     the user's question, used only to detect an explicit chart request
     * @param rawChartSpec the model's chart suggestion (JSON or key=value), may be null
     * @param columns      the columns that were returned (the only columns a chart may use)
     * @param rows         the rows that were returned (the only data a chart may show)
     */
    public ChartConfig resolve(String question, String rawChartSpec, List<String> columns,
            List<Map<String, Object>> rows) {
        List<String> safeColumns = columns == null ? List.of() : columns;
        List<Map<String, Object>> safeRows = rows == null ? List.of() : rows;

        ChartRequest request = parse(rawChartSpec);
        if (request != null) {
            ChartConfig chart = validate(request, safeColumns, safeRows);
            if (chart != null) {
                log.debug("Chart accepted: {} on {} of {} returned row(s)", chart.type(),
                        chart.isPairType() ? List.of(chart.xColumn(), chart.yColumn())
                                : List.of(chart.nameColumn(), chart.valueColumn()),
                        safeRows.size());
                return chart;
            }
            log.debug("Ignored the model's chart suggestion '{}' - it cannot be drawn from columns {}",
                    rawChartSpec, safeColumns);
        }
        return deriveFromExplicitRequest(question, safeColumns, safeRows);
    }

    /**
     * Reads the model's suggestion. Both a JSON object and a {@code key=value} list are
     * accepted (a small local model rarely gets JSON right every time), and an explicit
     * "no chart" answer is honoured.
     */
    private ChartRequest parse(String rawChartSpec) {
        if (rawChartSpec == null || rawChartSpec.isBlank()) {
            return null;
        }
        String spec = rawChartSpec.trim();
        String lower = spec.toLowerCase(Locale.ROOT);
        if (lower.startsWith("none") || lower.startsWith("no chart") || lower.startsWith("null")
                || lower.startsWith("n/a") || lower.equals("{}") || lower.equals("false")) {
            return null;
        }

        String type = normalizeType(read(spec, "type", "charttype", "chart_type", "kind"));
        if (type == null) {
            // An unsupported type (radar, gauge, ...) means no chart at all: only the four
            // supported types are ever returned.
            return null;
        }
        return new ChartRequest(type,
                read(spec, "xcolumn", "x_column", "xaxis", "x", "category", "categorycolumn", "label"),
                read(spec, "ycolumn", "y_column", "yaxis", "y", "series", "metric"),
                read(spec, "namecolumn", "name_column", "name", "labelcolumn", "piecolumn", "slicename"),
                read(spec, "valuecolumn", "value_column", "value", "slice", "slicecolumn", "count"),
                read(spec, "title", "charttitle", "chart_title", "caption"));
    }

    /**
     * Reads one field out of a JSON object or a {@code key=value} list. A quoted value may
     * contain commas, an unquoted one ends at the first delimiter.
     */
    private String read(String spec, String... aliases) {
        for (String alias : aliases) {
            Matcher matcher = Pattern.compile("(?i)(?<![\\w])[\"']?" + Pattern.quote(alias)
                    + "[\"']?\\s*[:=]\\s*(?:\"([^\"]*)\"|'([^']*)'|([^,;}=\\n]*))").matcher(spec);
            if (matcher.find()) {
                for (int group = 1; group <= 3; group++) {
                    String value = matcher.group(group);
                    if (value != null && !value.isBlank()) {
                        return value.trim();
                    }
                }
            }
        }
        return null;
    }

    /** Maps the many spellings a model may produce onto the four supported types. */
    private String normalizeType(String rawType) {
        if (rawType == null) {
            return null;
        }
        String type = rawType.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
        return switch (type) {
            case "bar", "barchart", "column", "columnchart", "histogram" -> ChartConfig.BAR;
            case "line", "linechart", "area", "areachart", "timeseries" -> ChartConfig.LINE;
            case "pie", "piechart", "donut", "doughnut" -> ChartConfig.PIE;
            case "scatter", "scatterplot", "scatterchart", "bubble" -> ChartConfig.SCATTER;
            default -> null;
        };
    }

    /**
     * Applies every rule to one suggestion. A null result means "show the table only", which
     * is the safe outcome for anything that cannot be proven to fit the returned data.
     */
    private ChartConfig validate(ChartRequest request, List<String> columns, List<Map<String, Object>> rows) {
        // A single row (or no columns at all) is better described by the table itself.
        if (rows.size() < MIN_ROWS || columns.isEmpty()) {
            return null;
        }
        // A pie is often described with x/y wording and a bar with name/value wording, so
        // either pair is accepted and mapped onto the columns the type needs.
        String x = match(columns, request.x() != null ? request.x() : request.name());
        String y = match(columns, request.y() != null ? request.y() : request.value());
        String name = match(columns, request.name() != null ? request.name() : request.x());
        String value = match(columns, request.value() != null ? request.value() : request.y());

        return switch (request.type()) {
            case ChartConfig.BAR -> bar(x, y, request.title(), rows);
            case ChartConfig.LINE -> line(x, y, request.title(), rows);
            case ChartConfig.PIE -> pie(name, value, request.title(), rows);
            case ChartConfig.SCATTER -> scatter(x, y, request.title(), rows);
            default -> null;
        };
    }

    /** Bar: a categorical comparison or ranking - exactly one bar per category. */
    private ChartConfig bar(String category, String value, String rawTitle, List<Map<String, Object>> rows) {
        if (category == null || value == null || category.equals(value)) {
            return null;
        }
        if (kindOf(value, rows) != Kind.NUMERIC) {
            return null;
        }
        Kind categoryKind = kindOf(category, rows);
        if (categoryKind != Kind.TEXT && categoryKind != Kind.TEMPORAL && categoryKind != Kind.NUMERIC) {
            return null;
        }
        int categories = columnDistinct(category, rows);
        if (categories < 2 || categories > MAX_CATEGORIES) {
            return null;
        }
        // Each category must appear once: a plain row list (the same category repeated) is
        // row-level data, not an aggregation, so it is left to the table.
        if (categories != rows.size()) {
            return null;
        }
        return ChartConfig.bar(category, value, title(rawTitle, titleOf(value, category)));
    }

    /** Line: a trend, so the x-axis must be date/time or an ordered numeric axis. */
    private ChartConfig line(String x, String y, String rawTitle, List<Map<String, Object>> rows) {
        if (x == null || y == null || x.equals(y)) {
            return null;
        }
        if (kindOf(y, rows) != Kind.NUMERIC) {
            return null;
        }
        Kind xKind = kindOf(x, rows);
        if (xKind != Kind.TEMPORAL && xKind != Kind.NUMERIC) {
            return null;
        }
        if (columnDistinct(x, rows) < 2) {
            return null;
        }
        return ChartConfig.line(x, y, title(rawTitle, titleOf(y, x)));
    }

    /** Pie: a part-to-whole split over a few named slices. */
    private ChartConfig pie(String name, String value, String rawTitle, List<Map<String, Object>> rows) {
        if (name == null || value == null || name.equals(value)) {
            return null;
        }
        if (kindOf(name, rows) != Kind.TEXT || kindOf(value, rows) != Kind.NUMERIC) {
            return null;
        }
        int slices = columnDistinct(name, rows);
        if (slices < 2 || slices > MAX_SLICES || slices != rows.size()) {
            return null;
        }
        for (Map<String, Object> row : rows) {
            Double number = toNumber(row == null ? null : row.get(value));
            if (number == null || number < 0) {
                // A negative part cannot be a share of a whole.
                return null;
            }
        }
        return ChartConfig.pie(name, value, title(rawTitle, titleOf(value, name)));
    }

    /** Scatter: two numeric columns, with a spread on the x-axis so a trend is visible. */
    private ChartConfig scatter(String x, String y, String rawTitle, List<Map<String, Object>> rows) {
        if (x == null || y == null || x.equals(y)) {
            return null;
        }
        if (kindOf(x, rows) != Kind.NUMERIC || kindOf(y, rows) != Kind.NUMERIC) {
            return null;
        }
        if (columnDistinct(x, rows) < 2) {
            return null;
        }
        return ChartConfig.scatter(x, y, title(rawTitle, titleOf(y, x)));
    }

    /**
     * Safety net for an explicit "show me a graph" style question: when the model suggested
     * nothing usable, the shape of the result decides. Every candidate goes through the very
     * same validation as the model's own suggestion, so this path can never produce a chart
     * the other path would have rejected.
     */
    private ChartConfig deriveFromExplicitRequest(String question, List<String> columns,
            List<Map<String, Object>> rows) {
        if (question == null || rows.size() < MIN_ROWS || columns.isEmpty()
                || !CHART_REQUEST.matcher(question).find()) {
            return null;
        }
        String numericA = firstColumn(columns, rows, Kind.NUMERIC, null);
        String numericB = firstColumn(columns, rows, Kind.NUMERIC, numericA);
        String temporal = firstColumn(columns, rows, Kind.TEMPORAL, null);
        List<ChartRequest> candidates = new ArrayList<>();

        if (SCATTER_REQUEST.matcher(question).find() && numericA != null && numericB != null) {
            candidates.add(new ChartRequest(ChartConfig.SCATTER, numericA, numericB, null, null, null));
        }
        if (numericA != null) {
            for (String category : categoricalColumns(columns, rows)) {
                if (PIE_REQUEST.matcher(question).find()) {
                    candidates.add(new ChartRequest(ChartConfig.PIE, null, null, category, numericA, null));
                }
                candidates.add(new ChartRequest(ChartConfig.BAR, category, numericA, null, null, null));
            }
            if (LINE_REQUEST.matcher(question).find() && temporal != null) {
                candidates.add(new ChartRequest(ChartConfig.LINE, temporal, numericA, null, null, null));
            }
            if (temporal != null) {
                candidates.add(new ChartRequest(ChartConfig.BAR, temporal, numericA, null, null, null));
            }
        }
        for (ChartRequest candidate : candidates) {
            ChartConfig chart = validate(candidate, columns, rows);
            if (chart != null) {
                log.debug("Derived a {} chart because the question explicitly asked for one", chart.type());
                return chart;
            }
        }
        log.debug("The question asks for a chart, but these columns cannot support one: {}", columns);
        return null;
    }

    /** Text columns that can act as a category axis (a few distinct labels, one per row). */
    private List<String> categoricalColumns(List<String> columns, List<Map<String, Object>> rows) {
        List<String> candidates = new ArrayList<>();
        for (String column : columns) {
            int distinct = columnDistinct(column, rows);
            if (kindOf(column, rows) == Kind.TEXT && distinct >= MIN_ROWS && distinct <= MAX_CATEGORIES
                    && distinct == rows.size()) {
                candidates.add(column);
            }
        }
        return candidates;
    }

    /** Case-insensitive column lookup that returns the real result label, or null if absent. */
    private String match(List<String> columns, String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return null;
        }
        String wanted = candidate.trim().replace("\"", "").replace("`", "");
        for (String column : columns) {
            if (column != null && column.equalsIgnoreCase(wanted)) {
                return column;
            }
        }
        return null;
    }

    /** Classifies a column from the values that were actually returned, not from its name. */
    private Kind kindOf(String column, List<Map<String, Object>> rows) {
        int numbers = 0;
        int temporal = 0;
        int text = 0;
        for (Map<String, Object> row : rows) {
            Object value = row == null ? null : row.get(column);
            if (value == null) {
                continue;
            }
            if (toNumber(value) != null) {
                numbers++;
            } else if (value instanceof TemporalAccessor || (value instanceof String dateText && isDateText(dateText))) {
                temporal++;
            } else {
                text++;
            }
        }
        int total = numbers + temporal + text;
        if (total == 0) {
            return Kind.EMPTY;
        }
        if (numbers == total) {
            return Kind.NUMERIC;
        }
        if (temporal == total) {
            return Kind.TEMPORAL;
        }
        if (text == total) {
            return Kind.TEXT;
        }
        return Kind.MIXED;
    }

    /** Distinct labels of a column; a null is not a category, a slice or a point. */
    private Set<String> distinctValues(String column, List<Map<String, Object>> rows) {
        Set<String> values = new LinkedHashSet<>();
        for (Map<String, Object> row : rows) {
            Object value = row == null ? null : row.get(column);
            if (value != null) {
                values.add(String.valueOf(value));
            }
        }
        return values;
    }

    private int columnDistinct(String column, List<Map<String, Object>> rows) {
        return distinctValues(column, rows).size();
    }

    /** First column of the given kind, optionally excluding one (e.g. the x-axis column). */
    private String firstColumn(List<String> columns, List<Map<String, Object>> rows, Kind kind, String exclude) {
        for (String column : columns) {
            if (!column.equals(exclude) && kindOf(column, rows) == kind) {
                return column;
            }
        }
        return null;
    }

    /** Numeric value of a returned cell - only the real result values are ever plotted. */
    private Double toNumber(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text) {
            String trimmed = text.trim();
            if (!trimmed.isEmpty() && !CLOCK_TIME.matcher(trimmed).matches()) {
                try {
                    new BigDecimal(trimmed);
                    return Double.valueOf(trimmed);
                } catch (NumberFormatException ex) {
                    return null;
                }
            }
        }
        return null;
    }

    /** True for date/time text such as "2024-05-01" or "2024-05-01 10:15:00.123". */
    private boolean isDateText(String text) {
        String trimmed = text.trim();
        return ISO_DATE.matcher(trimmed).matches() || CLOCK_TIME.matcher(trimmed).matches();
    }

    /** The model's title when it is usable, otherwise one built from the plotted columns. */
    private String title(String rawTitle, String fallback) {
        if (rawTitle != null) {
            String cleaned = rawTitle.replaceAll("[`\"'*#|]", " ").replaceAll("\\s+", " ").trim();
            cleaned = cleaned.replaceAll("[.\\s]+$", "");
            if (!cleaned.isEmpty() && cleaned.length() <= MAX_TITLE_LENGTH) {
                return cleaned;
            }
        }
        return fallback;
    }

    /** e.g. "Student count by university" - built from column names, never from row values. */
    private String titleOf(String value, String category) {
        String text = humanize(value) + " by " + humanize(category);
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private String humanize(String column) {
        return column.replace('_', ' ').toLowerCase(Locale.ROOT);
    }
}

