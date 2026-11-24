package com.raktaveda.assistant;

public final class AssistantDtos {

    private AssistantDtos() {
    }

    /** The question is checked in the service (so that a too-long question gets its own error code). */
    public record QuestionRequest(String question) {
    }

    /**
     * source: AI (answer written by the AI), SAFETY (fixed reply because of an emergency, medical-advice or
     * self-harm question), or FALLBACK (the AI's reply failed a safety check, so a fixed reply was used).
     */
    public record AnswerResponse(String answer, String source, int remainingToday) {
    }
}
