package com.studentloan.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.studentloan.dto.ChartConfig;

/**
 * The chart the API returns must always be drawable from the rows it returns, whatever the
 * model suggests. These tests drive the service directly (no Spring, no database) and cover
 * both the suggestions that are accepted and the ones that are dropped - including invented
 * columns, text values where numbers are required, and row-level data.
 */
class ChartConfigServiceTest {

    private final ChartConfigService service = new ChartConfigService();

    /** Builds the rows a query would return from parallel label/value arrays. */
    private static List<Map<String, Object>> table(String[] labels, Object[][] values) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Object[] value : values) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int index = 0; index < labels.length; index++) {
                row.put(labels[index], value[index]);
            }
            rows.add(row);
        }
        return rows;
    }

    private static String[] universities() {
        return new String[] { "university", "student_count" };
    }

    private static Object[][] twoUniversities() {
        return new Object[][] { { "IIT Bombay", 3L }, { "IIM Bangalore", 2L } };
    }

    private ChartConfig resolve(String question, String spec, String[] labels, Object[][] values) {
        return service.resolve(question, spec, List.of(labels), table(labels, values));
    }

    @Test
    @DisplayName("a bar chart that fits the returned columns is accepted, title and all")
    void acceptsABarChartForAnAggregatedCategory() {
        ChartConfig chart = resolve("How many students are there for each university?",
                "{\"type\":\"bar\",\"xColumn\":\"university\",\"yColumn\":\"student_count\","
                        + "\"title\":\"Students by university\"}",
                universities(), twoUniversities());

        assertThat(chart).isNotNull();
        assertThat(chart.type()).isEqualTo(ChartConfig.BAR);
        assertThat(chart.xColumn()).isEqualTo("university");
        assertThat(chart.yColumn()).isEqualTo("student_count");
        assertThat(chart.title()).isEqualTo("Students by university");
        // no data of its own: the chart only names columns of the result
        assertThat(chart.nameColumn()).isNull();
        assertThat(chart.valueColumn()).isNull();
    }

    @Test
    @DisplayName("a key=value suggestion is understood too, with a title built from the columns")
    void acceptsTheKeyValueForm() {
        ChartConfig chart = resolve("How many students are there for each university?",
                "type=bar; xColumn=university; yColumn=student_count",
                universities(), twoUniversities());

        assertThat(chart).isNotNull();
        assertThat(chart.type()).isEqualTo(ChartConfig.BAR);
        assertThat(chart.title()).isEqualTo("Student count by university");
    }

    @Test
    @DisplayName("column names are matched case-insensitively onto the real result labels")
    void matchesColumnNamesCaseInsensitively() {
        ChartConfig chart = resolve("test question",
                "{\"type\":\"bar\",\"xColumn\":\"UNIVERSITY\",\"yColumn\":\"Student_Count\"}",
                universities(), twoUniversities());

        assertThat(chart).isNotNull();
        assertThat(chart.xColumn()).isEqualTo("university");
        assertThat(chart.yColumn()).isEqualTo("student_count");
    }

    @Test
    @DisplayName("a column the query did not return is refused")
    void refusesAColumnThatIsNotInTheResult() {
        assertThat(resolve("test question",
                "{\"type\":\"bar\",\"xColumn\":\"population\",\"yColumn\":\"student_count\"}",
                universities(), twoUniversities())).isNull();
    }

    @Test
    @DisplayName("a bar chart plotted against text values is refused")
    void refusesABarChartWhoseValuesAreText() {
        assertThat(resolve("test question",
                "{\"type\":\"bar\",\"xColumn\":\"student_count\",\"yColumn\":\"university\"}",
                universities(), twoUniversities())).isNull();
    }

    @Test
    @DisplayName("a line chart needs a date/time or ordered numeric axis, not categories")
    void refusesALineChartOnACategoricalAxis() {
        // no chart wording in the question, so an invalid suggestion is simply dropped
        assertThat(resolve("Which university has the most students?",
                "{\"type\":\"line\",\"xColumn\":\"university\",\"yColumn\":\"student_count\"}",
                universities(), twoUniversities())).isNull();
    }

    @Test
    @DisplayName("a line chart over a date column is accepted")
    void acceptsALineChartOnADateAxis() {
        String[] labels = { "applied_on", "applications" };
        Object[][] values = { { "2024-01-05", 2L }, { "2024-02-11", 5L }, { "2024-03-02", 3L } };

        ChartConfig chart = resolve("Show applications over time",
                "{\"type\":\"line\",\"xColumn\":\"applied_on\",\"yColumn\":\"applications\"}", labels, values);

        assertThat(chart).isNotNull();
        assertThat(chart.type()).isEqualTo(ChartConfig.LINE);
        assertThat(chart.xColumn()).isEqualTo("applied_on");
        assertThat(chart.title()).isEqualTo("Applications by applied on");
    }

    @Test
    @DisplayName("a pie chart that fits the returned columns is accepted")
    void acceptsAPieChartOverAFewCategories() {
        String[] labels = { "status", "loan_count" };
        Object[][] values = { { "APPROVED", 6L }, { "PENDING", 3L }, { "REJECTED", 1L } };

        ChartConfig chart = resolve("Approved vs rejected loans",
                "{\"type\":\"pie\",\"nameColumn\":\"status\",\"valueColumn\":\"loan_count\","
                        + "\"title\":\"Loans by status\"}",
                labels, values);

        assertThat(chart).isNotNull();
        assertThat(chart.type()).isEqualTo(ChartConfig.PIE);
        assertThat(chart.nameColumn()).isEqualTo("status");
        assertThat(chart.valueColumn()).isEqualTo("loan_count");
        assertThat(chart.xColumn()).isNull();
        assertThat(chart.yColumn()).isNull();
    }

    @Test
    @DisplayName("a pie described with xColumn/yColumn wording is understood")
    void acceptsAPieDescribedWithXyWording() {
        String[] labels = { "status", "loan_count" };
        Object[][] values = { { "APPROVED", 6L }, { "PENDING", 3L } };

        ChartConfig chart = resolve("Approved vs rejected loans",
                "{\"type\":\"pie\",\"xColumn\":\"status\",\"yColumn\":\"loan_count\"}", labels, values);

        assertThat(chart).isNotNull();
        assertThat(chart.nameColumn()).isEqualTo("status");
        assertThat(chart.valueColumn()).isEqualTo("loan_count");
    }

    @Test
    @DisplayName("a pie chart with too many slices is refused")
    void refusesAPieChartWithTooManySlices() {
        String[] labels = { "applicant_name", "loan_amount" };
        Object[][] values = new Object[15][];
        for (int index = 0; index < values.length; index++) {
            values[index] = new Object[] { "Student " + index, 100 + index };
        }

        assertThat(resolve("Which students took the largest loans?", "{\"type\":\"pie\",\"nameColumn\":\"applicant_name\","
                + "\"valueColumn\":\"loan_amount\"}", labels, values)).isNull();
    }

    @Test
    @DisplayName("an unsupported chart type is refused, a donut is drawn as a pie")
    void handlesUnsupportedTypes() {
        assertThat(resolve("test question", "{\"type\":\"radar\",\"xColumn\":\"university\","
                + "\"yColumn\":\"student_count\"}", universities(), twoUniversities())).isNull();

        ChartConfig chart = resolve("test question",
                "{\"type\":\"donut\",\"xColumn\":\"university\",\"yColumn\":\"student_count\"}",
                universities(), twoUniversities());
        assertThat(chart).isNotNull();
        assertThat(chart.type()).isEqualTo(ChartConfig.PIE);
    }

    @Test
    @DisplayName("a single row is never charted")
    void refusesAChartForASingleRow() {
        String[] labels = { "university", "student_count" };
        Object[][] values = { { "IIT Bombay", 3L } };

        assertThat(resolve("How many students are there per university?",
                "{\"type\":\"bar\",\"xColumn\":\"university\",\"yColumn\":\"student_count\"}",
                labels, values)).isNull();
    }

    @Test
    @DisplayName("a bar chart with more categories than a chart can show is refused")
    void refusesABarChartWithTooManyCategories() {
        String[] labels = { "course", "student_count" };
        Object[][] values = new Object[30][];
        for (int index = 0; index < values.length; index++) {
            values[index] = new Object[] { "Course " + index, index + 1L };
        }

        assertThat(resolve("How many students are there per course?",
                "{\"type\":\"bar\",\"xColumn\":\"course\",\"yColumn\":\"student_count\"}",
                labels, values)).isNull();
    }

    @Test
    @DisplayName("row-level rows are not charted, not even for an explicit graph request")
    void refusesRowLevelDataEvenWhenAGraphWasRequested() {
        String[] labels = { "university", "loan_amount" };
        Object[][] values = { { "IIT Bombay", 100 }, { "IIT Bombay", 200 }, { "IIM Bangalore", 300 } };

        assertThat(resolve("Show me a graph of students by university",
                "{\"type\":\"bar\",\"xColumn\":\"university\",\"yColumn\":\"loan_amount\"}",
                labels, values)).isNull();
    }

    @Test
    @DisplayName("an explicit graph request is satisfied even without a suggestion from the model")
    void derivesABarChartForAnExplicitGraphRequest() {
        ChartConfig chart = resolve("Show me a graph of students by university", "none",
                universities(), twoUniversities());

        assertThat(chart).isNotNull();
        assertThat(chart.type()).isEqualTo(ChartConfig.BAR);
        assertThat(chart.xColumn()).isEqualTo("university");
        assertThat(chart.yColumn()).isEqualTo("student_count");
    }

    @Test
    @DisplayName("a distribution request becomes a pie chart")
    void derivesAPieChartForADistributionRequest() {
        String[] labels = { "status", "loan_count" };
        Object[][] values = { { "APPROVED", 6L }, { "PENDING", 3L }, { "REJECTED", 1L } };

        ChartConfig chart = resolve("Show the distribution of loans by status", "none", labels, values);

        assertThat(chart).isNotNull();
        assertThat(chart.type()).isEqualTo(ChartConfig.PIE);
        assertThat(chart.nameColumn()).isEqualTo("status");
        assertThat(chart.valueColumn()).isEqualTo("loan_count");
    }

    @Test
    @DisplayName("a chart of one number against another becomes a scatter chart")
    void derivesAScatterChartForTwoNumericColumns() {
        String[] labels = { "loan_amount", "credit_score" };
        Object[][] values = { { 200000, 720 }, { 500000, 660 }, { 800000, 790 } };

        ChartConfig chart = resolve("Show a chart of loan amount vs credit score", "none", labels, values);

        assertThat(chart).isNotNull();
        assertThat(chart.type()).isEqualTo(ChartConfig.SCATTER);
        assertThat(chart.xColumn()).isEqualTo("loan_amount");
        assertThat(chart.yColumn()).isEqualTo("credit_score");
    }

    @Test
    @DisplayName("no chart is returned when there is no suggestion and no visual request")
    void returnsNoChartByDefault() {
        assertThat(resolve("List all students", "none", universities(), twoUniversities())).isNull();
        assertThat(resolve("List all students", null, universities(), twoUniversities())).isNull();
    }

    @Test
    @DisplayName("an unusable title from the model is replaced by one built from the columns")
    void replacesAnUnusableTitle() {
        String longTitle = "Students".repeat(30);

        ChartConfig chart = resolve("test question",
                "{\"type\":\"bar\",\"xColumn\":\"university\",\"yColumn\":\"student_count\",\"title\":\"" + longTitle + "\"}",
                universities(), twoUniversities());

        assertThat(chart).isNotNull();
        assertThat(chart.title()).isEqualTo("Student count by university");
    }
}
