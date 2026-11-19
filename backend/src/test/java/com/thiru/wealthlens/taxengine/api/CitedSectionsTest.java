package com.thiru.wealthlens.taxengine.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.taxengine.rules.temporal.Provision;
import org.junit.jupiter.api.Test;

/** The sections a provision reference names (W-236); every reference here is copied from a shipped rule. */
class CitedSectionsTest {

    @Test
    void of_whenOneSectionWithSubdivisions_isTheSection() {
        // Given — SAL-STANDARD-DEDUCTION-NEW-TAX-REGIME's 2025 reference
        String reference = "s.19(1) Table Sl. 2(a)";

        // When / Then
        assertThat(CitedSections.of(reference)).containsExactly("19");
    }

    @Test
    void of_whenAListAndASecondCitation_isEverySectionInOrder() {
        // Given — DED-SCHEDULE-XV's 1961 reference
        String reference = "ss.80C, 80CCC, 80CCD(1), 80CCE; excluded by s.115BAC(2)(i)";

        // When / Then
        assertThat(CitedSections.of(reference)).containsExactly("80C", "80CCC", "80CCD", "80CCE", "115BAC");
    }

    @Test
    void of_whenOneSectionThenItsSubsections_isOnlyTheSection() {
        // Given — DED-DEPOSIT-INTEREST-SENIOR's 2025 reference: "(2)(b)" and "(5)" are sub-sections of 153
        String reference = "s.153(1)(b), (2)(b), (5); s.2(100)";

        // When / Then
        assertThat(CitedSections.of(reference)).containsExactly("153", "2");
    }

    @Test
    void of_whenASchedulePlusARule_isOnlyTheSection() {
        // Given — SAL-HOUSE-RENT-ALLOWANCE's 2025 reference: a Schedule's serial number and a rule are not sections
        String reference = "Sch. III Table Sl. 11, with Income-tax Rules, 2026 r.279; excluded by s.202(2)(a)(i)";

        // When / Then
        assertThat(CitedSections.of(reference)).containsExactly("202");
    }

    @Test
    void actOf_whenTheProvisionIsAbsentOrInAnotherInstrument_isEmpty() {
        // Given / When / Then
        assertThat(CitedSections.actOf(Provision.present("Income-tax Act, 1961", "s.16(ia)"))).contains("1961");
        assertThat(CitedSections.actOf(Provision.absent("Income-tax Act, 1961"))).isEmpty();
        assertThat(CitedSections.actOf(Provision.present("Finance Act, 2026", "s.3(4)(b)"))).isEmpty();
    }
}
