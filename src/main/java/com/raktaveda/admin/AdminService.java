package com.raktaveda.admin;

import com.raktaveda.admin.AdminDtos.AuditEntry;
import com.raktaveda.admin.AdminDtos.InstitutionAdminView;
import com.raktaveda.admin.AdminDtos.ReportView;
import com.raktaveda.admin.AdminDtos.ReportedMessage;
import com.raktaveda.audit.AuditService;
import com.raktaveda.chat.ChatService;
import com.raktaveda.chat.ReportStatus;
import com.raktaveda.common.ApiException;
import com.raktaveda.common.SqlUtil;
import com.raktaveda.crypto.CryptoService;
import com.raktaveda.institution.InstitutionRegistrationService;
import com.raktaveda.institution.InstitutionStatus;
import com.raktaveda.security.PrincipalType;
import com.raktaveda.security.TokenService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Work only an admin can do. Every action is written to the audit log with the admin's id.
 * Needs review before real use: how an admin checks a licence (the phone numbers are shown for that),
 * and who may read reported messages.
 */
@Service
public class AdminService {

    /** Allowed moves: target status -> statuses it may come from. */
    private static final Map<InstitutionStatus, List<InstitutionStatus>> ALLOWED_FROM = Map.of(
            InstitutionStatus.VERIFIED, List.of(InstitutionStatus.PENDING, InstitutionStatus.SUSPENDED),
            InstitutionStatus.REJECTED, List.of(InstitutionStatus.PENDING),
            InstitutionStatus.SUSPENDED, List.of(InstitutionStatus.VERIFIED));

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final CryptoService crypto;
    private final TokenService tokens;
    private final AuditService audit;

    public AdminService(JdbcTemplate jdbc, TransactionTemplate tx, CryptoService crypto, TokenService tokens,
                        AuditService audit) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.crypto = crypto;
        this.tokens = tokens;
        this.audit = audit;
    }

    // ---- institutions ----

    public List<InstitutionAdminView> listInstitutions(InstitutionStatus status) {
        String value = status == null ? null : status.name();
        return jdbc.query(INSTITUTION_SELECT + "WHERE (?::text IS NULL OR status = ?) ORDER BY created_at DESC LIMIT 100",
                institutionMapper(), value, value);
    }

    public InstitutionAdminView changeStatus(UUID adminId, UUID institutionId, InstitutionStatus target, String reason) {
        List<InstitutionStatus> from = ALLOWED_FROM.get(target);
        if (from == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "error.validation.failed");
        }
        boolean needsReason = target == InstitutionStatus.REJECTED || target == InstitutionStatus.SUSPENDED;
        String cleanReason = reason == null || reason.isBlank() ? null : reason.strip();
        if (needsReason && cleanReason == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "REASON_REQUIRED", "error.admin.reason_required");
        }

        tx.executeWithoutResult(t -> {
            List<Object> args = new ArrayList<>();
            String sql;
            if (target == InstitutionStatus.VERIFIED) {
                sql = "UPDATE institutions SET status = 'VERIFIED', status_reason = NULL, verified_at = now(), verified_by = ? ";
                args.add(adminId);
            } else {
                sql = "UPDATE institutions SET status = ?, status_reason = ? ";
                args.add(target.name());
                args.add(cleanReason);
            }
            sql += "WHERE id = ? AND status IN (" + SqlUtil.placeholders(from.size()) + ") RETURNING id";
            args.add(institutionId);
            from.forEach(s -> args.add(s.name()));
            List<UUID> changed = jdbc.query(sql, (rs, n) -> rs.getObject(1, UUID.class), args.toArray());
            if (changed.isEmpty()) {
                Integer exists = jdbc.queryForObject("SELECT count(*) FROM institutions WHERE id = ?", Integer.class, institutionId);
                if (exists == null || exists == 0) {
                    throw new ApiException(HttpStatus.NOT_FOUND, "INSTITUTION_NOT_FOUND", "error.admin.institution_not_found");
                }
                throw new ApiException(HttpStatus.CONFLICT, "INVALID_STATUS_CHANGE", "error.admin.invalid_status_change");
            }
            if (target != InstitutionStatus.VERIFIED) {
                // A rejected or suspended institution stops working at once: logins end and open requests are cancelled.
                jdbc.update("""
                        UPDATE blood_requests SET status = 'CANCELLED', closed_at = now(), location = NULL
                        WHERE requester_type = 'INSTITUTION' AND requester_id = ? AND status = 'OPEN'
                        """, institutionId);
            }
            audit.record("ADMIN", adminId, "INSTITUTION_" + target.name(), "INSTITUTION", institutionId, cleanReason);
        });
        if (target != InstitutionStatus.VERIFIED) {
            tokens.revokeAllFor(PrincipalType.INSTITUTION, institutionId);
        }
        return jdbc.query(INSTITUTION_SELECT + "WHERE id = ?", institutionMapper(), institutionId).get(0);
    }

    // ---- chat reports ----

    public List<ReportView> listReports(ReportStatus status) {
        String value = status == null ? null : status.name();
        return jdbc.query("""
                SELECT id, message_id, reporter_type, reason, note, status, created_at FROM chat_reports
                WHERE (?::text IS NULL OR status = ?) ORDER BY created_at DESC LIMIT 100
                """, (rs, n) -> new ReportView(rs.getObject("id", UUID.class), rs.getLong("message_id"),
                rs.getString("reporter_type"), rs.getString("reason"), rs.getString("note"), rs.getString("status"),
                rs.getTimestamp("created_at").toInstant(), null), value, value);
    }

    /**
     * Opens ONE report together with the ONE reported message. The rest of the conversation is never
     * shown. Needs review before real use (privacy). Opening it is written to the audit log.
     */
    public ReportView getReport(UUID adminId, UUID reportId) {
        List<ReportView> rows = jdbc.query("""
                SELECT r.id, r.message_id, r.reporter_type, r.reason, r.note, r.status, r.created_at,
                       m.sender_type, m.body_enc, m.sent_at
                FROM chat_reports r JOIN chat_messages m ON m.id = r.message_id
                WHERE r.id = ?
                """, (rs, n) -> new ReportView(rs.getObject("id", UUID.class), rs.getLong("message_id"),
                rs.getString("reporter_type"), rs.getString("reason"), rs.getString("note"), rs.getString("status"),
                rs.getTimestamp("created_at").toInstant(),
                new ReportedMessage(rs.getString("sender_type"),
                        crypto.decrypt(rs.getString("body_enc"), ChatService.BODY_CONTEXT),
                        rs.getTimestamp("sent_at").toInstant())), reportId);
        if (rows.isEmpty()) {
            throw reportNotFound();
        }
        audit.record("ADMIN", adminId, "REPORTED_MESSAGE_VIEWED", "REPORT", reportId, null);
        return rows.get(0);
    }

    public void resolveReport(UUID adminId, UUID reportId, ReportStatus outcome, String note) {
        if (outcome == ReportStatus.OPEN) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "error.validation.failed");
        }
        List<UUID> changed = jdbc.query("""
                UPDATE chat_reports SET status = ?, resolved_at = now(), resolved_by = ?, resolution_note = ?
                WHERE id = ? AND status = 'OPEN' RETURNING id
                """, (rs, n) -> rs.getObject(1, UUID.class), outcome.name(), adminId, note, reportId);
        if (changed.isEmpty()) {
            Integer exists = jdbc.queryForObject("SELECT count(*) FROM chat_reports WHERE id = ?", Integer.class, reportId);
            if (exists == null || exists == 0) {
                throw reportNotFound();
            }
            throw new ApiException(HttpStatus.CONFLICT, "REPORT_ALREADY_RESOLVED", "error.admin.report_already_resolved");
        }
        audit.record("ADMIN", adminId, "REPORT_" + outcome.name(), "REPORT", reportId, null);
    }

    // ---- audit log ----

    /** Newest first. Pass "before" (an entry id) to read older entries. */
    public List<AuditEntry> auditLog(Long before, Integer limit) {
        int size = Math.min(200, Math.max(1, limit == null ? 100 : limit));
        long beforeId = before == null ? Long.MAX_VALUE : before;
        return jdbc.query("""
                SELECT id, at, actor_type, actor_id, action, target_type, target_id, detail
                FROM audit_log WHERE id < ? ORDER BY id DESC LIMIT ?
                """, (rs, n) -> new AuditEntry(rs.getLong("id"), rs.getTimestamp("at").toInstant(),
                rs.getString("actor_type"), rs.getObject("actor_id", UUID.class), rs.getString("action"),
                rs.getString("target_type"), rs.getObject("target_id", UUID.class), rs.getString("detail")),
                beforeId, size);
    }

    // ---- helpers ----

    private static final String INSTITUTION_SELECT = """
            SELECT id, name, type, licence_number, email, public_phone_enc, emergency_phone_enc, address,
                   status, status_reason, created_at
            FROM institutions\s""";

    private RowMapper<InstitutionAdminView> institutionMapper() {
        return (rs, n) -> new InstitutionAdminView(
                rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("type"),
                rs.getString("licence_number"), rs.getString("email"),
                crypto.decrypt(rs.getString("public_phone_enc"), InstitutionRegistrationService.PUBLIC_PHONE_CONTEXT),
                crypto.decrypt(rs.getString("emergency_phone_enc"), InstitutionRegistrationService.EMERGENCY_PHONE_CONTEXT),
                rs.getString("address"), rs.getString("status"), rs.getString("status_reason"),
                rs.getTimestamp("created_at").toInstant());
    }

    private static ApiException reportNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "REPORT_NOT_FOUND", "error.admin.report_not_found");
    }
}
