package com.raktaveda.assistant;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Loads assistant/approved-facts.md once at start-up. These facts are the ONLY knowledge the assistant may use.
 * The app refuses to start if the file is missing or empty, so the assistant can never run without its facts.
 */
@Component
public class ApprovedFacts {

    private final String text;

    public ApprovedFacts() {
        try (InputStream in = new ClassPathResource("assistant/approved-facts.md").getInputStream()) {
            this.text = new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            throw new IllegalStateException("assistant/approved-facts.md is missing. The assistant cannot start without it.", e);
        }
        if (text.isEmpty()) {
            throw new IllegalStateException("assistant/approved-facts.md is empty.");
        }
    }

    /** For tests: use the given text instead of the file. */
    ApprovedFacts(String text) {
        this.text = text;
    }

    public String text() {
        return text;
    }
}
