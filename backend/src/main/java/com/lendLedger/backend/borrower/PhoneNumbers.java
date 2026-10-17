package com.lendLedger.backend.borrower;

/**
 * Turns phone numbers as typed by a person into one comparable form (digits only, with country code).
 * This is what makes the duplicate warning work: "0711 222 333" and "+254 711 222 333" are the same person.
 * Kenyan numbers are recognised; other international numbers are kept as typed (digits only).
 */
final class PhoneNumbers {

    private static final int MIN_DIGITS = 10;
    private static final int MAX_DIGITS = 15;
    private static final String KENYA = "254";

    private PhoneNumbers() { }

    /** @throws IllegalArgumentException if the text cannot be a phone number */
    static String normalize(String raw) {
        if (raw == null) throw new IllegalArgumentException("Phone number is required");
        String trimmed = raw.trim();
        String digits = trimmed.replaceAll("\\D", "");
        boolean international = trimmed.startsWith("+");

        String result;
        if (international) {
            result = digits;
        } else if (digits.startsWith("00")) {
            result = digits.substring(2);
        } else if (digits.startsWith("0") && digits.length() == 10) {
            result = KENYA + digits.substring(1);
        } else if (digits.length() == 9 && (digits.startsWith("7") || digits.startsWith("1"))) {
            result = KENYA + digits;
        } else {
            result = digits;
        }

        boolean validLength = result.length() >= MIN_DIGITS && result.length() <= MAX_DIGITS;
        boolean validKenyan = !result.startsWith(KENYA) || result.length() == 12;
        if (!validLength || !validKenyan) {
            throw new IllegalArgumentException("Enter a valid phone number, for example 0712 345 678 or +254 712 345 678");
        }
        return result;
    }

    /** For partial matching while searching: digits only, local leading zero turned into the country code. */
    static String searchDigits(String raw) {
        if (raw == null) return "";
        String digits = raw.replaceAll("\\D", "");
        if (digits.startsWith("0")) return KENYA + digits.substring(1);
        return digits;
    }
}