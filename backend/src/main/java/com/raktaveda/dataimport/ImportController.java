package com.raktaveda.dataimport;

import com.raktaveda.common.ApiException;
import com.raktaveda.dataimport.ImportDtos.ImportReport;
import com.raktaveda.security.AuthPrincipal;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Admin only. Upload a CSV of verified official data. dryRun defaults to TRUE, so nothing is saved by accident. */
@RestController
@RequestMapping("/api/admin/import")
@PreAuthorize("hasRole('ADMIN')")
public class ImportController {

    private final InstitutionCsvImportService importer;

    public ImportController(InstitutionCsvImportService importer) {
        this.importer = importer;
    }

    /**
     * Answers 200 for a dry run, 201 when rows were imported, and 422 when nothing was imported because
     * some rows are invalid. The body is the same report in every case.
     */
    @PostMapping(path = "/institutions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImportReport> importInstitutions(@AuthenticationPrincipal AuthPrincipal principal,
                                                           @RequestParam("file") MultipartFile file,
                                                           @RequestParam(defaultValue = "true") boolean dryRun) {
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "IMPORT_MALFORMED", "error.import.malformed");
        }
        ImportReport report = importer.run(principal.id(), bytes, dryRun);
        HttpStatus status = dryRun ? HttpStatus.OK
                : report.invalidRows() > 0 ? HttpStatus.UNPROCESSABLE_ENTITY : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(report);
    }
}
