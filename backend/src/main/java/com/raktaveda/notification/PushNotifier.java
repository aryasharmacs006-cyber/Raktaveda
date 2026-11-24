package com.raktaveda.notification;

import java.util.Map;

/**
 * Sends one push notification to one phone. This is the place where Firebase Cloud Messaging (FCM,
 * Google's free push service) plugs in.
 *
 * To use the real thing later: add the Firebase Admin library, write a class that implements this
 * interface, mark it @Component @ConditionalOnProperty(name = "raktaveda.notifications.provider",
 * havingValue = "fcm"), and set raktaveda.notifications.provider=fcm. Nothing else changes.
 *
 * Rules for every implementation:
 * - Never log the device token, the message or anything about the person.
 * - Send only message KEYS (like "notification.request.new.title"). The phone app turns the key into
 *   text in the user's language. The backend never sends display text.
 * - Return INVALID_TOKEN when the service says the token is dead, so we can delete it.
 */
public interface PushNotifier {

    enum Result { SENT, INVALID_TOKEN, FAILED }

    /** data holds ids only (for example the request id), never names, phone numbers or places. */
    record PushMessage(String kind, String titleKey, String bodyKey, Map<String, String> data) {
    }

    Result send(String deviceToken, PushMessage message);
}
