package com.raktaveda.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Picks the AI client at start-up: the real one when AI_API_KEY is set, otherwise the fake one.
 * The key is read from the environment only (not from application.yml) and is never printed.
 */
@Configuration
public class AiClientConfig {

    private static final Logger log = LoggerFactory.getLogger(AiClientConfig.class);

    @Bean
    public AiClient aiClient(@Value("${AI_API_KEY:}") String apiKey,
                             @Value("${AI_MODEL:}") String model,
                             ObjectMapper mapper) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Assistant: AI_API_KEY is not set, so the FAKE assistant is used (fixed reply, development only).");
            return new FakeAiClient();
        }
        if (model == null || model.isBlank()) {
            throw new IllegalStateException("AI_MODEL must be set when AI_API_KEY is set.");
        }
        log.info("Assistant: using the external AI service with model {}.", model.trim());
        return new HttpAiClient(apiKey.trim(), model.trim(), mapper);
    }
}
