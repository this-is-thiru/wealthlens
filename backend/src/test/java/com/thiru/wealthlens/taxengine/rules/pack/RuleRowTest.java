package com.thiru.wealthlens.taxengine.rules.pack;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.cap.Fixed;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.registry.LegalStatus;
import com.thiru.wealthlens.taxengine.rules.registry.Provenance;
import com.thiru.wealthlens.taxengine.rules.registry.PublicationStatus;
import com.thiru.wealthlens.taxengine.rules.registry.VerificationLevel;
import com.thiru.wealthlens.taxengine.rules.temporal.AmendmentRef;
import com.thiru.wealthlens.taxengine.rules.temporal.MissingTemporalCoordinateException;
import com.thiru.wealthlens.taxengine.rules.temporal.NoRuleVersionException;
import com.thiru.wealthlens.taxengine.rules.temporal.Provision;
import com.thiru.wealthlens.taxengine.rules.temporal.RuleVersion;
import com.thiru.wealthlens.taxengine.rules.temporal.TemporalAxis;
import com.thiru.wealthlens.taxengine.rules.temporal.TemporalContext;
import com.thiru.wealthlens.taxengine.rules.temporal.Validity;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Version selection, which is the whole reason a rule id can stay stable while the law moves.
 *
 * <p>A rule id appears in stored computations, trace lines, golden tests and the claim catalogue, so
 * picking the wrong version is not a display bug — it recomputes an old return with this year's law.
 */
class RuleRowTest {

    private static final RuleId STANDARD_DEDUCTION = new RuleId("SAL-STDDED-001");
    private static final TaxYearId TY_2026 = new TaxYearId(2026);

    private static Provenance provenance() {
        return new Provenance(
                URI.create("https://egazette.gov.in/WriteReadData/2025/000000.pdf"),
                "Income-tax Act 2025, s.19(1) Table Sl. 2(a)",
                LocalDate.of(2026, 9, 19),
                VerificationLevel.SOURCE_CHECKED,
                null);
    }

    private static AmendmentRef amendment(LocalDate effectiveFrom, LocalDate notifiedOn) {
        return new AmendmentRef(
                "Finance Act, 2026 (4 of 2026), s.38",
                "CG-DL-E-31032026-271439",
                notifiedOn,
                effectiveFrom,
                effectiveFrom.isBefore(notifiedOn),
                URI.create("https://egazette.gov.in/WriteReadData/2026/271439.pdf"),
                VerificationLevel.SOURCE_CHECKED);
    }

    private static RuleVersion version(Validity validity, String amount) {
        return new RuleVersion(
                validity,
                new RegimeApplicability.AllRegimes(),
                new Fixed(Money.of(amount)),
                Optional.empty(),
                Optional.empty(),
                List.of(),
                Provision.present("Income-tax Act, 1961", "s.16(ia)"),
                Provision.present("Income-tax Act, 2025", "s.19(1) Table Sl. 2(a)"),
                amendment(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 3, 31)),
                // A pending validity must be stated as pending in the legal status too (D-052)
                validity instanceof Validity.PendingNotification
                        ? LegalStatus.PENDING_NOTIFICATION
                        : LegalStatus.IN_FORCE,
                PublicationStatus.PUBLISHED,
                provenance());
    }

    /** ₹50,000 to 31 March 2026, then ₹75,000 onwards. */
    private static RuleRow twoVersions() {
        return new RuleRow(
                STANDARD_DEDUCTION,
                TemporalAxis.TAX_YEAR,
                List.of(
                        version(
                                new Validity.Between(
                                        LocalDate.of(2020, 4, 1), Optional.of(LocalDate.of(2026, 3, 31))),
                                "50000"),
                        version(Validity.Between.fromOnwards(LocalDate.of(2026, 4, 1)), "75000")),
                provenance());
    }

    private static TemporalContext at(int year) {
        return TemporalContext.forTaxYear(new TaxYearId(year), LocalDate.of(2026, 9, 19));
    }

    @Nested
    @DisplayName("versionAt")
    class VersionSelection {

        @Test
        void picksTheVersionInForceAtYearEnd() {
            // TY 2026-27 ends 31 March 2027, so the ₹75,000 version applies
            assertThat(twoVersions().versionAt(at(2026)).cap()).isEqualTo(new Fixed(Money.of("75000")));
        }

        @Test
        void picksTheEarlierVersionForAnEarlierYear() {
            // TY 2025-26 ends 31 March 2026 — the last day the ₹50,000 version was in force
            assertThat(twoVersions().versionAt(at(2025)).cap()).isEqualTo(new Fixed(Money.of("50000")));
        }

        @Test
        void bothBoundariesAreInclusiveAndAdjacent() {
            RuleRow row = twoVersions();

            // The two windows touch without a gap: 31 March 2026 and 1 April 2026
            assertThat(row.versions().get(0).validity().contains(LocalDate.of(2026, 3, 31))).isTrue();
            assertThat(row.versions().get(0).validity().contains(LocalDate.of(2026, 4, 1))).isFalse();
            assertThat(row.versions().get(1).validity().contains(LocalDate.of(2026, 4, 1))).isTrue();
        }

        @Test
        void whenNoVersionCoversTheDate_throws() {
            // A gap the pack validator should have caught. The law said nothing then, and there is
            // no defensible amount to return.
            RuleRow gapped = new RuleRow(
                    STANDARD_DEDUCTION,
                    TemporalAxis.TAX_YEAR,
                    List.of(version(Validity.Between.fromOnwards(LocalDate.of(2030, 4, 1)), "75000")),
                    provenance());

            assertThatThrownBy(() -> gapped.versionAt(at(2026)))
                    .isInstanceOf(NoRuleVersionException.class)
                    .hasMessageContaining("SAL-STDDED-001")
                    .hasMessageContaining("2027-03-31");
        }

        @Test
        void aPendingNotificationVersionIsNeverSelected() {
            // It is enacted but not in force; contains() is always false
            RuleRow pending = new RuleRow(
                    new RuleId("SAL-DISPENSION-001"),
                    TemporalAxis.TAX_YEAR,
                    List.of(version(
                            new Validity.PendingNotification(
                                    new RuleId("SAL-DISPENSION-002"),
                                    "the pre-amendment exemption continues until notified",
                                    provenance()),
                            "0")),
                    provenance());

            assertThatThrownBy(() -> pending.versionAt(at(2026)))
                    .isInstanceOf(NoRuleVersionException.class);
        }

        @Test
        void anAxisWithNoDateSuppliedThrowsRatherThanFallingBackToTheYear() {
            // D-TEMPORALKEY: s.197(3) turns on the acquisition date. Falling back to the tax year
            // would answer a question about a 2019 asset using this year's law.
            RuleRow acquisitionKeyed = new RuleRow(
                    new RuleId("CG-INDEX-001"),
                    TemporalAxis.ACQUISITION_DATE,
                    List.of(version(Validity.Between.fromOnwards(LocalDate.of(2020, 4, 1)), "0")),
                    provenance());

            assertThatThrownBy(() -> acquisitionKeyed.versionAt(at(2026)))
                    .isInstanceOf(MissingTemporalCoordinateException.class)
                    .hasMessageContaining("ACQUISITION_DATE");
        }

        @Test
        void anAxisWithItsDateSuppliedResolves() {
            RuleRow acquisitionKeyed = new RuleRow(
                    new RuleId("CG-INDEX-001"),
                    TemporalAxis.ACQUISITION_DATE,
                    List.of(version(Validity.Between.fromOnwards(LocalDate.of(2020, 4, 1)), "0")),
                    provenance());

            TemporalContext ctx =
                    at(2026).with(TemporalAxis.ACQUISITION_DATE, LocalDate.of(2024, 7, 23));

            assertThat(acquisitionKeyed.versionAt(ctx)).isNotNull();
        }
    }

    @Nested
    @DisplayName("changesWithin — what drives apportionment")
    class MidYearChange {

        @Test
        void isFalse_whenOneVersionSpansTheWholeYear() {
            assertThat(twoVersions().changesWithin(at(2026))).isFalse();
        }

        @Test
        void isTrue_whenARuleChangesInsideTheYear() {
            // Not hypothetical: the Income-tax Rules 2026 were amended four times inside the year
            // they govern, one of them commencing partly on 1 April and partly on publication.
            RuleRow midYear = new RuleRow(
                    STANDARD_DEDUCTION,
                    TemporalAxis.TAX_YEAR,
                    List.of(
                            version(
                                    new Validity.Between(
                                            LocalDate.of(2026, 4, 1),
                                            Optional.of(LocalDate.of(2026, 8, 31))),
                                    "50000"),
                            version(Validity.Between.fromOnwards(LocalDate.of(2026, 9, 1)), "75000")),
                    provenance());

            assertThat(midYear.changesWithin(at(2026))).isTrue();
        }

        @Test
        void aPendingNotificationVersionDoesNotCountAsAChange() {
            // It is never in force, so it cannot be one of the two versions that make a change
            RuleRow withPending = new RuleRow(
                    STANDARD_DEDUCTION,
                    TemporalAxis.TAX_YEAR,
                    List.of(
                            version(Validity.Between.fromOnwards(LocalDate.of(2020, 4, 1)), "50000"),
                            version(
                                    new Validity.PendingNotification(
                                            new RuleId("SAL-STDDED-002"), "the old limit continues", provenance()),
                                    "75000")),
                    provenance());

            assertThat(withPending.changesWithin(at(2026))).isFalse();
        }
    }

    @Nested
    @DisplayName("construction")
    class Construction {

        @Test
        void aRuleWithNoVersionsIsRejected() {
            assertThatThrownBy(() ->
                            new RuleRow(STANDARD_DEDUCTION, TemporalAxis.TAX_YEAR, List.of(), provenance()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("no versions");
        }

        @Test
        void aRuleMustDeclareItsAxis() {
            assertThatThrownBy(() ->
                            new RuleRow(STANDARD_DEDUCTION, null, List.of(version(
                                    Validity.Between.fromOnwards(LocalDate.of(2020, 4, 1)), "1")), provenance()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("TemporalAxis");
        }

        @Test
        void aVersionMustStateBothActsProvisions() {
            // Provision.absent and Provision.notYetVerified exist so that "we did not look" and
            // "there is nothing there" are different answers, and neither is silence
            assertThatThrownBy(() ->
                            new RuleVersion(
                                    Validity.Between.fromOnwards(LocalDate.of(2020, 4, 1)),
                                    new RegimeApplicability.AllRegimes(),
                                    new Fixed(Money.ZERO),
                                    Optional.empty(),
                                    Optional.empty(),
                                    List.of(),
                                    null,
                                    Provision.present("Income-tax Act, 2025", "s.19"),
                                    amendment(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 3, 31)),
                                    LegalStatus.IN_FORCE,
                                    PublicationStatus.PUBLISHED,
                                    provenance()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("both Acts");
        }

        @Test
        void anAmendmentWhoseFlagContradictsItsDatesIsRejected() {
            assertThatThrownBy(() ->
                            new AmendmentRef(
                                    "Finance Act, 2026",
                                    "CG-DL-E-31032026-271439",
                                    LocalDate.of(2026, 3, 31),
                                    LocalDate.of(2026, 4, 1),
                                    true,
                                    URI.create("https://egazette.gov.in/x.pdf"),
                                    VerificationLevel.SOURCE_CHECKED))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must agree");
        }
    }

    @Nested
    @DisplayName("RegimeApplicability — a set, never a tri-state")
    class Applicability {

        private static final com.thiru.wealthlens.taxengine.rules.id.RegimeCode DEFAULT_REGIME =
                new com.thiru.wealthlens.taxengine.rules.id.RegimeCode("NEW_TAX_REGIME");
        private static final com.thiru.wealthlens.taxengine.rules.id.RegimeCode OPTED_OUT =
                new com.thiru.wealthlens.taxengine.rules.id.RegimeCode("FIRST_SCHEDULE_RATES");
        private static final com.thiru.wealthlens.taxengine.rules.id.RegimeCode FUTURE =
                new com.thiru.wealthlens.taxengine.rules.id.RegimeCode("FUTURE_REGIME");

        @Test
        void allRegimes_includesARegimeThatDoesNotExistYet() {
            assertThat(new RegimeApplicability.AllRegimes().appliesIn(FUTURE)).isTrue();
        }

        @Test
        void onlyIn_excludesANewRegime() {
            RegimeApplicability rule =
                    new RegimeApplicability.OnlyIn(java.util.Set.of(DEFAULT_REGIME, OPTED_OUT));

            assertThat(rule.appliesIn(DEFAULT_REGIME)).isTrue();
            assertThat(rule.appliesIn(FUTURE)).isFalse();
        }

        @Test
        void exceptIn_includesANewRegime() {
            // "Everywhere except the default regime" keeps meaning that when a fifth regime arrives.
            // Written as OnlyIn(the others) it would silently fail to apply, announcing nothing.
            RegimeApplicability rule =
                    new RegimeApplicability.ExceptIn(java.util.Set.of(DEFAULT_REGIME));

            assertThat(rule.appliesIn(DEFAULT_REGIME)).isFalse();
            assertThat(rule.appliesIn(FUTURE)).isTrue();
        }

        @Test
        void emptySetsAreRejected() {
            assertThatThrownBy(() -> new RegimeApplicability.OnlyIn(java.util.Set.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("applies nowhere");
            assertThatThrownBy(() -> new RegimeApplicability.ExceptIn(java.util.Set.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("AllRegimes");
        }
    }
}
