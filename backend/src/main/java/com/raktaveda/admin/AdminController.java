package com.raktaveda.admin;

import com.raktaveda.admin.AdminDtos.AuditEntry;
import com.raktaveda.admin.AdminDtos.InstitutionAdminView;
import com.raktaveda.admin.AdminDtos.ReportView;
import com.raktaveda.admin.AdminDtos.ResolveReportDto;
import com.raktaveda.admin.AdminDtos.StatusChangeDto;
import com.raktaveda.chat.ReportStatus;
import com.raktaveda.institution.InstitutionStatus;
import com.raktaveda.security.AuthPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Admin only. Admin accounts are created at startup from environment settings, never through this API. */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService admin;

    public AdminController(AdminService admin) {
        this.admin = admin;
    }

    @GetMapping("/institutions")
    public List<InstitutionAdminView> institutions(@RequestParam(required = false) InstitutionStatus status) {
        return admin.listInstitutions(status);
    }

    @PostMapping("/institutions/{id}/verify")
    public InstitutionAdminView verify(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID id,
                                       @Valid @RequestBody(required = false) StatusChangeDto body) {
        return admin.changeStatus(principal.id(), id, InstitutionStatus.VERIFIED, body == null ? null : body.reason());
    }

    @PostMapping("/institutions/{id}/reject")
    public InstitutionAdminView reject(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID id,
                                       @Valid @RequestBody(required = false) StatusChangeDto body) {
        return admin.changeStatus(principal.id(), id, InstitutionStatus.REJECTED, body == null ? null : body.reason());
    }

    @PostMapping("/institutions/{id}/suspend")
    public InstitutionAdminView suspend(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID id,
                                        @Valid @RequestBody(required = false) StatusChangeDto body) {
        return admin.changeStatus(principal.id(), id, InstitutionStatus.SUSPENDED, body == null ? null : body.reason());
    }

    @GetMapping("/reports")
    public List<ReportView> reports(@RequestParam(required = false) ReportStatus status) {
        return admin.listReports(status);
    }

    /** Opening a report shows the one reported message and is written to the audit log. */
    @GetMapping("/reports/{id}")
    public ReportView report(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID id) {
        return admin.getReport(principal.id(), id);
    }

    @PostMapping("/reports/{id}/resolve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resolve(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID id,
                        @Valid @RequestBody ResolveReportDto body) {
        admin.resolveReport(principal.id(), id, body.status(), body.note());
    }

    @GetMapping("/audit")
    public List<AuditEntry> audit(@RequestParam(required = false) Long before,
                                  @RequestParam(required = false) Integer limit) {
        return admin.auditLog(before, limit);
    }
}
