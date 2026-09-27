package com.thiru.wealthlens.taxengine.rules.pack;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.cap.CapContext;
import com.thiru.wealthlens.taxengine.rules.cap.CapResult;
import com.thiru.wealthlens.taxengine.rules.cap.Fixed;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.registry.LegalStatus;
import com.thiru.wealthlens.taxengine.rules.registry.Provenance;
import com.thiru.wealthlens.taxengine.rules.registry.PublicationStatus;
import com.thiru.wealthlens.taxengine.rules.registry.VerificationLevel;
import com.thiru.wealthlens.taxengine.rules.temporal.AmendmentRef;
import com.thiru.wealthlens.taxengine.rules.temporal.Provision;
import com.thiru.wealthlens.taxengine.rules.temporal.RuleVersion;
import com.thiru.wealthlens.taxengine.rules.temporal.TemporalAxis;
import com.thiru.wealthlens.taxengine.rules.temporal.TemporalContext;
import com.thiru.wealthlens.taxengine.rules.temporal.Validity;
import com.thiru.wealthlens.taxengine.rules.temporal.proration.CalendarMonths;
import com.thiru.wealthlens.taxengine.rules.temporal.proration.Days;
import com.thiru.wealthlens.taxengine.rules.temporal.proration.ProrationBasis;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Applying a rule that changed inside the year (D-098), each {@link MidYearCombination} branch in turn. The
 * fixture rule is ₹12,000 until 30 Sep 2025 and ₹24,000 from 1 Oct 2025 — not law. In TY 2025-26, 1 Apr –
 * 30 Sep is 30 + 31 + 30 + 31 + 31 + 30 = 183 days and 1 Oct – 31 Mar is 31 + 30 + 31 + 31 + 28 + 31 = 182.
 */
class TemporallyApportionedCapTest {

    private static final RuleId RULE = new RuleId("FIXTURE-CHANGES-IN-OCTOBER");
    private static final TemporalContext TY_2025 =
            TemporalContext.forTaxYear(TaxYearId.of(2025), LocalDate.of(2026, 7, 31));
    private static final CapContext NOTHING_READ = new CapContext(Map.of());

    private static Provenance provenance() {
        return new Provenance(URI.create("https://egazette.gov.in/WriteReadData/2025/265620.pdf"), "fixture",
                LocalDate.of(2026, 9, 27), VerificationLevel.SOURCE_CHECKED, null);
    }

    private static RuleVersion version(Validity validity, String amount) {
        return new RuleVersion(validity, new RegimeApplicability.AllRegimes(), new Fixed(Money.of(amount)),
                Optional.empty(), Optional.empty(), Optional.empty(), List.of(),
                Provision.present("Income-tax Act, 1961", "s.16"), Provision.present("Income-tax Act, 2025", "s.19"),
                new AmendmentRef("fixture instrument", "fixture gazette", LocalDate.of(2025, 3, 31),
                        LocalDate.of(2025, 4, 1), false, URI.create("https://egazette.gov.in/"),
                        VerificationLevel.SOURCE_CHECKED),
                LegalStatus.IN_FORCE, PublicationStatus.PUBLISHED, provenance());
    }

    private static List<RuleVersion> changingOnFirstOctober() {
        return List.of(
                version(new Validity.Between(LocalDate.of(2025, 4, 1), Optional.of(LocalDate.of(2025, 9, 30))),
                        "12000"),
                version(Validity.Between.fromOnwards(LocalDate.of(2025, 10, 1)), "24000"));
    }

    private static RuleRow changing(MidYearCombination combination, Optional<ProrationBasis> basis,
            RuleId combinationRule) {
        return new RuleRow(RULE, TemporalAxis.TAX_YEAR, changingOnFirstOctober(), combination, combinationRule,
                basis, provenance());
    }

    @Test
    void apply_whenTheRuleDidNotChangeInTheYear_appliesItsOneVersion() {
        // Given
        RuleRow unchanged = new RuleRow(RULE, TemporalAxis.TAX_YEAR,
                List.of(version(Validity.Between.fromOnwards(LocalDate.of(2025, 4, 1)), "12000")),
                MidYearCombination.UNDETERMINED, null, Optional.empty(), provenance());

        // When
        CapResult result = TemporallyApportionedCap.apply(unchanged, TY_2025, NOTHING_READ);

        // Then — UNDETERMINED only matters in a year the rule moved
        assertThat(result.value()).isEqualTo(Money.of("12000"));
    }

    @Test
    void apply_whenProratedByDays_sumsEachVersionsShareOfTheYear() {
        // When
        CapResult result = TemporallyApportionedCap.apply(
                changing(MidYearCombination.PRORATE_PER_SEGMENT, Optional.of(new Days()), null), TY_2025,
                NOTHING_READ);

        // Then — 12,000 × 183/365 = 6,016.44; 24,000 × 182/365 = 11,967.12; together 17,983.56
        assertThat(result.value()).isEqualTo(Money.of("17983.56"));
    }

    @Test
    void apply_whenProratedByCalendarMonths_sumsSixMonthsOfEach() {
        // When
        CapResult result = TemporallyApportionedCap.apply(
                changing(MidYearCombination.PRORATE_PER_SEGMENT, Optional.of(new CalendarMonths()), null), TY_2025,
                NOTHING_READ);

        // Then — 12,000 × 6/12 + 24,000 × 6/12 = 6,000 + 12,000 = 18,000
        assertThat(result.value()).isEqualTo(Money.of("18000"));
    }

    @Test
    void apply_whenTheLatestVersionGoverns_appliesTheOneInForceAtYearEnd() {
        // When
        CapResult result = TemporallyApportionedCap.apply(
                changing(MidYearCombination.LATEST_VERSION_GOVERNS, Optional.empty(), null), TY_2025, NOTHING_READ);

        // Then
        assertThat(result.value()).isEqualTo(Money.of("24000"));
    }

    @Test
    void apply_whenTheEarliestVersionGoverns_appliesTheOneInForceAtYearStart() {
        // When
        CapResult result = TemporallyApportionedCap.apply(
                changing(MidYearCombination.EARLIEST_VERSION_GOVERNS, Optional.empty(), null), TY_2025,
                NOTHING_READ);

        // Then
        assertThat(result.value()).isEqualTo(Money.of("12000"));
    }

    @Test
    void apply_whenTheCombinationIsStatutory_isRefusedNamingBothRules() {
        // Given
        RuleRow statutory = changing(MidYearCombination.STATUTORY, Optional.empty(), new RuleId("FIXTURE-HOW"));

        // When / Then
        assertThatThrownBy(() -> TemporallyApportionedCap.apply(statutory, TY_2025, NOTHING_READ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(RULE.value())
                .hasMessageContaining("FIXTURE-HOW");
    }

    @Test
    void apply_whenTheCombinationIsUndetermined_throwsNamingTheRuleAndItsSegments() {
        // Given
        RuleRow undetermined = changing(MidYearCombination.UNDETERMINED, Optional.empty(), null);

        // When / Then
        assertThatThrownBy(() -> TemporallyApportionedCap.apply(undetermined, TY_2025, NOTHING_READ))
                .isInstanceOf(UndeterminedApportionmentException.class)
                .hasMessageContaining(RULE.value())
                .hasMessageContaining("2025-10-01");
    }

    @Test
    void governingVersion_whenLatestOrEarliestGoverns_isThatOneVersion() {
        assertThat(TemporallyApportionedCap.governingVersion(
                        changing(MidYearCombination.LATEST_VERSION_GOVERNS, Optional.empty(), null), TY_2025)
                .cap())
                .isEqualTo(new Fixed(Money.of("24000")));
        assertThat(TemporallyApportionedCap.governingVersion(
                        changing(MidYearCombination.EARLIEST_VERSION_GOVERNS, Optional.empty(), null), TY_2025)
                .cap())
                .isEqualTo(new Fixed(Money.of("12000")));
    }

    @Test
    void governingVersion_whenProrated_isRefusedBecauseNoOneVersionGoverns() {
        // Given
        RuleRow prorated = changing(MidYearCombination.PRORATE_PER_SEGMENT, Optional.of(new Days()), null);

        // When / Then
        assertThatThrownBy(() -> TemporallyApportionedCap.governingVersion(prorated, TY_2025))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(RULE.value())
                .hasMessageContaining("PRORATE_PER_SEGMENT");
    }
}
