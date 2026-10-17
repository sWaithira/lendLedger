package com.lendLedger.backend.loan;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RequestHashTest {

    @Test
    void sameInputGivesSameHash() {
        assertThat(RequestHash.of("loan-1", "5000.00", "2026-10-17", "CASH", ""))
                .isEqualTo(RequestHash.of("loan-1", "5000.00", "2026-10-17", "CASH", ""));
    }

    @Test
    void anyChangedPartChangesTheHash() {
        String base = RequestHash.of("loan-1", "5000.00", "2026-10-17", "CASH", "");
        assertThat(RequestHash.of("loan-1", "5000.01", "2026-10-17", "CASH", "")).isNotEqualTo(base);
        assertThat(RequestHash.of("loan-2", "5000.00", "2026-10-17", "CASH", "")).isNotEqualTo(base);
        assertThat(RequestHash.of("loan-1", "5000.00", "2026-10-18", "CASH", "")).isNotEqualTo(base);
        assertThat(RequestHash.of("loan-1", "5000.00", "2026-10-17", "BANK", "")).isNotEqualTo(base);
        assertThat(RequestHash.of("loan-1", "5000.00", "2026-10-17", "CASH", "ref")).isNotEqualTo(base);
    }

    @Test
    void partBoundariesCannotBeFaked() {
        assertThat(RequestHash.of("a|b", "c")).isNotEqualTo(RequestHash.of("a", "b|c"));
    }

    @Test
    void nullAndEmptyAreTheSame() {
        assertThat(RequestHash.of("x", null)).isEqualTo(RequestHash.of("x", ""));
    }

    @Test
    void hashIs64HexCharacters() {
        assertThat(RequestHash.of("x").length()).isEqualTo(64);
    }
}