package com.example;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.service.AiServices;

public interface CustomerServiceAgent {

    String answer(String question);

    static CustomerServiceAgent create(ContentRetriever contentRetriever, ChatMemory chatMemory) {
        String apiKey = System.getenv("MIMO_API_KEY");
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalStateException("Missing MIMO_API_KEY env var.");
        }

        ChatModel model = OpenAiChatModel.builder()
                .apiKey(apiKey)
                .baseUrl("https://api.xiaomimimo.com/v1")
                .modelName("mimo-v2-flash")
                .build();

        return AiServices.builder(CustomerServiceAgent.class)
            .chatModel(model)
            .contentRetriever(contentRetriever)
            .chatMemory(chatMemory)
            .tools(new DateCalculator())
            .build();
    }
}
