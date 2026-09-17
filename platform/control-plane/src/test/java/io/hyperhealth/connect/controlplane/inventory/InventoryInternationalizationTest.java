package io.hyperhealth.connect.controlplane.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

class InventoryInternationalizationTest {

    @Test
    @ResourceLock("jvm-default-locale")
    void preservesUnicodeCodePointsWithoutLocaleDependentOrImplicitNormalization() {
        String decomposed = "Cafe\u0301 — Αθήνα / Київ / مستشفى / 東京 / 👩🏽‍⚕️";
        assertThat(Normalizer.isNormalized(decomposed, Normalizer.Form.NFC)).isFalse();
        Locale previous = Locale.getDefault();
        try {
            for (Locale locale : List.of(Locale.ROOT, Locale.ITALIAN, Locale.ENGLISH, Locale.forLanguageTag("tr-TR"))) {
                Locale.setDefault(locale);
                String accepted = ScopedInventoryService.normalizeDisplayName(decomposed);
                assertThat(accepted).isEqualTo(decomposed);
                assertThat(accepted.codePoints().toArray()).containsExactly(decomposed.codePoints().toArray());
                assertThat(Normalizer.isNormalized(accepted, Normalizer.Form.NFC)).isFalse();
            }
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void countsSupplementaryCharactersAsCodePointsInsteadOfUtf16CodeUnits() {
        String exactlyAtLimit = "😀".repeat(256);
        String overLimit = "😀".repeat(257);

        assertThat(exactlyAtLimit).hasSize(512);
        assertThat(ScopedInventoryService.normalizeDisplayName(exactlyAtLimit)).isEqualTo(exactlyAtLimit);
        assertThatThrownBy(() -> ScopedInventoryService.normalizeDisplayName(overLimit))
                .isInstanceOf(InvalidInventoryRequestException.class)
                .hasMessage("displayName must contain between 1 and 256 characters");
    }

    @Test
    void rejectsMalformedUtf16AndControlCharactersBeforePersistence() {
        assertThatThrownBy(() -> ScopedInventoryService.normalizeDisplayName("orphan-high-\uD83D"))
                .isInstanceOf(InvalidInventoryRequestException.class)
                .hasMessage("displayName contains malformed Unicode");
        assertThatThrownBy(() -> ScopedInventoryService.normalizeDisplayName("orphan-low-\uDC00"))
                .isInstanceOf(InvalidInventoryRequestException.class)
                .hasMessage("displayName contains malformed Unicode");
        assertThatThrownBy(() -> ScopedInventoryService.normalizeDisplayName("line\nbreak"))
                .isInstanceOf(InvalidInventoryRequestException.class)
                .hasMessage("displayName contains a disallowed control character");
    }

    @Test
    void trimsOnlyBoundaryWhitespaceAndPreservesInternalUnicodeWhitespace() {
        String value = "  Ospedale\u00A0San\u2003Luca  ";

        assertThat(ScopedInventoryService.normalizeDisplayName(value))
                .isEqualTo("Ospedale\u00A0San\u2003Luca");
    }
}
