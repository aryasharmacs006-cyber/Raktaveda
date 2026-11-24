package com.raktaveda.assistant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OutputGuardTest {

    @Test
    void acceptsPlainGeneralAnswers() {
        assertTrue(OutputGuard.isSafe("Donors should eat and drink normally. The blood bank decides eligibility."));
        assertTrue(OutputGuard.isSafe("In an emergency call 112 and go to the nearest hospital."));
    }

    @Test
    void rejectsEmptyReplies() {
        assertFalse(OutputGuard.isSafe(null));
        assertFalse(OutputGuard.isSafe("   "));
    }

    @Test
    void rejectsPhoneNumbers() {
        assertFalse(OutputGuard.isSafe("Call the blood bank on 98765 43210."));
        assertFalse(OutputGuard.isSafe("Call +44 20 7946 0958."));
    }

    @Test
    void rejectsClaimsThatTheAppDecidesEligibility() {
        assertFalse(OutputGuard.isSafe("The app decides whether you are eligible to donate."));
        assertFalse(OutputGuard.isSafe("Raktaveda will confirm your eligibility."));
        assertFalse(OutputGuard.isSafe("Your eligibility is decided by the app."));
        assertFalse(OutputGuard.isSafe("You are eligible to donate."));
        assertFalse(OutputGuard.isSafe("Usted es elegible para donar."));
        assertFalse(OutputGuard.isSafe("La app decide si usted es elegible."));
    }

    @Test
    void doesNotRejectTheCorrectStatement() {
        assertTrue(OutputGuard.isSafe("The blood bank decides eligibility, not the app."));
    }
}
