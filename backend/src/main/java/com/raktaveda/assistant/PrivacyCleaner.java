package com.raktaveda.assistant;

import java.text.Normalizer;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Removes personal details from a question BEFORE it is sent to the AI service.
 * Removed (replaced with [removed]): email addresses, dates written as dates (for example a date of birth),
 * and phone numbers (any run of 10 or more digits, with or without spaces, dashes or brackets, and any
 * number that starts with + and has at least 7 digits).
 *
 * Limit (important): names and places typed in plain words cannot be reliably found by simple rules,
 * so they are NOT removed. The question page should tell people not to type personal details.
 * Needs review before real use (privacy).
 */
final class PrivacyCleaner {

    static final String REMOVED = "[removed]";

    private static final int FLAGS = Pattern.UNICODE_CHARACTER_CLASS | Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;

    private static final String MONTHS = "(?:january|february|march|april|may|june|july|august|september|october|november|december"
            + "|jan|feb|mar|apr|jun|jul|aug|sep|sept|oct|nov|dec"
            + "|enero|febrero|marzo|abril|mayo|junio|julio|agosto|septiembre|setiembre|octubre|noviembre|diciembre"
            + "|जनवरी|फ़रवरी|फरवरी|मार्च|अप्रैल|मई|जून|जुलाई|अगस्त|सितंबर|सितम्बर|अक्टूबर|नवंबर|नवम्बर|दिसंबर|दिसम्बर)";

    private static final Pattern EMAIL = Pattern.compile("[\\p{L}\\p{N}._%+\\-]+@[\\p{L}\\p{N}\\-]+(?:\\.[\\p{L}\\p{N}\\-]+)+", FLAGS);

    private static final List<Pattern> DATES = List.of(
            Pattern.compile("(?<!\\p{N})\\p{Nd}{1,2}[/.\\-]\\p{Nd}{1,2}[/.\\-]\\p{Nd}{2,4}(?!\\p{N})", FLAGS),
            Pattern.compile("(?<!\\p{N})\\p{Nd}{4}[/.\\-]\\p{Nd}{1,2}[/.\\-]\\p{Nd}{1,2}(?!\\p{N})", FLAGS),
            Pattern.compile("(?<!\\p{N})\\p{Nd}{1,2}(?:st|nd|rd|th)?\\s+(?:of\\s+|de\\s+)?" + MONTHS
                    + "\\.?,?\\s+(?:de\\s+)?\\p{Nd}{4}(?!\\p{N})", FLAGS),
            Pattern.compile("(?<!\\p{L})" + MONTHS + "\\.?\\s+\\p{Nd}{1,2}(?:st|nd|rd|th)?,?\\s+\\p{Nd}{4}(?!\\p{N})", FLAGS),
            Pattern.compile("\\p{Nd}{4}\\s?[年년]\\s?\\p{Nd}{1,2}\\s?[月월]\\s?\\p{Nd}{1,2}\\s?[日일]?", FLAGS));

    // 10 or more digits, with at most two separator characters between digits.
    private static final Pattern LONG_NUMBER = Pattern.compile(
            "\\+?\\p{Nd}(?:[\\s.\\-()]{0,2}\\p{Nd}){9,}", FLAGS);
    // A number written with a leading + (an international number), at least 7 digits.
    private static final Pattern PLUS_NUMBER = Pattern.compile(
            "\\+[\\s.\\-()]*\\p{Nd}(?:[\\s.\\-()]{0,2}\\p{Nd}){6,}", FLAGS);

    private PrivacyCleaner() {
    }

    static String clean(String text) {
        String t = Normalizer.normalize(text, Normalizer.Form.NFKC);
        t = EMAIL.matcher(t).replaceAll(REMOVED);
        for (Pattern p : DATES) {
            t = p.matcher(t).replaceAll(REMOVED);
        }
        t = PLUS_NUMBER.matcher(t).replaceAll(REMOVED);
        t = LONG_NUMBER.matcher(t).replaceAll(REMOVED);
        return t;
    }

    /** True if the text contains something that looks like a phone number. Used on the AI's reply. */
    static boolean containsPhoneNumber(String text) {
        String t = Normalizer.normalize(text, Normalizer.Form.NFKC);
        return LONG_NUMBER.matcher(t).find() || PLUS_NUMBER.matcher(t).find();
    }
}
