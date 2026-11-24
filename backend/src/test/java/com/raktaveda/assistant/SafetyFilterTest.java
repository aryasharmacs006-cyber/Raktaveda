package com.raktaveda.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class SafetyFilterTest {

    private static Optional<SafetyFilter.Kind> kind(String q) {
        return SafetyFilter.check(q);
    }

    @Test
    void emergencyWordsInAllFiveLanguages() {
        assertEquals(Optional.of(SafetyFilter.Kind.EMERGENCY), kind("My friend is unconscious after donating"));
        assertEquals(Optional.of(SafetyFilter.Kind.EMERGENCY), kind("I have chest pain"));
        assertEquals(Optional.of(SafetyFilter.Kind.EMERGENCY), kind("वह बेहोश हो गया है"));
        assertEquals(Optional.of(SafetyFilter.Kind.EMERGENCY), kind("가슴 통증이 있어요"));
        assertEquals(Optional.of(SafetyFilter.Kind.EMERGENCY), kind("他昏迷了"));
        assertEquals(Optional.of(SafetyFilter.Kind.EMERGENCY), kind("Creo que es un infarto"));
        assertEquals(Optional.of(SafetyFilter.Kind.EMERGENCY), kind("heavy BLEEDING from the arm"));
    }

    @Test
    void dosageAndDiagnosis() {
        assertEquals(Optional.of(SafetyFilter.Kind.MEDICAL_ADVICE), kind("How many mg of iron should I take?"));
        assertEquals(Optional.of(SafetyFilter.Kind.MEDICAL_ADVICE), kind("What dosage of paracetamol?"));
        assertEquals(Optional.of(SafetyFilter.Kind.MEDICAL_ADVICE), kind("Can you diagnose me?"));
        assertEquals(Optional.of(SafetyFilter.Kind.MEDICAL_ADVICE), kind("दवा की खुराक कितनी हो?"));
        assertEquals(Optional.of(SafetyFilter.Kind.MEDICAL_ADVICE), kind("¿Qué dosis debo tomar?"));
    }

    @Test
    void selfHarmWinsOverOtherKinds() {
        assertEquals(Optional.of(SafetyFilter.Kind.SELF_HARM), kind("I want to die"));
        assertEquals(Optional.of(SafetyFilter.Kind.SELF_HARM), kind("overdose to kill myself"));
        assertEquals(Optional.of(SafetyFilter.Kind.SELF_HARM), kind("죽고 싶어요"));
        assertEquals(Optional.of(SafetyFilter.Kind.SELF_HARM), kind("我想自杀"));
        assertEquals(Optional.of(SafetyFilter.Kind.SELF_HARM), kind("quiero morir"));
    }

    @Test
    void normalQuestionsPass() {
        assertTrue(kind("What happens when I donate blood?").isEmpty());
        assertTrue(kind("How do I create an emergency request in the app?").isEmpty());
        assertTrue(kind("Should I eat before donating?").isEmpty());
        assertTrue(kind("रक्तदान से पहले क्या खाना चाहिए?").isEmpty());
    }

    @Test
    void selfHarmReplyHasHelplineGuidance() {
        String reply = SafetyFilter.replyFor(SafetyFilter.Kind.SELF_HARM, Lang.EN);
        assertTrue(reply.contains("112"));
        assertTrue(reply.contains("14416"));
        assertTrue(reply.toLowerCase().contains("emergency"));
    }

    @Test
    void detectsLanguage() {
        assertEquals(Lang.EN, Lang.detect("How do I donate?"));
        assertEquals(Lang.HI, Lang.detect("रक्तदान कैसे करें"));
        assertEquals(Lang.KO, Lang.detect("헌혈은 어떻게 하나요"));
        assertEquals(Lang.ZH, Lang.detect("如何献血"));
        assertEquals(Lang.ES, Lang.detect("¿Cómo puedo donar sangre?"));
    }
}
