package com.raktaveda.admin;

import com.raktaveda.chat.ReportStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/** Request and response shapes for /api/admin. Plain DTOs, never database rows. */
public final class AdminDtos {

    private AdminDtos() {
    }

    /** reason is required when rejecting or suspending, and optional when verifying. Do not put personal data in it. */
    public record StatusChangeDto(@Size(max = 300) String reason) {
    }

    /** The phone numbers are here so the admin can phone the institution to check its licence. */
    public record InstitutionAdminView(UUID id, String name, String type, String licenceNumber, String email,
                                       String publicPhone, String emergencyPhone, String address, String status,
                                       String statusReason, Instant createdAt) {
        @Override
        public String toString() {
            return "InstitutionAdminView[redacted]"; // never print phone numbers or emails by accident
        }
    }

    public record ReportedMessage(String senderType, String body, Instant sentAt) {
        @Override
        public String toString() {
            return "ReportedMessage[redacted]";
        }
    }

    /** message is filled only when one report is opened on its own (and that opening is written to the audit log). */
    public record ReportView(UUID id, long messageId, String reporterType, String reason, String note,
                             String status, Instant createdAt, ReportedMessage message) {
    }

    public record ResolveReportDto(@NotNull ReportStatus status, @Size(max = 300) String note) {
    }

    public record AuditEntry(long id, Instant at, String actorType, UUID actorId, String action,
                             String targetType, UUID targetId, String detail) {
    }
}
