package com.raktaveda.audit;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Writes one row to the audit log. Actor types: USER, INSTITUTION, ADMIN, SYSTEM, ANONYMOUS.
 * Never put a phone number, email, code, token or coordinates in any argument, including
 * "detail". Ids and short action names only.
 */
@Service
public class AuditService {

    private final JdbcTemplate jdbc;

    public AuditService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void record(String actorType, UUID actorId, String action, String targetType, UUID targetId, String detail) {
        String safeDetail = detail == null ? null : detail.substring(0, Math.min(detail.length(), 300));
        jdbc.update("INSERT INTO audit_log (actor_type, actor_id, action, target_type, target_id, detail) VALUES (?, ?, ?, ?, ?, ?)",
                actorType, actorId, action, targetType, targetId, safeDetail);
    }
}
