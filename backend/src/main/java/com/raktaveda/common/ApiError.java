package com.raktaveda.common;

import java.time.Instant;

/** The one JSON error format used everywhere: { "error", "message", "timestamp" }. */
public record ApiError(String error, String message, Instant timestamp) {
}
