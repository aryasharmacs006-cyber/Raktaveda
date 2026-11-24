package com.raktaveda.dataimport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class CsvParserTest {

    @Test
    void parsesSimpleRows() {
        List<List<String>> rows = CsvParser.parse("a,b\n1,2\n");
        assertEquals(List.of(List.of("a", "b"), List.of("1", "2")), rows);
    }

    @Test
    void handlesQuotesCommasAndWindowsLineEndings() {
        List<List<String>> rows = CsvParser.parse("name,note\r\n\"Smith, J\",\"say \"\"hi\"\"\"\r\n");
        assertEquals(List.of("Smith, J", "say \"hi\""), rows.get(1));
    }

    @Test
    void stripsByteOrderMark() {
        assertEquals("a", CsvParser.parse("﻿a,b\n").get(0).get(0));
    }

    @Test
    void rejectsUnterminatedQuote() {
        assertThrows(IllegalArgumentException.class, () -> CsvParser.parse("a,\"b\n"));
    }
}
