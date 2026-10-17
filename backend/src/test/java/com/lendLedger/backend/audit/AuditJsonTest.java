package com.lendLedger.backend.audit;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuditJsonTest {

    @Test
    void emptyBecomesEmptyObject() {
        assertThat(AuditJson.toJson(Map.of())).isEqualTo("{}");
        assertThat(AuditJson.toJson(null)).isEqualTo("{}");
    }

    @Test
    void pairsAreWrittenInOrder() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("changed", "fullName,phone");
        m.put("by", "owner");
        assertThat(AuditJson.toJson(m)).isEqualTo("{\"changed\":\"fullName,phone\",\"by\":\"owner\"}");
    }

    @Test
    void quotesBackslashesAndNewlinesAreEscaped() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("reason", "said \"hi\"\\\nnext");
        assertThat(AuditJson.toJson(m)).isEqualTo("{\"reason\":\"said \\\"hi\\\"\\\\\\nnext\"}");
    }

    @Test
    void nullValueBecomesJsonNull() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("x", null);
        assertThat(AuditJson.toJson(m)).isEqualTo("{\"x\":null}");
    }
}