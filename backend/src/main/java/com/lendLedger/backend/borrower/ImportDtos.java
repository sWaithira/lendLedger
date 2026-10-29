package com.lendLedger.backend.borrower;

import java.util.List;

public final class ImportDtos {

    private ImportDtos() { }

    /** status is NEW, CREATED, EXISTING, SKIPPED or ERROR. */
    public record RowResult(String sheet, int row, String label, String status, String message) { }

    public record Summary(int borrowersNew, int borrowersExisting, int borrowersSkipped,
                          int loansNew, int loansSkipped, int errors) { }

    public record Preview(String fileName, boolean alreadyImported, Summary summary, List<RowResult> rows) { }

    public record Report(Summary summary, List<RowResult> rows) { }
}