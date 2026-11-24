package com.raktaveda.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PrivacyCleanerTest {

    @Test
    void removesPhoneNumbersInCommonShapes() {
        assertEquals("call me [removed] now", PrivacyCleaner.clean("call me 9876543210 now"));
        assertEquals("call [removed]", PrivacyCleaner.clean("call +91 98765 43210"));
        assertEquals("call [removed]", PrivacyCleaner.clean("call 98765-43210"));
        assertEquals("call [removed]", PrivacyCleaner.clean("call +1 555 0101"));
        assertEquals("call [removed]", PrivacyCleaner.clean("call ９８７６５４３２１０")); // full-width digits
    }

    @Test
    void removesEmails() {
        assertEquals("write to [removed] please", PrivacyCleaner.clean("write to ravi.k+test@example.co.in please"));
    }

    @Test
    void removesDatesOfBirth() {
        assertEquals("born [removed]", PrivacyCleaner.clean("born 15/06/1992"));
        assertEquals("born [removed]", PrivacyCleaner.clean("born 1992-06-15"));
        assertEquals("born [removed]", PrivacyCleaner.clean("born 15 June 1992"));
        assertEquals("born [removed]", PrivacyCleaner.clean("born June 15, 1992"));
        assertEquals("born [removed]", PrivacyCleaner.clean("born 15 de junio de 1992"));
        assertEquals("born [removed]", PrivacyCleaner.clean("born 1992년 6월 15일"));
    }

    @Test
    void keepsOrdinaryQuestions() {
        String q = "Can I donate blood every 3 months? What about 450 ml?";
        assertEquals(q, PrivacyCleaner.clean(q));
        assertEquals("Call 112 in an emergency", PrivacyCleaner.clean("Call 112 in an emergency"));
    }

    @Test
    void detectsPhoneNumbersInReplies() {
        assertTrue(PrivacyCleaner.containsPhoneNumber("Call 98765 43210"));
        assertTrue(PrivacyCleaner.containsPhoneNumber("Call +44 20 7946 0958"));
        assertFalse(PrivacyCleaner.containsPhoneNumber("Call 112 now. Wait 120 days."));
    }
}
