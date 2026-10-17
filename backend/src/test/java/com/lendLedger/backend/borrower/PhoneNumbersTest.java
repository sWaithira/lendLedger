package com.lendLedger.backend.borrower;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PhoneNumbersTest {

    @Test
    void localFormatBecomesInternational() {
        assertThat(PhoneNumbers.normalize("0711 222 333")).isEqualTo("254711222333");
    }

    @Test
    void plusFormatIsReducedToDigits() {
        assertThat(PhoneNumbers.normalize("+254 711-222-333")).isEqualTo("254711222333");
    }

    @Test
    void nineDigitFormatGetsTheCountryCode() {
        assertThat(PhoneNumbers.normalize("711222333")).isEqualTo("254711222333");
    }

    @Test
    void doubleZeroPrefixIsAccepted() {
        assertThat(PhoneNumbers.normalize("00254711222333")).isEqualTo("254711222333");
    }

    @Test
    void differentWaysOfTypingTheSameNumberMatch() {
        String a = PhoneNumbers.normalize("0711222333");
        String b = PhoneNumbers.normalize("+254711222333");
        String c = PhoneNumbers.normalize("254 711 222 333");
        assertThat(a).isEqualTo(b);
        assertThat(b).isEqualTo(c);
    }

    @Test
    void otherCountriesAreKeptAsDigits() {
        assertThat(PhoneNumbers.normalize("+1 415 555 2671")).isEqualTo("14155552671");
    }

    @Test
    void tooShortIsRejected() {
        assertThatThrownBy(() -> PhoneNumbers.normalize("0711")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void wrongLengthKenyanNumberIsRejected() {
        assertThatThrownBy(() -> PhoneNumbers.normalize("+254 711 222")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void lettersOnlyIsRejected() {
        assertThatThrownBy(() -> PhoneNumbers.normalize("call me")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullIsRejected() {
        assertThatThrownBy(() -> PhoneNumbers.normalize(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void searchTurnsLocalPrefixIntoCountryCode() {
        assertThat(PhoneNumbers.searchDigits("0711")).isEqualTo("254711");
        assertThat(PhoneNumbers.searchDigits("711")).isEqualTo("711");
        assertThat(PhoneNumbers.searchDigits("jane")).isEqualTo("");
    }
}