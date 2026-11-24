package com.raktaveda.dataimport;

import java.util.List;

public final class ImportDtos {

    private ImportDtos() {
    }

    /**
     * row counts like a spreadsheet: the header is row 1, the first data row is row 2.
     * status is NEW (would be imported), IMPORTED, DUPLICATE (skipped) or INVALID.
     * errors are message keys only. The file's own text is never repeated here.
     */
    public record RowResult(int row, String status, List<String> errors) {
    }

    /**
     * outcomeKey is one of import.outcome.dry_run, import.outcome.imported or
     * import.outcome.blocked_by_invalid_rows (nothing is imported if ANY row is invalid).
     */
    public record ImportReport(boolean dryRun, String outcomeKey, int totalRows, int newRows, int duplicateRows,
                               int invalidRows, int importedRows, List<RowResult> rows) {
    }
}
