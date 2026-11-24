package com.raktaveda.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class HttpAiClientTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void joinsTextBlocks() {
        String json = "{\"content\":[{\"type\":\"text\",\"text\":\"Hello \"},{\"type\":\"text\",\"text\":\"there\"}]}";
        assertEquals("Hello there", HttpAiClient.extractText(mapper, json));
    }

    @Test
    void emptyOrBrokenReplyIsUnavailable() {
        assertThrows(AiUnavailableException.class, () -> HttpAiClient.extractText(mapper, "{\"content\":[]}"));
        assertThrows(AiUnavailableException.class, () -> HttpAiClient.extractText(mapper, "not json"));
    }

    @Test
    void fakeClientNeedsNoKeyAndReturnsFixedReply() {
        assertEquals(FakeAiClient.REPLY, new FakeAiClient().ask("system", "question"));
    }
}
