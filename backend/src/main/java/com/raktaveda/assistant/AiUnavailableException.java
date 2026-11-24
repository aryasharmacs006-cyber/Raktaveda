package com.raktaveda.assistant;

/** The AI service could not answer (timeout, network error, bad status, bad reply). The message is a short reason only. */
public class AiUnavailableException extends RuntimeException {

    public AiUnavailableException(String shortReason) {
        super(shortReason);
    }

    public AiUnavailableException(String shortReason, Throwable cause) {
        super(shortReason, cause);
    }
}
