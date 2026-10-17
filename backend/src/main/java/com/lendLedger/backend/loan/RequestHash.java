package com.lendLedger.backend.loan;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * A fingerprint of a request, stored with its idempotency key. Same key and same fingerprint means a retry
 * of the same request; same key and a different fingerprint means the key was reused for something else.
 * Each part is length-prefixed, so ("a|b","c") and ("a","b|c") can never produce the same fingerprint.
 */
final class RequestHash {

    private RequestHash() { }

    static String of(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            String value = part == null ? "" : part;
            sb.append(value.length()).append(':').append(value).append(';');
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(sb.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available on the JVM", e);
        }
    }
}