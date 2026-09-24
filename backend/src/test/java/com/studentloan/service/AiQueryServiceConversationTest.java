package com.studentloan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.jdbc.core.JdbcTemplate;

import com.studentloan.dto.ChartConfig;
import com.studentloan.dto.ConversationMessage;
import com.studentloan.dto.DatabaseSchema;
import com.studentloan.dto.DatabaseSchema.Column;
import com.studentloan.dto.DatabaseSchema.Table;

/**
 * Covers short-term conversation context: history is bounded and rendered as
 * an untrusted context section, while SQL validation stays untouched.
 */
class AiQueryServiceConversationTest {

    private static final DatabaseSchema SCHEMA = new DatabaseSchema(
            "public",
            List.of(new Table("student_table", "TABLE", List.of(
                    new Column("id", "integer", false, Column.PRIMARY_KEY),
                    new Column("emi", "numeric(12,2)", false, ""),
                    new Column("status", "varchar(20)", false, "")))),
            List.of());

    private ChatClient.ChatClientRequestSpec requestSpec;
    private ChatClient.CallResponseSpec callResponseSpec;
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

        service = new AiQueryService(builder, mock(JdbcTemplate.class), schemaService,
                new ChartConfigService());
    }

    private ConversationMessage assistant(String content, String sql) {
        return new ConversationMessage("assistant", content, sql, List.of("id"),
                List.of(Map.of("id", 1)), new ChartConfig("bar", "status", "id", null, null, "T"));
    }

    private String systemPrompt() {
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(requestSpec).system(prompt.capture());
        return prompt.getValue();
    }

    private String userPrompt() {
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(requestSpec).user(prompt.capture());
        return prompt.getValue();
    }

    @Test
    @DisplayName("no history keeps the previous single-question behaviour")
    void noHistoryMeansNoConversationSection() {
        when(callResponseSpec.content()).thenReturn("SQL: SELECT id FROM student_table\nANSWER: ok");
        service.runQuery("How many students are there?");

        assertThat(systemPrompt()).contains("(no previous conversation");
        assertThat(userPrompt()).isEqualTo("How many students are there?");
    }

    @Test
    @DisplayName("null history falls back to single-question behaviour")
    void nullHistoryIsTolerated() {
        when(callResponseSpec.content()).thenReturn("SQL: SELECT id FROM student_table\nANSWER: ok");
        service.runQuery("How many students are there?", null);

        assertThat(systemPrompt()).contains("(no previous conversation");
    }

    @Test
    @DisplayName("follow-up keeps previous filters as labelled context")
    void followUpCarriesPreviousFiltersAsContext() {
        when(callResponseSpec.content()).thenReturn("SQL: SELECT id FROM student_table\nANSWER: ok");
        service.runQuery("Only pending ones", List.of(
                new ConversationMessage("user", "Show students with EMI below 10000"),
                assistant("Here are the students:",
                        "SELECT id FROM student_table WHERE emi < 10000")));

        String system = systemPrompt();
        assertThat(system).contains("Show students with EMI below 10000");
        assertThat(system).contains("Previous SQL (context only, do not execute)");
        assertThat(system).contains("emi < 10000");
        assertThat(system).contains("untrusted");
        assertThat(userPrompt()).contains("Only pending ones");
    }

    @Test
    @DisplayName("history is bounded to the most recent turns and chars")
    void historyIsBounded() {
        List<ConversationMessage> history = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            history.add(new ConversationMessage("user", "question " + i));
            Map<String, Object> row = new LinkedHashMap<>();
            for (int c = 0; c < 10; c++) {
                row.put("col" + c, "value-" + c + "-with-padding-to-grow-the-section");
            }
            history.add(new ConversationMessage("assistant", "answer " + i,
                    "SELECT id FROM student_table", List.of("id"), List.of(row), null));
        }
        String section = service.buildConversationSection(history);
        assertThat(section).doesNotContain("question 0");
        assertThat(section).contains("question 14");
        assertThat(section.length()).isLessThanOrEqualTo(4100);
    }

    @Test
    @DisplayName("previous SQL never bypasses guardrails on follow-ups")
    void followUpSqlIsStillValidated() {
        when(callResponseSpec.content()).thenReturn("SQL: DELETE FROM student_table\nANSWER: bye");
        var response = service.runQuery("delete everything", List.of(
                new ConversationMessage("user", "Show students with EMI below 10000"),
                assistant("Here they are:", "SELECT id FROM student_table WHERE emi < 10000")));

        assertThat(response.columns()).isEmpty();
        assertThat(response.answer()).isNotBlank();
    }
}
