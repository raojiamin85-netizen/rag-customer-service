package com.example;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class App {
    public static void main(String[] args) {
        String apiKey = requireApiKey();

        String userQuestion = "在线支付取消订单后钱怎么返还？";
        Document document = loadDocument();
        DocumentSplitter splitter = new CustomerServiceDocumentSplitter();
        List<TextSegment> segments = splitter.split(document);
        List<QaEntry> qaEntries = parseSegments(segments);

        EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();
        List<TextSegment> questionSegments = extractQuestionSegments(segments);
        List<Embedding> embeddings = embeddingModel.embedAll(questionSegments).content();
        System.out.println("Embeddings generated: " + embeddings.size());

        EmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();
        for (int i = 0; i < embeddings.size(); i++) {
            embeddingStore.add(embeddings.get(i), segments.get(i));
        }
        System.out.println("Embeddings stored: " + embeddings.size());
        ContentRetriever contentRetriever = EmbeddingStoreContentRetriever.builder()
            .embeddingStore(embeddingStore)
            .embeddingModel(embeddingModel)
            .maxResults(5)
            .minScore(0.8)
            .build();

        ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);
        CustomerServiceAgent agent = CustomerServiceAgent.create(contentRetriever, chatMemory);

        Query query = new Query(userQuestion);
        List<Content> retrieved = contentRetriever.retrieve(query);
        List<QaEntry> topMatches = parseContents(retrieved);
        if (topMatches.isEmpty()) {
            topMatches = retrieveTopK(userQuestion, qaEntries, 3);
        }

        String prompt = buildPrompt(userQuestion, topMatches);
        String result = agent.answer(prompt);
        System.out.println(result);
    }

    private static String requireApiKey() {
        String apiKey = System.getenv("MIMO_API_KEY");
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalStateException("Missing MIMO_API_KEY env var.");
        }
        return apiKey;
    }


    private static Document loadDocument() {
        try {
            URL resource = CustomerServiceAgent.class.getClassLoader().getResource("meituan-qa.txt");
            if (resource == null) {
                throw new IllegalStateException("Cannot find resource meituan-qa.txt in classpath.");
            }
            Path documentPath = Paths.get(resource.toURI());
            TextDocumentParser documentParser = new TextDocumentParser();
            return FileSystemDocumentLoader.loadDocument(documentPath, documentParser);
        } catch (URISyntaxException ex) {
            throw new IllegalStateException("Failed to load QA document.", ex);
        }
    }

    private static List<QaEntry> parseSegments(List<TextSegment> segments) {
        List<QaEntry> entries = new ArrayList<>();
        for (TextSegment segment : segments) {
            QaEntry entry = parseSegmentText(segment.text());
            if (entry != null) {
                entries.add(entry);
            }
        }
        return entries;
    }

    private static List<TextSegment> extractQuestionSegments(List<TextSegment> segments) {
        List<TextSegment> questions = new ArrayList<>();
        for (TextSegment segment : segments) {
            String text = segment.text();
            String firstLine = text.split("\\r?\\n", 2)[0].trim();
            if (!firstLine.isEmpty()) {
                questions.add(TextSegment.from(firstLine));
            }
        }
        return questions;
    }

    private static QaEntry parseSegmentText(String text) {
        Pattern pattern = Pattern.compile("Q:\\s*(.*?)\\r?\\n(.*)", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(text.trim());
        if (!matcher.find()) {
            return null;
        }
        String question = matcher.group(1).trim();
        String answer = matcher.group(2).trim();
        if (question.isEmpty() || answer.isEmpty()) {
            return null;
        }
        return new QaEntry(question, answer);
    }

    private static List<QaEntry> retrieveTopK(String query, List<QaEntry> entries, int k) {
        String normalizedQuery = normalize(query);
        return entries.stream()
                .map(entry -> new ScoredEntry(entry, score(normalizedQuery, entry)))
                .sorted(Comparator.comparingInt(ScoredEntry::score).reversed())
                .limit(k)
                .map(ScoredEntry::entry)
                .collect(java.util.stream.Collectors.toList());
    }

    private static List<QaEntry> parseContents(List<Content> contents) {
        List<QaEntry> entries = new ArrayList<>();
        for (Content content : contents) {
            String text = content.textSegment().text();
            QaEntry entry = parseSegmentText(text);
            if (entry != null) {
                entries.add(entry);
            }
        }
        return entries;
    }

    private static int score(String normalizedQuery, QaEntry entry) {
        String target = normalize(entry.question + " " + entry.answer);
        int score = 0;
        for (String token : normalizedQuery.split("\\s+")) {
            if (token.length() < 2) {
                continue;
            }
            if (target.contains(token)) {
                score += 1;
            }
        }
        return score;
    }

    private static String normalize(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    }

    private static String buildPrompt(String question, List<QaEntry> context) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是企业客服助手。只能基于下列资料回答问题。\n\n");
        sb.append("资料：\n");
        for (QaEntry entry : context) {
            sb.append("Q: ").append(entry.question).append("\n");
            sb.append("A: ").append(entry.answer).append("\n\n");
        }
        sb.append("用户问题：").append(question).append("\n");
        sb.append("回答：");
        return sb.toString();
    }

    private static final class QaEntry {
        private final String question;
        private final String answer;

        private QaEntry(String question, String answer) {
            this.question = question;
            this.answer = answer;
        }
    }

    private static final class ScoredEntry {
        private final QaEntry entry;
        private final int score;

        private ScoredEntry(QaEntry entry, int score) {
            this.entry = entry;
            this.score = score;
        }

        private QaEntry entry() {
            return entry;
        }

        private int score() {
            return score;
        }
    }
}
