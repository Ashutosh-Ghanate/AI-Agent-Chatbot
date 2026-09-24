package com.studentloan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

import com.studentloan.dto.AiQueryResponse;
import com.studentloan.dto.ChartConfig;
import com.studentloan.dto.DatabaseSchema;
import com.studentloan.dto.DatabaseSchema.Column;
import com.studentloan.dto.DatabaseSchema.Table;

/**
 * Guards the two things that must stay dynamic: the LLM prompt is built from the schema
 * discovered at runtime, and the SQL guardrail allows exactly those tables - nothing is
 * hardcoded, while a table the snapshot does not contain is still refused.
 *
 * The LLM and the database are both stubbed, so neither Ollama nor PostgreSQL is needed.
 */
class AiQueryServiceGuardrailTest {

    /** Stands in for whatever the live database happens to contain. */
    private static final DatabaseSchema SCHEMA = new DatabaseSchema(
            "public",
            List.of(
                    new Table("student_table", "TABLE", List.of(
                            new Column("id", "integer", false, Column.PRIMARY_KEY),
                            new Column("university", "varchar(150)", false, ""),
                            new Column("loan_amount", "numeric(12,2)", false, ""),
                            new Column("applied_on", "date", false, ""),
                            new Column("scholarship_amount", "numeric(12,2)", true, ""))),
                    new Table("test_table", "TABLE", List.of(
                            new Column("id", "integer", false, Column.PRIMARY_KEY),
                            new Column("item_name", "varchar(50)", false, "")))),
            List.of());

    private static final String UNANSWERABLE = "I can only answer questions about the student loan data";
    private static final String NO_RECORDS = "I could not find any matching student loan records";

    private ChatClient.ChatClientRequestSpec requestSpec;
    private ChatClient.CallResponseSpec callResponseSpec;
    private JdbcTemplate jdbcTemplate;
    private AiQueryService service;

    @BeforeEach
    void setUp() {
        ChatClient chatClient = mock(ChatClient.class);
        requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        callResponseSpec = mock(ChatClient.CallResponseSpec.class);

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);

        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        when(builder.build()).thenReturn(chatClient);

        DatabaseSchemaService schemaService = mock(DatabaseSchemaService.class);
        when(schemaService.discoverSchema()).thenReturn(SCHEMA);
        when(schemaService.describeForPrompt(any(DatabaseSchema.class))).thenCallRealMethod();

        // The JdbcTemplate is left unstubbed by default: query(...) returns null, which the
        // service treats as "the statement ran but matched no rows".
        jdbcTemplate = mock(JdbcTemplate.class);
        service = new AiQueryService(builder, jdbcTemplate, schemaService, new ChartConfigService());
    }

    /** Feeds the service a canned LLM reply and returns the chatbot answer. */
    private String answerFor(String llmReply) {
        return responseFor("test question", llmReply).answer();
    }

    private AiQueryResponse responseFor(String question, String llmReply) {
        when(callResponseSpec.content()).thenReturn(llmReply);
        return service.runQuery(question);
    }

    /**
     * Makes the stubbed JdbcTemplate return real rows, so the service's own ResultSetExtractor
     * (and therefore ChartConfigService) sees the columns and values a live query would give it.
     */
    private void stubRows(String[] labels, Object[][] values) throws SQLException {
        ResultSet resultSet = mock(ResultSet.class);
        ResultSetMetaData metaData = mock(ResultSetMetaData.class);

        when(resultSet.getMetaData()).thenReturn(metaData);
        when(metaData.getColumnCount()).thenReturn(labels.length);
        for (int index = 0; index < labels.length; index++) {
            when(metaData.getColumnLabel(index + 1)).thenReturn(labels[index]);
        }

        // next() walks the canned rows and reports false at the end, exactly like a cursor.
        int[] cursor = { -1 };
        when(resultSet.next()).thenAnswer(invocation -> {
            cursor[0]++;
            return cursor[0] < values.length;
        });
        when(resultSet.getObject(anyInt())).thenAnswer(invocation ->
                values[cursor[0]][(Integer) invocation.getArgument(0) - 1]);

        when(jdbcTemplate.query(anyString(), any(ResultSetExtractor.class)))
                .thenAnswer(invocation -> ((ResultSetExtractor<?>) invocation.getArgument(1))
                        .extractData(resultSet));
    }

    @Test
    @DisplayName("prompt describes the discovered schema, drops missing columns and keeps no placeholders")
    void promptIsBuiltFromTheDiscoveredSchema() {
        answerFor("SQL: SELECT item_name FROM test_table\nANSWER: ok");

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(requestSpec).system(prompt.capture());

        assertThat(prompt.getValue())
                // a table nobody mentioned in Java is offered to the model ...
                .contains("Table: test_table")
                .contains("item_name")
                // ... and a column added a moment ago too
                .contains("scholarship_amount")
                // ... the aggregation example follows the live schema
                .contains("SELECT university, COUNT(*) AS student_count FROM student_table")
                // ... the semantic wording is kept, but only for columns that exist
                .contains("\"loan money\"")
                .doesNotContain("-> emi")
                // ... and every placeholder was substituted
                .doesNotContain("{{");
    }

    @Test
    @DisplayName("a table that was just discovered is queryable without any code change")
    void allowsQueryOnDiscoveredTable() {
        assertThat(answerFor("SQL: SELECT COUNT(*) FROM test_table\nANSWER: counting")).startsWith(NO_RECORDS);
    }

    @Test
    @DisplayName("a discovered table may still be schema-qualified with the allowlisted schema")
    void allowsQueryQualifiedWithAllowlistedSchema() {
        assertThat(answerFor("SQL: SELECT item_name FROM public.test_table\nANSWER: items"))
                .startsWith(NO_RECORDS);
    }

    @Test
    @DisplayName("a table that is not in the discovered schema is rejected, not executed")
    void rejectsTableThatIsNotDiscovered() {
        assertThat(answerFor("SQL: SELECT COUNT(*) FROM orders\nANSWER: counting")).startsWith(UNANSWERABLE);
    }

    @Test
    @DisplayName("a real table in a non-allowlisted schema is rejected")
    void rejectsTableFromAnotherSchema() {
        assertThat(answerFor("SQL: SELECT COUNT(*) FROM schema_test.hidden_table\nANSWER: counting"))
                .startsWith(UNANSWERABLE);
    }

    @Test
    @DisplayName("a query with no discovered table at all is rejected")
    void rejectsQueryWithoutAnyTable() {
        assertThat(answerFor("SQL: SELECT 1\nANSWER: one")).startsWith(UNANSWERABLE);
    }

    @Test
    @DisplayName("write statements stay forbidden")
    void rejectsWriteStatements() {
        assertThat(answerFor("SQL: DELETE FROM student_table\nANSWER: deleted")).startsWith(UNANSWERABLE);
    }

    @Test
    @DisplayName("stacked statements stay forbidden")
    void rejectsMultipleStatements() {
        assertThat(answerFor("SQL: SELECT id FROM student_table; DELETE FROM student_table\nANSWER: x"))
                .startsWith(UNANSWERABLE);
    }

    @Test
    @DisplayName("function syntax such as EXTRACT(... FROM applied_on) is not mistaken for a table")
    void allowsFunctionSyntaxInsideSelect() {
        assertThat(answerFor("SQL: SELECT EXTRACT(YEAR FROM applied_on) AS yr FROM student_table\nANSWER: years"))
                .startsWith(NO_RECORDS);
    }

    @Test
    @DisplayName("the prompt asks for a chart line and lists the chartable columns of the live schema")
    void promptExplainsChartsFromTheDiscoveredSchema() {
        answerFor("SQL: SELECT item_name FROM test_table\nANSWER: ok");

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(requestSpec).system(prompt.capture());

        assertThat(prompt.getValue())
                .contains("CHART:")
                .contains("CHART: none")
                // the hints follow the discovered schema, including a column added later on
                .contains("numeric (usable as yColumn/valueColumn): id, loan_amount, scholarship_amount")
                .contains("date/time (usable as a line or bar xColumn): applied_on")
                .doesNotContain("{{");
    }

    @Test
    @DisplayName("a chart the model asks for is returned when it fits the returned columns")
    void returnsTheChartTheModelAskedFor() throws SQLException {
        stubRows(new String[] { "university", "student_count" },
                new Object[][] { { "IIT Bombay", 3L }, { "IIM Bangalore", 2L } });

        AiQueryResponse response = responseFor("How many students are there for each university?", """
                SQL: SELECT university, COUNT(*) AS student_count FROM student_table GROUP BY university
                ANSWER: Here are the student counts per university:
                CHART: {"type":"bar","xColumn":"university","yColumn":"student_count","title":"Students by university"}""");

        assertThat(response.chart()).isNotNull();
        assertThat(response.chart().type()).isEqualTo(ChartConfig.BAR);
        assertThat(response.chart().xColumn()).isEqualTo("university");
        assertThat(response.chart().yColumn()).isEqualTo("student_count");
        assertThat(response.chart().title()).isEqualTo("Students by university");
        // the table is untouched by the chart
        assertThat(response.columns()).containsExactly("university", "student_count");
        assertThat(response.rows()).hasSize(2);
    }

    @Test
    @DisplayName("a chart naming a column the query did not return is dropped, the table is kept")
    void dropsChartWithAColumnThatWasNotReturned() throws SQLException {
        stubRows(new String[] { "university", "student_count" },
                new Object[][] { { "IIT Bombay", 3L }, { "IIM Bangalore", 2L } });

        AiQueryResponse response = responseFor("test question", """
                SQL: SELECT university, COUNT(*) AS student_count FROM student_table GROUP BY university
                ANSWER: Here are the student counts:
                CHART: {"type":"bar","xColumn":"population","yColumn":"student_count"}""");

        assertThat(response.chart()).isNull();
        assertThat(response.rows()).hasSize(2);
    }

    @Test
    @DisplayName("a chart plotted against text values is dropped instead of drawn")
    void dropsChartWhosePlottedValuesAreText() throws SQLException {
        stubRows(new String[] { "university", "student_count" },
                new Object[][] { { "IIT Bombay", 3L }, { "IIM Bangalore", 2L } });

        AiQueryResponse response = responseFor("test question", """
                SQL: SELECT university, COUNT(*) AS student_count FROM student_table GROUP BY university
                ANSWER: Here are the student counts:
                CHART: {"type":"bar","xColumn":"student_count","yColumn":"university"}""");

        assertThat(response.chart()).isNull();
    }

    @Test
    @DisplayName("row-level data is never drawn as an aggregate chart")
    void doesNotChartRowLevelData() throws SQLException {
        stubRows(new String[] { "university", "loan_amount" }, new Object[][] {
                { "IIT Bombay", 100 }, { "IIT Bombay", 200 }, { "IIM Bangalore", 300 } });

        AiQueryResponse response = responseFor("test question", """
                SQL: SELECT university, loan_amount FROM student_table
                ANSWER: Here are the students:
                CHART: {"type":"bar","xColumn":"university","yColumn":"loan_amount"}""");

        // the same category appears on several rows, so this is a list of records
        assertThat(response.chart()).isNull();
        assertThat(response.rows()).hasSize(3);
    }

    @Test
    @DisplayName("an explicit request for a graph still gets a chart when the model forgot one")
    void derivesAChartWhenTheUserExplicitlyAskedForOne() throws SQLException {
        stubRows(new String[] { "university", "student_count" },
                new Object[][] { { "IIT Bombay", 1L }, { "IIM Bangalore", 1L } });

        AiQueryResponse response = responseFor("Show me a graph of students by university", """
                SQL: SELECT university, COUNT(*) AS student_count FROM student_table GROUP BY university
                ANSWER: Here are the student counts per university:""");

        assertThat(response.chart()).isNotNull();
        assertThat(response.chart().type()).isEqualTo(ChartConfig.BAR);
        assertThat(response.chart().xColumn()).isEqualTo("university");
        assertThat(response.chart().yColumn()).isEqualTo("student_count");
        // no title from the model, so it is built from the column names
        assertThat(response.chart().title()).isEqualTo("Student count by university");
    }

    @Test
    @DisplayName("a trend question gets a line chart when the result is date based")
    void derivesALineChartForATrendRequest() throws SQLException {
        stubRows(new String[] { "applied_on", "applications" }, new Object[][] {
                { "2024-01-05", 2L }, { "2024-02-11", 5L }, { "2024-03-02", 3L } });

        AiQueryResponse response = responseFor("Show the trend of loan applications over time", """
                SQL: SELECT applied_on, COUNT(*) AS applications FROM student_table GROUP BY applied_on ORDER BY applied_on
                ANSWER: Here is how applications developed over time:""");

        assertThat(response.chart()).isNotNull();
        assertThat(response.chart().type()).isEqualTo(ChartConfig.LINE);
        assertThat(response.chart().xColumn()).isEqualTo("applied_on");
        assertThat(response.chart().yColumn()).isEqualTo("applications");
    }

    @Test
    @DisplayName("an empty result is never charted")
    void neverChartsAnEmptyResult() {
        AiQueryResponse response = responseFor("Show me a graph of students by university", """
                SQL: SELECT 1 AS no_results FROM student_table WHERE false
                ANSWER: nothing to show
                CHART: {"type":"bar","xColumn":"university","yColumn":"student_count"}""");

        assertThat(response.chart()).isNull();
        assertThat(response.columns()).isEmpty();
    }
}
