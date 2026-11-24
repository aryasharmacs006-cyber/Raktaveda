package com.raktaveda.dataimport;

import java.util.ArrayList;
import java.util.List;

/**
 * A small CSV reader (CSV = a text table where commas separate the columns).
 * It follows the common rules: a value may be wrapped in double quotes, a quote inside a quoted value is
 * written twice (""), and a quoted value may contain commas and line breaks. A leading byte-order mark
 * (an invisible marker some spreadsheet programs add) is ignored, and blank lines are skipped.
 * It reads text only. It never runs or evaluates anything.
 */
public final class CsvParser {

    private CsvParser() {
    }

    /** Throws IllegalArgumentException if a quoted value is never closed. */
    public static List<List<String>> parse(String text) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean inQuotes = false;
        boolean cellWasQuoted = false;
        int i = text.startsWith("﻿") ? 1 : 0;
        int n = text.length();

        while (i < n) {
            char c = text.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < n && text.charAt(i + 1) == '"') {
                        cell.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    cell.append(c);
                }
            } else if (c == '"' && cell.length() == 0 && !cellWasQuoted) {
                inQuotes = true;
                cellWasQuoted = true;
            } else if (c == ',') {
                row.add(cell.toString());
                cell.setLength(0);
                cellWasQuoted = false;
            } else if (c == '\r' || c == '\n') {
                if (c == '\r' && i + 1 < n && text.charAt(i + 1) == '\n') {
                    i++;
                }
                finishRow(rows, row, cell);
                cellWasQuoted = false;
                row = new ArrayList<>();
            } else {
                cell.append(c);
            }
            i++;
        }
        if (inQuotes) {
            throw new IllegalArgumentException("Unterminated quote");
        }
        if (cell.length() > 0 || !row.isEmpty() || cellWasQuoted) {
            finishRow(rows, row, cell);
        }
        return rows;
    }

    private static void finishRow(List<List<String>> rows, List<String> row, StringBuilder cell) {
        row.add(cell.toString());
        cell.setLength(0);
        boolean blankLine = row.size() == 1 && row.get(0).isBlank();
        if (!blankLine) {
            rows.add(row);
        }
    }
}
