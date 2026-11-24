package com.raktaveda.assistant;

/**
 * Development stand-in, used when AI_API_KEY is not set. It never contacts anyone and always
 * returns the same safe, general reply, so the app runs without a key.
 */
public class FakeAiClient implements AiClient {

    static final String REPLY = "This is a development reply (no AI service is connected). "
            + "In general: blood donation is a short, simple visit, and the blood bank or a doctor makes the "
            + "final decision on whether someone can donate. In an emergency, call 112 and go to the nearest hospital.";

    @Override
    public String ask(String systemPrompt, String userText) {
        return REPLY;
    }
}
