package com.raktaveda.dataimport;

import com.raktaveda.audit.AuditService;
import com.raktaveda.common.ApiException;
import com.raktaveda.crypto.CryptoService;
import com.raktaveda.dataimport.ImportDtos.ImportReport;
import com.raktaveda.dataimport.ImportDtos.RowResult;
import com.raktaveda.dataimport.InstitutionRowValidator.ParsedRow;
import com.raktaveda.institution.InstitutionRegistrationService;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Loads hospitals and blood banks from a CSV file of VERIFIED OFFICIAL data (for example, an official
 * government list). Columns: name, type, licence_number, public_phone, emergency_phone, latitude, longitude
 * and optionally address.
 *
 * - Dry run: checks everything and reports, but saves nothing. The API makes dry run the default.
 * - Validation: every row is checked (see InstitutionRowValidator).
 * - Duplicates: a row is skipped as a duplicate if its licence number is already known (in the file or in
 *   the database), or if an institution with the same name already exists within 100 metres.
 * - All or nothing for errors: if ANY row is invalid, nothing is imported. Duplicates alone are just skipped.
 *
 * Imported institutions are listings with no login (no email or password), marked VERIFIED because the
 * admin vouches for the file. Needs review before real use: who may import, and how a real institution
 * later "claims" its listing. The admin must only upload data from an official source.
 */
@Service
public class InstitutionCsvImportService {

    public static final int MAX_BYTES = 1_048_576;
    public static final int MAX_ROWS = 2000;

    private static final Set<String> REQUIRED = Set.of("name", "type", "licence_number", "public_phone",
            "emergency_phone", "latitude", "longitude");
    private static final Set<String> ALLOWED = new HashSet<>(REQUIRED);

    static {
        ALLOWED.add("address");
    }

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final CryptoService crypto;
    private final AuditService audit;

    public InstitutionCsvImportService(JdbcTemplate jdbc, TransactionTemplate tx, CryptoService crypto, AuditService audit) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.crypto = crypto;
        this.audit = audit;
    }

    /** adminId is null when the system itself loads the test data. */
    public ImportReport run(UUID adminId, byte[] bytes, boolean dryRun) {
        if (bytes == null || bytes.length == 0) {
            throw bad("IMPORT_EMPTY", "error.import.empty");
        }
        if (bytes.length > MAX_BYTES) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "IMPORT_TOO_LARGE", "error.import.too_large");
        }
        List<List<String>> rows;
        try {
            rows = CsvParser.parse(decode(bytes));
        } catch (IllegalArgumentException e) {
            throw bad("IMPORT_MALFORMED", "error.import.malformed");
        }
        if (rows.size() < 2) {
            throw bad("IMPORT_EMPTY", "error.import.empty");
        }
        List<String> header = rows.get(0).stream().map(h -> h.strip().toLowerCase(Locale.ROOT)).toList();
        if (!header.containsAll(REQUIRED) || !ALLOWED.containsAll(header) || new HashSet<>(header).size() != header.size()) {
            throw bad("IMPORT_HEADER_INVALID", "error.import.header_invalid");
        }
        if (rows.size() - 1 > MAX_ROWS) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "IMPORT_TOO_LARGE", "error.import.too_large");
        }

        List<RowResult> results = new ArrayList<>();
        List<ParsedRow> toInsert = new ArrayList<>();
        Set<String> seenLicences = new HashSet<>();
        Set<String> seenPlaces = new HashSet<>();
        int invalid = 0;
        int duplicates = 0;

        for (int i = 1; i < rows.size(); i++) {
            int rowNumber = i + 1;
            List<String> cells = rows.get(i);
            if (cells.size() != header.size()) {
                results.add(new RowResult(rowNumber, "INVALID", List.of("import.row.column_count")));
                invalid++;
                continue;
            }
            Map<String, String> byName = new HashMap<>();
            for (int c = 0; c < header.size(); c++) {
                byName.put(header.get(c), cells.get(c));
            }
            InstitutionRowValidator.Result checked = InstitutionRowValidator.validate(byName);
            if (!checked.valid()) {
                results.add(new RowResult(rowNumber, "INVALID", checked.errors()));
                invalid++;
                continue;
            }
            ParsedRow row = checked.row();
            String licenceKey = row.licenceNumber().toUpperCase(Locale.ROOT);
            String placeKey = row.name().toLowerCase(Locale.ROOT) + "|" + Math.round(row.latitude() * 1000) + "," + Math.round(row.longitude() * 1000);
            if (!seenLicences.add(licenceKey) | !seenPlaces.add(placeKey)) {
                results.add(new RowResult(rowNumber, "DUPLICATE", List.of("import.row.duplicate_in_file")));
                duplicates++;
            } else if (existsAlready(row)) {
                results.add(new RowResult(rowNumber, "DUPLICATE", List.of("import.row.duplicate_existing")));
                duplicates++;
            } else {
                results.add(new RowResult(rowNumber, "NEW", List.of()));
                toInsert.add(row);
            }
        }

        int total = rows.size() - 1;
        int newRows = toInsert.size();
        String outcome;
        int imported = 0;
        if (dryRun) {
            outcome = "import.outcome.dry_run";
        } else if (invalid > 0) {
            outcome = "import.outcome.blocked_by_invalid_rows";
        } else {
            insertAll(adminId, toInsert);
            imported = newRows;
            outcome = "import.outcome.imported";
            results = results.stream()
                    .map(r -> r.status().equals("NEW") ? new RowResult(r.row(), "IMPORTED", r.errors()) : r)
                    .toList();
        }
        // The audit row holds counts only, never file contents.
        audit.record(adminId == null ? "SYSTEM" : "ADMIN", adminId, dryRun ? "IMPORT_DRY_RUN" : "INSTITUTIONS_IMPORTED",
                "INSTITUTION", null, "new=" + newRows + " duplicate=" + duplicates + " invalid=" + invalid + " imported=" + imported);
        return new ImportReport(dryRun, outcome, total, newRows, duplicates, invalid, imported, results);
    }

    // ---- helpers ----

    private boolean existsAlready(ParsedRow row) {
        Integer found = jdbc.queryForObject("""
                SELECT count(*) FROM institutions
                WHERE upper(licence_number) = upper(?)
                   OR (lower(name) = lower(?) AND ST_DWithin(location, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography, 100))
                """, Integer.class, row.licenceNumber(), row.name(), row.longitude(), row.latitude());
        return found != null && found > 0;
    }

    private void insertAll(UUID adminId, List<ParsedRow> rows) {
        try {
            tx.executeWithoutResult(t -> {
                for (ParsedRow r : rows) {
                    // PostGIS point order is (longitude, latitude).
                    jdbc.update("""
                            INSERT INTO institutions (name, type, licence_number, public_phone_enc, emergency_phone_enc,
                                                      address, location, status, source, verified_at, verified_by)
                            VALUES (?, ?, ?, ?, ?, ?, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography,
                                    'VERIFIED', 'IMPORTED', now(), ?)
                            """, r.name(), r.type().name(), r.licenceNumber(),
                            crypto.encrypt(r.publicPhone(), InstitutionRegistrationService.PUBLIC_PHONE_CONTEXT),
                            crypto.encrypt(r.emergencyPhone(), InstitutionRegistrationService.EMERGENCY_PHONE_CONTEXT),
                            r.address(), r.longitude(), r.latitude(), adminId);
                }
            });
        } catch (DuplicateKeyException e) {
            // Someone registered the same licence between our check and the insert. Nothing was saved.
            throw new ApiException(HttpStatus.CONFLICT, "IMPORT_CONFLICT", "error.import.conflict");
        }
    }

    /** Strict UTF-8: a file in another encoding is refused instead of being silently garbled. */
    private static String decode(byte[] bytes) {
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            return decoder.decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            throw bad("IMPORT_ENCODING", "error.import.encoding");
        }
    }

    private static ApiException bad(String code, String key) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, key);
    }
}
