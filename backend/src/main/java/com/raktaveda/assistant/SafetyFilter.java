package com.raktaveda.assistant;

// Needs review before real use (medical): a doctor must review every word list in this file.

import java.text.Normalizer;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The check done BEFORE the AI is called. If the question looks like an emergency, a request for medical
 * advice (dosage or diagnosis), or self-harm, the AI is not called and a fixed reply is returned.
 *
 * Words are matched in English, Hindi, Korean, Simplified Chinese and Spanish. Order matters: self-harm is
 * checked first, then emergency, then medical advice.
 *
 * This is a keyword list. It will miss some phrasings and will sometimes trigger on harmless questions
 * (for example "is bleeding normal after a donation" is treated as an emergency on purpose: it is safer
 * to over-warn). Needs review before real use (medical): a doctor should review the word lists.
 */
final class SafetyFilter {

    enum Kind { SELF_HARM, EMERGENCY, MEDICAL_ADVICE }

    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;

    private static final List<Pattern> SELF_HARM = compile(
            // English
            "\\bsuicid", "\\bkill (?:myself|me)\\b", "\\bend (?:my|this) life\\b", "\\bwant to die\\b", "\\bwanna die\\b",
            "\\bself[- ]?harm", "\\bhurt (?:myself|me)\\b", "\\bharm myself\\b", "\\bcut myself\\b", "\\bno reason to live\\b",
            // Hindi
            "आत्महत्या", "खुदकुशी", "मरना चाहता", "मरना चाहती", "जान दे", "खुद को नुकसान", "खुद को चोट",
            // Korean
            "자살", "죽고 싶", "죽고싶", "자해", "스스로 목숨", "목숨을 끊",
            // Chinese
            "自杀", "自殺", "想死", "不想活", "自残", "自残", "伤害自己", "结束生命",
            // Spanish
            "suicid", "quiero morir", "quiero morirme", "matarme", "hacerme daño", "hacerme dano", "quitarme la vida", "autolesi");

    private static final List<Pattern> EMERGENCY = compile(
            // English
            "\\bbleeding\\b", "\\bbleed(?:s)? (?:a lot|heavily|badly)\\b", "\\bunconscious\\b", "\\bpassed out\\b", "\\bpassing out\\b",
            "\\bfaint(?:ed|ing)\\b", "\\bheart attack\\b", "\\bcardiac arrest\\b", "\\boverdos", "\\bchest pain\\b",
            "\\bcan(?:not|'t| not) breathe\\b", "\\bnot breathing\\b", "\\bshortness of breath\\b", "\\bseizure", "\\bstroke\\b",
            "\\bpoison", "\\bsevere pain\\b", "\\bcollapsed\\b",
            // Hindi
            "खून बह", "रक्तस्राव", "बेहोश", "दिल का दौरा", "हार्ट अटैक", "ओवरडोज", "सीने में दर्द", "छाती में दर्द",
            "सांस नहीं", "साँस नहीं", "सांस लेने में तकलीफ", "दौरा पड़",
            // Korean
            "출혈", "피가 많이", "의식이 없", "의식 없", "의식을 잃", "기절", "심장마비", "심근경색", "과다복용", "과다 복용",
            "가슴 통증", "가슴이 아", "숨을 못", "숨이 안", "호흡곤란", "경련",
            // Chinese
            "出血", "流血", "昏迷", "失去意识", "失去意識", "晕倒", "暈倒", "心脏病发", "心臟病發", "心肌梗", "过量", "過量",
            "胸痛", "无法呼吸", "無法呼吸", "呼吸困难", "呼吸困難", "抽搐", "中风", "中風",
            // Spanish
            "sangrado", "sangrando", "sangra mucho", "inconsciente", "desmay", "infarto", "ataque al coraz", "sobredosis",
            "dolor de pecho", "dolor en el pecho", "no respira", "no puedo respirar", "convulsi");

    private static final List<Pattern> MEDICAL_ADVICE = compile(
            // English
            "\\bdosage\\b", "\\bdoses?\\b", "\\b\\d+\\s?(?:mg|mcg|ml)\\b", "\\bhow (?:much|many) (?:mg|tablets?|pills?|capsules?)\\b",
            "\\bprescri(?:be|ption)", "\\bwhich (?:medicine|medication|tablet|pill|drug)\\b", "\\bwhat (?:medicine|medication|tablet|pill|drug)\\b",
            "\\bdiagnos", "\\bdo i have\\b", "\\bam i (?:anaemic|anemic|sick|ill|infected|diabetic)\\b", "\\bwhat(?:'s| is) wrong with me\\b",
            "\\bwhat disease\\b", "\\bam i eligible\\b",
            // Hindi
            "खुराक", "डोज़", "डोज", "कौन सी दवा", "कौन-सी दवा", "कौन सी गोली", "दवा लेनी", "निदान", "मुझे कौन सी बीमारी", "क्या मुझे बीमारी",
            // Korean
            "복용량", "용량", "처방", "무슨 약", "어떤 약", "진단", "무슨 병",
            // Chinese
            "剂量", "劑量", "用药", "用藥", "吃什么药", "吃什麼藥", "处方", "處方", "诊断", "診斷", "我是不是得了", "我得了什么病",
            // Spanish
            "dosis", "posolog", "receta", "qué medicina", "que medicina", "qué medicamento", "que medicamento", "diagnostic", "tengo alguna enfermedad");

    private SafetyFilter() {
    }

    static Optional<Kind> check(String question) {
        String text = Normalizer.normalize(question, Normalizer.Form.NFKC);
        if (matches(SELF_HARM, text)) {
            return Optional.of(Kind.SELF_HARM);
        }
        if (matches(EMERGENCY, text)) {
            return Optional.of(Kind.EMERGENCY);
        }
        if (matches(MEDICAL_ADVICE, text)) {
            return Optional.of(Kind.MEDICAL_ADVICE);
        }
        return Optional.empty();
    }

    static String replyFor(Kind kind, Lang lang) {
        return switch (kind) {
            case SELF_HARM -> SafeReplies.selfHarm(lang);
            case EMERGENCY -> SafeReplies.emergency(lang);
            case MEDICAL_ADVICE -> SafeReplies.medicalAdvice(lang);
        };
    }

    private static boolean matches(List<Pattern> patterns, String text) {
        for (Pattern p : patterns) {
            if (p.matcher(text).find()) {
                return true;
            }
        }
        return false;
    }

    private static List<Pattern> compile(String... regexes) {
        return java.util.Arrays.stream(regexes).map(r -> Pattern.compile(r, FLAGS)).toList();
    }
}
