package com.example;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.segment.TextSegment;

import java.util.ArrayList;
import java.util.List;

public class CustomerServiceDocumentSplitter implements DocumentSplitter {

    @Override
    public List<TextSegment> split(Document document) {
        List<TextSegment> segments = new ArrayList<>();
        for (String part : split(document.text())) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                segments.add(TextSegment.from(trimmed));
            }
        }
        return segments;
    }

    public String[] split(String text) {
        return text.split("\\s*\\r?\\n\\s*\\r?\\n\\s*");
    }
}
