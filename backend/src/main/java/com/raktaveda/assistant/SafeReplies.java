package com.raktaveda.assistant;

/**
 * Fixed replies written by us, not by the AI. They are used when the question looks like an emergency,
 * asks for medical advice, mentions self-harm, or when the AI's reply fails a safety check.
 *
 * Needs review before real use (medical and legal): a doctor and a native speaker of each language should
 * check the wording. Check the emergency number (112 is the all-India number) and the helpline numbers
 * (Tele-MANAS 14416 / 1800-891-4416 in India) against official sources before launch. Outside India
 * the person is told to use their local number.
 */
final class SafeReplies {

    private SafeReplies() {
    }

    static String emergency(Lang l) {
        return switch (l) {
            case HI -> "यह आपातकाल जैसा लगता है। कृपया अभी अपने स्थानीय आपातकालीन नंबर पर कॉल करें (भारत में 112) और तुरंत नज़दीकी अस्पताल जाएँ। इस सहायक के जवाब का इंतज़ार न करें।";
            case KO -> "응급 상황으로 보입니다. 지금 바로 현지 응급 전화번호로 전화하시고(인도는 112) 가장 가까운 병원으로 가세요. 이 도우미의 답변을 기다리지 마세요.";
            case ZH -> "这听起来像是紧急情况。请立即拨打当地急救电话（印度为 112），并马上前往最近的医院。请不要等待本助手的回复。";
            case ES -> "Esto parece una emergencia. Llame ahora al número de emergencias de su zona (112 en India) y acuda de inmediato al hospital más cercano. No espere la respuesta de este asistente.";
            default -> "This sounds like an emergency. Please call your local emergency number now (112 in India) and go to the nearest hospital immediately. Do not wait for an answer from this assistant.";
        };
    }

    static String medicalAdvice(Lang l) {
        return switch (l) {
            case HI -> "मैं चिकित्सा सलाह, बीमारी की पहचान, या दवा और खुराक की जानकारी नहीं दे सकता। कृपया किसी डॉक्टर या अपने ब्लड बैंक से बात करें। रक्तदान कर सकते हैं या नहीं, इसका अंतिम फ़ैसला वही करते हैं।";
            case KO -> "저는 의학적 조언, 진단, 약이나 복용량에 대한 안내를 드릴 수 없습니다. 의사 또는 혈액원과 상담해 주세요. 헌혈 가능 여부의 최종 결정도 그쪽에서 합니다.";
            case ZH -> "我无法提供医疗建议、诊断疾病，也无法提供用药或剂量方面的建议。请咨询医生或您的血库。能否献血的最终决定也由他们作出。";
            case ES -> "No puedo dar consejos médicos, diagnosticar ni orientar sobre medicamentos o dosis. Consulte a un médico o a su banco de sangre. Ellos también toman la decisión final sobre si puede donar.";
            default -> "I can't give medical advice, diagnose a condition, or advise on medicines or doses. Please talk to a doctor or your blood bank. They also make the final decision on whether you can donate.";
        };
    }

    static String selfHarm(Lang l) {
        return switch (l) {
            case HI -> "आप जो महसूस कर रहे हैं, उसके लिए मुझे सच में दुख है। आपको अभी सहारा मिलना चाहिए। अगर आप खुद को नुकसान पहुँचा सकते हैं, तो कृपया अभी अपने स्थानीय आपातकालीन नंबर पर कॉल करें (भारत में 112) या नज़दीकी अस्पताल जाएँ। भारत में आप Tele-MANAS हेल्पलाइन 14416 या 1800-891-4416 पर भी कॉल कर सकते हैं। दूसरे देशों में अपनी स्थानीय संकट हेल्पलाइन या आपातकालीन सेवाओं से संपर्क करें। किसी भरोसेमंद व्यक्ति को बताएँ कि आपको साथ चाहिए।";
            case KO -> "지금 그렇게 느끼신다니 정말 마음이 아픕니다. 지금 도움을 받으셔야 합니다. 스스로를 해칠 수 있다고 느끼신다면 지금 바로 현지 응급 전화번호로 전화하시거나(인도는 112) 가까운 병원으로 가세요. 인도에서는 Tele-MANAS 상담전화 14416 또는 1800-891-4416으로도 전화하실 수 있습니다. 다른 나라에서는 현지 위기 상담전화나 응급 서비스에 연락하세요. 믿을 수 있는 사람에게 곁에 있어 달라고 말씀해 보세요.";
            case ZH -> "听到您有这样的感受，我很难过。您现在需要得到支持。如果您可能伤害自己，请立即拨打当地急救电话（印度为 112）或前往最近的医院。在印度，您也可以拨打 Tele-MANAS 心理援助热线 14416 或 1800-891-4416。在其他地方，请联系当地的危机求助热线或急救服务。请告诉您信任的人，您需要有人陪伴。";
            case ES -> "Lamento mucho que se sienta así. Merece recibir apoyo ahora mismo. Si cree que podría hacerse daño, llame ya al número de emergencias de su zona (112 en India) o acuda al hospital más cercano. En India también puede llamar a la línea Tele-MANAS: 14416 o 1800-891-4416. En otros lugares, contacte con su línea de ayuda en crisis local o con los servicios de emergencia. Si puede, dígale a una persona de confianza que necesita compañía.";
            default -> "I'm really sorry you're feeling this way. You deserve support right now. If you might hurt yourself, please call your local emergency number now (112 in India) or go to the nearest hospital. In India you can also call the Tele-MANAS helpline at 14416 or 1800-891-4416. Elsewhere, contact your local crisis helpline or emergency services. If you can, tell someone you trust that you need company.";
        };
    }

    /** Used when the AI's reply fails a check. */
    static String cannotAnswer(Lang l) {
        return switch (l) {
            case HI -> "मैं इसका सुरक्षित जवाब नहीं दे सकता। कृपया अपने ब्लड बैंक या डॉक्टर से पूछें। सामान्य जानकारी के लिए ऐप में दिए गए आधिकारिक स्रोत देखें।";
            case KO -> "이 질문에는 안전하게 답변드릴 수 없습니다. 혈액원이나 의사에게 문의해 주세요. 일반 정보는 앱에 안내된 공식 정보 출처를 이용하세요.";
            case ZH -> "我无法安全地回答这个问题。请咨询您的血库或医生。一般信息请查看应用中列出的官方信息来源。";
            case ES -> "No puedo responder a esto de forma segura. Consulte a su banco de sangre o a un médico. Para información general, use las fuentes oficiales indicadas en la aplicación.";
            default -> "I can't answer that safely. Please ask your blood bank or a doctor. For general help, use the official information sources listed in the app.";
        };
    }

    /** Added to every normal AI answer, so the final-decision rule is always stated. */
    static String eligibilityNote(Lang l) {
        return switch (l) {
            case HI -> "ध्यान दें: पात्रता का अंतिम फ़ैसला ब्लड बैंक या डॉक्टर करते हैं।";
            case KO -> "참고: 헌혈 자격의 최종 결정은 혈액원 또는 의사가 합니다.";
            case ZH -> "提示：献血资格的最终决定由血库或医生作出。";
            case ES -> "Nota: el banco de sangre o un médico toma la decisión final sobre la elegibilidad.";
            default -> "Note: the blood bank or a doctor makes the final decision on eligibility.";
        };
    }
}
