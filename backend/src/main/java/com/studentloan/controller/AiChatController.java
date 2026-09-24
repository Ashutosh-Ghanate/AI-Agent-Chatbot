package com.studentloan.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.studentloan.dto.AiChatRequest;
import com.studentloan.dto.AiQueryResponse;
import com.studentloan.dto.ConversationMessage;
import com.studentloan.service.AiQueryService;

import java.util.List;
import java.util.Map;

/**
 * Chatbot endpoint used by the React UI.
 *
 * The LLM turn is fully internal: the user's question is turned into SQL, the SQL is
 * validated and executed against PostgreSQL, and the client receives a natural-language
 * answer plus the structured columns/rows table. The generated SQL is only returned as
 * a debug field and is never displayed as the chatbot answer.
 *
 * POST /api/ai/chat  { "message": "fetch student names" }
 */
@RestController
@RequestMapping("/api/ai")
public class AiChatController {

    private final AiQueryService aiQueryService;

    public AiChatController(AiQueryService aiQueryService) {
        this.aiQueryService = aiQueryService;
    }

    @PostMapping("/chat")
    public AiQueryResponse chat(@RequestBody AiChatRequest request) {
        if (request.message() == null || request.message().isBlank()) {
            throw new IllegalArgumentException("'message' must not be blank.");
        }
        List<ConversationMessage> conversation = request.conversation() == null
                ? List.of()
                : request.conversation();
        return aiQueryService.runQuery(request.message().trim(), conversation);
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