package com.raktaveda.assistant;

import java.text.Normalizer;
import java.util.Locale;

/**
 * The five languages the assistant answers in. Anything else is treated as English.
 * Detection is a simple guess from the letters used (and a few common Spanish words). It only
 * chooses which fixed message to show, so a wrong guess is harmless.
 */
public enum Lang {
    EN, HI, KO, ZH, ES;

    public static Lang detect(String text) {
        if (text == null) {
            return EN;
        }
        int hangul = 0;
        int han = 0;
        int devanagari = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            Character.UnicodeScript script = Character.UnicodeScript.of(cp);
            switch (script) {
                case HANGUL -> hangul++;
                case HAN -> han++;
                case DEVANAGARI -> devanagari++;
                default -> { }
            }
        }
        if (hangul > 0) {
            return KO;
        }
        if (devanagari > 0) {
            return HI;
        }
        if (han > 0) {
            return ZH;
        }
        String lower = Normalizer.normalize(text, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        if (lower.contains("¿") || lower.contains("¡") || lower.matches("(?s).*[ñáéíóú].*")
                || lower.matches("(?s).*\\b(puedo|donar|sangre|cómo|como puedo|qué|dónde|quiero|necesito|para|donación)\\b.*")) {
            return ES;
        }
        return EN;
    }
}
