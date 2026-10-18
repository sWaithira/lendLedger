package com.lendLedger.backend.loan;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class CsvWriterTest {

    @Test
    void startsWithByteOrderMarkAndUsesCrLf() {
        String csv = new CsvWriter().row("a", "b").build();
        assertThat(csv).isEqualTo("\uFEFFa,b\r\n");
    }

    @Test
    void commasQuotesAndNewlinesAreQuoted() {
        assertThat(CsvWriter.cell("Jane, \"JJ\" Wanjiru")).isEqualTo("\"Jane, \"\"JJ\"\" Wanjiru\"");
        assertThat(CsvWriter.cell("line1\nline2")).isEqualTo("\"line1\nline2\"");
    }

    @Test
    void formulaStartsAreNeutralised() {
        assertThat(CsvWriter.cell("=SUM(1,2)")).isEqualTo("\"'=SUM(1,2)\"");
        assertThat(CsvWriter.cell("=cmd|x")).isEqualTo("'=cmd|x");
        assertThat(CsvWriter.cell("@user")).isEqualTo("'@user");
        assertThat(CsvWriter.cell("+254700")).isEqualTo("'+254700");
        assertThat(CsvWriter.cell("-1")).isEqualTo("'-1");
    }

    @Test
    void negativeNumbersStayNumeric() {
        assertThat(CsvWriter.cell(new BigDecimal("-8000.00"))).isEqualTo("-8000.00");
        assertThat(CsvWriter.cell(-5)).isEqualTo("-5");
    }

    @Test
    void nullBecomesEmptyAndDatesAreIso() {
        assertThat(CsvWriter.cell(null)).isEqualTo("");
        assertThat(CsvWriter.cell(LocalDate.of(2026, 10, 17))).isEqualTo("2026-10-17");
    }

    @Test
    void bigDecimalsNeverUseScientificNotation() {
        assertThat(CsvWriter.cell(new BigDecimal("1E+3"))).isEqualTo("1000");
    }

    @Test
    void plainTextIsUntouched() {
        assertThat(CsvWriter.cell("Peter Otieno")).isEqualTo("Peter Otieno");
    }
}