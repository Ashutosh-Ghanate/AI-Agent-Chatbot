package com.studentloan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

import com.studentloan.dto.AiQueryResponse;
import com.studentloan.dto.DatabaseSchema;
import com.studentloan.dto.DatabaseSchema.Column;
import com.studentloan.dto.DatabaseSchema.Table;

/**
 * Guards case-insensitive handling of categorical/string filter values.
 */
class AiQueryServiceCaseInsensitiveTest {

    private static final DatabaseSchema SCHEMA = new DatabaseSchema(
            "public",
            List.of(
                    new Table("student_table", "TABLE", List.of(
                            new Column("id", "integer", false, Column.PRIMARY_KEY),
                            new Column("applicant_name", "varchar(150)", false, ""),
                            new Column("university", "varchar(150)", false, ""),
                            new Column("course", "varchar(100)", true, ""),
                            new Column("emi", "numeric(12,2)", false, ""),
                            new Column("status", "varchar(20)", false, "")))),
            List.of());

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

        jdbcTemplate = mock(JdbcTemplate.class);
        service = new AiQueryService(builder, jdbcTemplate, schemaService, new ChartConfigService());
    }

    private AiQueryResponse responseFor(String question, String llmReply) {
        when(callResponseSpec.content()).thenReturn(llmReply);
        return service.runQuery(question);
    }

    private String executedSql(String llmReply) {
        responseFor("test question", llmReply);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, atLeastOnce()).query(sql.capture(), any(ResultSetExtractor.class));
        List<String> all = sql.getAllValues();
        return all.get(all.size() - 1);
    }

    @Test
    @DisplayName("prompt tells the model that string filters are case-insensitive")
    void promptRequiresCaseInsensitiveStringFilters() {
        responseFor("test", "SQL: SELECT id FROM student_table\nANSWER: ok");

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(requestSpec).system(prompt.capture());

        assertThat(prompt.getValue())
                .contains("case-insensitive")
                .contains("LOWER(")
                .contains("ILIKE");
    }

    @Test
    @DisplayName("status pending, PENDING and Pending generate equivalent SQL")
    void statusCasingVariantsAreEquivalent() {
        String lower = executedSql(
                "SQL: SELECT id FROM student_table WHERE status = 'pending'\nANSWER: ok");
        String upper = executedSql(
                "SQL: SELECT id FROM student_table WHERE status = 'PENDING'\nANSWER: ok");
        String mixed = executedSql(
                "SQL: SELECT id FROM student_table WHERE status = 'Pending'\nANSWER: ok");
        String weird = executedSql(
                "SQL: SELECT id FROM student_table WHERE status = 'PeNdInG'\nANSWER: ok");

        for (String sql : List.of(lower, upper, mixed, weird)) {
            assertThat(sql).contains("LOWER(status) = LOWER(");
        }

        assertThat(lower.toLowerCase()).isEqualTo(upper.toLowerCase());
        assertThat(lower.toLowerCase()).isEqualTo(mixed.toLowerCase());
        assertThat(lower.toLowerCase()).isEqualTo(weird.toLowerCase());
    }

    @Test
    @DisplayName("emi less than 10000 with pending or PENDING yields the same comparison")
    void emiAndStatusCombinationIsCaseInsensitive() {
        String lower = executedSql(
                "SQL: SELECT id FROM student_table WHERE emi < 10000 AND status = 'pending'\nANSWER: ok");
        String upper = executedSql(
                "SQL: SELECT id FROM student_table WHERE emi < 10000 AND status = 'PENDING'\nANSWER: ok");

        assertThat(lower).contains("emi < 10000");
        assertThat(upper).contains("emi < 10000");
        assertThat(lower.toLowerCase()).isEqualTo(upper.toLowerCase());
    }

    @Test
    @DisplayName("university casing differences generate equivalent SQL")
    void universityCasingVariantsAreEquivalent() {
        String lower = executedSql(
                "SQL: SELECT id FROM student_table WHERE university = 'iit bombay'\nANSWER: ok");
        String upper = executedSql(
                "SQL: SELECT id FROM student_table WHERE university = 'IIT Bombay'\nANSWER: ok");

        assertThat(lower).contains("LOWER(university) = LOWER(");
        assertThat(lower.toLowerCase()).isEqualTo(upper.toLowerCase());
    }

    @Test
    @DisplayName("numeric comparisons are never rewritten")
    void numericFiltersAreUntouched() {
        String sql = executedSql(
                "SQL: SELECT id FROM student_table WHERE emi < 10000\nANSWER: ok");

        assertThat(sql).isEqualTo("SELECT id FROM student_table WHERE emi < 10000");
    }

    @Test
    @DisplayName("already case-insensitive SQL is left alone")
    void ilikeAndLowerAreNotDoubleWrapped() {
        String ilike = executedSql(
                "SQL: SELECT id FROM student_table WHERE status ILIKE 'pending'\nANSWER: ok");
        assertThat(ilike).isEqualTo("SELECT id FROM student_table WHERE status ILIKE 'pending'");

        String lowered = executedSql(
                "SQL: SELECT id FROM student_table WHERE LOWER(status) = LOWER('pending')\nANSWER: ok");
        assertThat(lowered)
                .isEqualTo("SELECT id FROM student_table WHERE LOWER(status) = LOWER('pending')");
    }
}
