package com.raktaveda.assistant;

import java.text.Normalizer;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The check done AFTER the AI replies. A reply that fails is thrown away and a fixed safe reply is used.
 * It fails if it is empty, contains a phone number, claims that the app (or the assistant) decides
 * eligibility, or tells a person that they ARE or ARE NOT eligible.
 *
 * Limit: the "claims" checks are written for English and Spanish. Needs review before real use (medical).
 */
final class OutputGuard {

    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;

    private static final List<Pattern> BAD_CLAIMS = List.of(
            // "the app / Raktaveda / I / we decide(s) / determine(s) / approve(s) / confirm(s) ... eligibility"
            Pattern.compile("\\b(?:the app|this app|our app|raktaveda|the system|the platform|the assistant|i|we)\\b[^.!?\\n]{0,40}"
                    + "\\b(?:decide|decides|decided|determine|determines|determined|approve|approves|approved|confirm|confirms|confirmed)\\b"
                    + "[^.!?\\n]{0,40}\\beligib", FLAGS),
            // "eligibility is decided / determined / confirmed by the app"
            Pattern.compile("\\beligib\\w*[^.!?\\n]{0,40}\\b(?:decided|determined|approved|confirmed|checked)\\s+by\\s+(?:the|this|our)?\\s*(?:app|system|platform|assistant|raktaveda)\\b", FLAGS),
            // telling a person their own result
            Pattern.compile("\\byou(?:'re| are)\\s+(?:definitely\\s+|certainly\\s+|not\\s+|un)?eligible\\b", FLAGS),
            Pattern.compile("\\byou\\s+(?:qualify|do not qualify|don't qualify)\\b", FLAGS),
            // Spanish
            Pattern.compile("\\b(?:la app|la aplicaci[oó]n|el sistema|raktaveda)\\b[^.!?\\n]{0,40}\\b(?:decide|determina|aprueba|confirma)\\b[^.!?\\n]{0,40}\\belegib", FLAGS),
            Pattern.compile("\\busted\\s+(?:es|no es)\\s+elegible\\b", FLAGS));

    private OutputGuard() {
    }

    /** True if the reply may be shown. */
    static boolean isSafe(String reply) {
        if (reply == null || reply.isBlank()) {
            return false;
        }
        String text = Normalizer.normalize(reply, Normalizer.Form.NFKC);
        if (PrivacyCleaner.containsPhoneNumber(text)) {
            return false;
        }
        for (Pattern p : BAD_CLAIMS) {
            if (p.matcher(text).find()) {
                return false;
            }
        }
        return true;
    }
}
