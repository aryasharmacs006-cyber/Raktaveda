package com.raktaveda.common;

public final class SqlUtil {

    private SqlUtil() {
    }

    /** "?,?,?" for an IN list. Values are still passed as parameters, never joined into the SQL text. */
    public static String placeholders(int count) {
        if (count < 1) {
            throw new IllegalArgumentException("count must be at least 1");
        }
        return "?" + ",?".repeat(count - 1);
    }
}
