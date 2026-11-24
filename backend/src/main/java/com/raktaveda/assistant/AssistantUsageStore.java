package com.raktaveda.assistant;

import com.raktaveda.audit.AuditService;
import com.raktaveda.security.AuthPrincipal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Keeps the daily question count in the database table assistant_usage (one row per question: account type,
 * account id, day, time). The question and the answer are never stored. A short note is also written to the
 * audit log (time, account type, account id, ASSISTANT_QUESTION, question length).
 *
 * The count and the new rows are written in one transaction behind a per-account lock, so two requests sent at
 * the same moment cannot both slip past the limit.
 *
 * Needs review before real use: "today" is the UTC day (it changes at 05:30 India time). A question that
 * ends in a safe fixed reply or an AI error still counts. Rows are not deleted automatically, and they are
 * not removed when an account is deleted (they hold only the random account id).
 */
@Component
public class AssistantUsageStore {

    static final String CATEGORY = "ASSISTANT_QUESTION";

    public record Usage(boolean allowed, int remaining) {
    }

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final AuditService audit;

    public AssistantUsageStore(JdbcTemplate jdbc, TransactionTemplate tx, AuditService audit) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.audit = audit;
    }

    /** If the account is under its daily limit, records this question and returns how many are left; otherwise records nothing. */
    public Usage tryConsume(AuthPrincipal principal, int questionLength, int dailyLimit) {
        return tx.execute(status -> {
            jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtext(?)) AS locked", "assistant:" + principal.id());
            Integer used = jdbc.queryForObject(
                    "SELECT count(*) FROM assistant_usage WHERE account_id = ? AND asked_on = (now() AT TIME ZONE 'UTC')::date",
                    Integer.class, principal.id());
            int usedToday = used == null ? 0 : used;
            if (usedToday >= dailyLimit) {
                return new Usage(false, 0);
            }
            jdbc.update("INSERT INTO assistant_usage (account_type, account_id) VALUES (?, ?)",
                    principal.type().name(), principal.id());
            audit.record(principal.type().name(), principal.id(), CATEGORY, null, null, "length=" + questionLength);
            return new Usage(true, dailyLimit - usedToday - 1);
        });
    }
}
