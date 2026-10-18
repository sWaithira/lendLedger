package com.lendLedger.backend.loan;

import java.math.BigDecimal;
import java.time.temporal.TemporalAccessor;

/**
 * Builds RFC 4180 CSV for Excel: UTF-8 with a byte-order mark (so names with accents open correctly),
 * CRLF line endings, quoting where needed.
 *
 * Text cells that start with = + - @ (or a tab / carriage return) are prefixed with an apostrophe, so a
 * borrower called "=HYPERLINK(...)" cannot run as a formula when someone opens the export (CSV injection).
 * Numbers and dates are written as-is, so negative amounts stay numeric.
 */
final class CsvWriter {

    private final StringBuilder out = new StringBuilder("\uFEFF");

    CsvWriter row(Object... cells) {
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) out.append(',');
            out.append(cell(cells[i]));
        }
        out.append("\r\n");
        return this;
    }

    String build() {
        return out.toString();
    }

    static String cell(Object value) {
        if (value == null) return "";
        if (value instanceof BigDecimal d) return d.toPlainString();
        if (value instanceof Number || value instanceof Boolean || value instanceof TemporalAccessor) {
            return value.toString();
        }
        String text = value.toString();
        if (!text.isEmpty() && isFormulaStart(text.charAt(0))) text = "'" + text;
        boolean needsQuotes = text.indexOf(',') >= 0 || text.indexOf('"') >= 0
                || text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0;
        return needsQuotes ? '"' + text.replace("\"", "\"\"") + '"' : text;
    }

    private static boolean isFormulaStart(char c) {
        return c == '=' || c == '+' || c == '-' || c == '@' || c == '\t' || c == '\r';
    }
}