package com.raktaveda.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Calls an external AI service over HTTPS (the Anthropic "Messages" API).
 *
 * - The API key comes from the environment (AI_API_KEY) and is kept only in memory. It is never logged,
 *   never put in an exception message and never written to a file.
 * - The model name comes from AI_MODEL.
 * - Every call has a 10 second limit. Any failure becomes {@link AiUnavailableException}
 *   with a short reason (a status number or the word "timeout"). The question, the answer and the
 *   server's error text are never logged.
 *
 * Needs review before real use: this service is usually paid. Check the provider's pricing, its data rules
 * (what it does with the text we send), and whether it is allowed for a health-related app.
 */
public class HttpAiClient implements AiClient {

    private static final Logger log = LoggerFactory.getLogger(HttpAiClient.class);

    static final URI ENDPOINT = URI.create("https://api.anthropic.com/v1/messages");
    static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final int MAX_ANSWER_TOKENS = 400;

    private final String apiKey;
    private final String model;
    private final ObjectMapper mapper;
    private final HttpClient http;

    public HttpAiClient(String apiKey, String model, ObjectMapper mapper) {
        this.apiKey = apiKey;
        this.model = model;
        this.mapper = mapper;
        this.http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    }

    @Override
    public String ask(String systemPrompt, String userText) {
        String body;
        try {
            ObjectNode root = mapper.createObjectNode();
            root.put("model", model);
            root.put("max_tokens", MAX_ANSWER_TOKENS);
            root.put("system", systemPrompt);
            ArrayNode messages = root.putArray("messages");
            messages.addObject().put("role", "user").put("content", userText);
            body = mapper.writeValueAsString(root);
        } catch (IOException e) {
            throw new AiUnavailableException("request_build_failed");
        }

        HttpRequest request = HttpRequest.newBuilder(ENDPOINT)
                .timeout(TIMEOUT)
                .header("content-type", "application/json")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                log.warn("AI service answered with status {}", response.statusCode());
                throw new AiUnavailableException("status_" + response.statusCode());
            }
            return extractText(mapper, response.body());
        } catch (java.net.http.HttpTimeoutException e) {
            log.warn("AI service timed out");
            throw new AiUnavailableException("timeout");
        } catch (IOException e) {
            log.warn("AI service could not be reached ({})", e.getClass().getSimpleName());
            throw new AiUnavailableException("network");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiUnavailableException("interrupted");
        }
    }

    /** Joins the text blocks of the reply. Package-visible so it can be tested without a network. */
    static String extractText(ObjectMapper mapper, String json) {
        try {
            JsonNode content = mapper.readTree(json).path("content");
            StringBuilder text = new StringBuilder();
            if (content.isArray()) {
                for (JsonNode block : content) {
                    if ("text".equals(block.path("type").asText()) && block.path("text").isTextual()) {
                        text.append(block.path("text").asText());
                    }
                }
            }
            if (text.toString().isBlank()) {
                throw new AiUnavailableException("empty_reply");
            }
            return text.toString();
        } catch (IOException e) {
            throw new AiUnavailableException("bad_reply");
        }
    }
}
