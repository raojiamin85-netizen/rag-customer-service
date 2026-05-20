package com.example;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ChatController {

    private final CustomerServiceRagService ragService;

    public ChatController(CustomerServiceRagService ragService) {
        this.ragService = ragService;
    }

    @PostMapping(value = "/api/chat", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ChatResponse chat(@RequestBody ChatRequest request) {
        String question = request == null ? null : request.getQuestion();
        if (question == null || question.trim().isEmpty()) {
            return new ChatResponse("Please provide a question.");
        }
        String answer = ragService.answer(question.trim());
        return new ChatResponse(answer);
    }
}
