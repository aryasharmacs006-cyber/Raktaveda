package com.raktaveda.notification;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Development version. It sends nothing anywhere. It only records that a notification of this kind
 * was sent (one row in notification_log), with no personal data: not the token, not the user, not the text.
 * Active unless raktaveda.notifications.provider is set to something else.
 */
@Component
@ConditionalOnProperty(name = "raktaveda.notifications.provider", havingValue = "dev", matchIfMissing = true)
public class DevPushNotifier implements PushNotifier {

    private final JdbcTemplate jdbc;

    public DevPushNotifier(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Result send(String deviceToken, PushMessage message) {
        jdbc.update("INSERT INTO notification_log (kind) VALUES (?)", message.kind());
        return Result.SENT;
    }
}
