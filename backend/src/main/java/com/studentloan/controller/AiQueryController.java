package com.studentloan.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.studentloan.dto.AiQueryRequest;
import com.studentloan.dto.AiQueryResponse;
import com.studentloan.dto.ConversationMessage;
import com.studentloan.service.AiQueryService;

import java.util.List;

/**
 * Temporary AI endpoint: natural-language question -> LLM-generated SQL -> rows.
 */
@RestController
@RequestMapping("/api/ai")
public class AiQueryController {

    private final AiQueryService aiQueryService;

    public AiQueryController(AiQueryService aiQueryService) {
        this.aiQueryService = aiQueryService;
    }

    @PostMapping("/query")
    public AiQueryResponse query(@RequestBody AiQueryRequest request) {
        if (request.question() == null || request.question().isBlank()) {
            throw new IllegalArgumentException("'question' must not be blank.");
        }
        List<ConversationMessage> conversation = request.conversation() == null
                ? List.of()
                : request.conversation();
        return aiQueryService.runQuery(request.question().trim(), conversation);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleServerError(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()));
    }
}