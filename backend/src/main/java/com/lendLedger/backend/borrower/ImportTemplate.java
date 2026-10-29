package com.lendLedger.backend.borrower;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/** The downloadable Excel template: empty Borrowers and Loans sheets plus a short guide. */
final class ImportTemplate {

    private ImportTemplate() { }

    static byte[] build() {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Font bold = wb.createFont();
            bold.setBold(true);
            CellStyle header = wb.createCellStyle();
            header.setFont(bold);
            // Text format keeps the leading zero of phone numbers and long ID numbers.
            CellStyle text = wb.createCellStyle();
            text.setDataFormat(wb.createDataFormat().getFormat("@"));
            CellStyle date = wb.createCellStyle();
            date.setDataFormat(wb.createDataFormat().getFormat("yyyy-mm-dd"));

            Sheet borrowers = wb.createSheet("Borrowers");
            writeHeader(borrowers, header, "Full name", "Phone", "Email", "ID number", "Note");
            borrowers.setDefaultColumnStyle(1, text);
            borrowers.setDefaultColumnStyle(3, text);

            Sheet loans = wb.createSheet("Loans");
            writeHeader(loans, header, "Borrower phone", "Amount", "Issue date", "Term (months)", "Product");
            loans.setDefaultColumnStyle(0, text);
            loans.setDefaultColumnStyle(2, date);

            Sheet guide = wb.createSheet("How to fill in");
            String[] lines = {
                    "One row per borrower on the Borrowers sheet and one row per loan on the Loans sheet. Keep row 1 as it is.",
                    "Phone: a Kenyan number such as 0712345678 or +254712345678.",
                    "A loan finds its borrower by Borrower phone. The borrower can be on the Borrowers sheet or already in LendLedger.",
                    "Amount: a number, for example 3223 or 3223.50.",
                    "Issue date: 2026-10-01 (year-month-day) or 01/10/2026. It cannot be in the future.",
                    "Term (months): a whole number, for example 1.",
                    "Product: the product name as it appears in Settings. Leave it empty if you offer only one product.",
                    "Payments are not imported. Record them in LendLedger after the import.",
                    "Example borrower: Jane Wanjiku | 0712345678 | jane@example.com | 12345678 | Market trader",
                    "Example loan: 0712345678 | 3223 | 2026-10-01 | 1",
            };
            for (int i = 0; i < lines.length; i++) {
                guide.createRow(i).createCell(0).setCellValue(lines[i]);
            }
            guide.setColumnWidth(0, 120 * 256);
            for (Sheet s : new Sheet[] {borrowers, loans}) {
                for (int c = 0; c < 5; c++) s.setColumnWidth(c, 22 * 256);
            }
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void writeHeader(Sheet sheet, CellStyle style, String... names) {
        Row row = sheet.createRow(0);
        for (int i = 0; i < names.length; i++) {
            row.createCell(i).setCellValue(names[i]);
            row.getCell(i).setCellStyle(style);
        }
    }
}