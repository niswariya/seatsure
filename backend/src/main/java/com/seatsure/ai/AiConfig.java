package com.seatsure.ai;

import com.seatsure.config.SeatSureProperties;
import com.seatsure.event.EventRepository;
import com.seatsure.event.EventService;
import com.seatsure.event.SeatRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * All AI wiring. Loaded only when seatsure.ai.enabled=true (the "aws" profile),
 * so the rest of the app runs without AWS credentials.
 */
@Configuration
@ConditionalOnProperty(prefix = "seatsure.ai", name = "enabled", havingValue = "true")
public class AiConfig {

    private static final Logger log = LoggerFactory.getLogger(AiConfig.class);

    /** Short-term conversation memory: the last N messages per conversation, in memory. */
    @Bean
    ChatMemory chatMemory(SeatSureProperties props) {
        return MessageWindowChatMemory.builder()
                .maxMessages(props.ai().memoryMessages())
                .build();
    }

    /** Help-centre embeddings (Titan v2), kept in memory. Fine for a few dozen chunks. */
    @Bean
    VectorStore helpCentreStore(EmbeddingModel embeddingModel) {
        return SimpleVectorStore.builder(embeddingModel).build();
    }

    @Bean
    SeatTools seatTools(EventService eventService, EventRepository events, SeatRepository seats,
                        VectorStore helpCentreStore) {
        return new SeatTools(eventService, events, seats, helpCentreStore);
    }

    /** The concierge: tools + memory. The system prompt is set per request (it carries the current time). */
    @Bean
    ChatClient conciergeClient(ChatClient.Builder builder, ChatMemory chatMemory, SeatTools seatTools) {
        return builder
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        new SimpleLoggerAdvisor())       // logs prompts/responses at DEBUG
                .defaultTools(seatTools)
                .build();
    }

    /** A plain client for one-shot structured generation (no tools, no memory). */
    @Bean
    ChatClient writerClient(ChatClient.Builder builder) {
        return builder.build();
    }

    /**
     * RAG ingestion: split the help-centre markdown files into one chunk per
     * "## " section, embed them with Titan and load the vector store at startup.
     * A failure here (e.g. model access not granted) is logged, not fatal.
     */
    @Bean
    ApplicationRunner helpCentreLoader(VectorStore helpCentreStore) {
        return args -> {
            try {
                List<Document> chunks = new ArrayList<>();
                Resource[] files = new PathMatchingResourcePatternResolver().getResources("classpath:knowledge/*.md");
                for (Resource file : files) {
                    chunks.addAll(splitBySection(file));
                }
                helpCentreStore.add(chunks);
                log.info("Help centre indexed: {} chunks from {} files", chunks.size(), files.length);
            } catch (RuntimeException | IOException e) {
                log.error("Could not index help centre; policy answers will be unavailable: {}", e.getMessage());
            }
        };
    }

    static List<Document> splitBySection(Resource file) throws IOException {
        String name = file.getFilename();
        String text = file.getContentAsString(StandardCharsets.UTF_8);
        String title = text.lines().filter(l -> l.startsWith("# ")).findFirst().map(l -> l.substring(2)).orElse(name);
        List<Document> docs = new ArrayList<>();
        for (String section : text.split("(?m)^## ")) {
            if (section.isBlank() || section.startsWith("# ")) continue;
            String heading = section.lines().findFirst().orElse("").trim();
            // Prefix the document title so each chunk makes sense on its own.
            docs.add(new Document(title + " — " + section.trim(), Map.of("source", String.valueOf(name), "section", heading)));
        }
        return docs;
    }
}
