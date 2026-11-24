package com.raktaveda.assistant;

/**
 * The only thing the assistant needs from an AI service: send instructions plus a question, get text back.
 * Two versions exist: {@link FakeAiClient} (no key, for development) and {@link HttpAiClient} (real service).
 *
 * An implementation must throw {@link AiUnavailableException} when it cannot get an answer.
 * It must never put the question, the answer or the API key in a log line or an exception message.
 */
public interface AiClient {

    String ask(String systemPrompt, String userText);
}
