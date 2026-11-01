package com.lendLedger.backend.security;

import java.util.regex.Pattern;

import com.lendLedger.backend.common.ApiException;

import org.springframework.http.HttpStatus;

/** One password rule for every place a password is set. Keep in step with frontend/src/passwordRules.js. */
public final class PasswordPolicy {

    static final int MIN_LENGTH = 8;   // set to 1 to remove the length rule

    private static final Pattern LOWER = Pattern.compile("[a-z]");
    private static final Pattern UPPER = Pattern.compile("[A-Z]");
    private static final Pattern DIGIT = Pattern.compile("[0-9]");
    private static final Pattern SYMBOL = Pattern.compile("[^A-Za-z0-9]");

    private PasswordPolicy() { }

    public static void require(String password, String email) {
        String p = password == null ? "" : password;
        boolean strong = p.length() >= MIN_LENGTH && LOWER.matcher(p).find() && UPPER.matcher(p).find()
                && DIGIT.matcher(p).find() && SYMBOL.matcher(p).find();
        if (!strong) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "weak_password",
                    "Mix an uppercase letter, a lowercase letter, a number and a symbol"
                            + (MIN_LENGTH > 1 ? " (at least " + MIN_LENGTH + " characters)." : "."));
        }
        if (email != null && p.equalsIgnoreCase(email.trim())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "weak_password", "The password cannot be the same as the email.");
        }
    }
}