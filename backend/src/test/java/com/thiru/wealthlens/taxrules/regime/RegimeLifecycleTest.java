package com.thiru.wealthlens.taxrules.regime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxrules.id.RegimeCode;
import com.thiru.wealthlens.taxrules.id.RuleId;
import com.thiru.wealthlens.taxrules.id.TaxYearId;
import com.thiru.wealthlens.taxrules.registry.DraftOrFinal;
import com.thiru.wealthlens.taxrules.registry.Provenance;
import com.thiru.wealthlens.taxrules.registry.VerificationStatus;
import com.thiru.wealthlens.taxrules.temporal.DateRange;
import com.thiru.wealthlens.taxrules.validate.PackValidationException;
import java.net.URI;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Two windows, and why one is not enough.
 *
 * <p>W-026b extends this class with the election protocol, and W-026c with {@code RegimeSpec}.
 */
class RegimeLifecycleTest {

    private static final RuleId S201 = new RuleId("REG-S201-ENTRY-001");

    private static Provenance provenance() {
        return new Provenance(
                URI.create("https://egazette.gov.in/WriteReadData/2025/000000.pdf"),
                "Income-tax Act 2025, s.201(2)",
                DraftOrFinal.FINAL,
                LocalDate.of(2026, 9, 19),
                VerificationStatus.VERIFIED,
                null);
    }

    /**
     * The s.201 shape as it stands today: entry closed on 31 March 2024, application open with no
     * end date, incumbents continuing indefinitely.
     */
    private static RegimeLifecycle grandfathered() {
        return new RegimeLifecycle(
                DateRange.between(LocalDate.of(2019, 4, 1), LocalDate.of(2024, 3, 31)),
                DateRange.fromOnwards(LocalDate.of(2019, 4, 1)),
                new ContinuationPolicy.ContinueIndefinitely(),
                new InvalidationPolicy.RetroactiveFromFailureYear(new RuleId("REG-S203-FAIL-001")),
                provenance());
    }

    @Nested
    @DisplayName("two windows — the ss.201/204 shape, today")
    class TwoWindows {

        @Test
        void aRegimeOpenToEntrantsIsAlsoApplicable() {
            RegimeLifecycle lifecycle = grandfathered();

            assertThat(lifecycle.openToNewEntrants(new TaxYearId(2022))).isTrue();
            assertThat(lifecycle.applicable(new TaxYearId(2022))).isTrue();
            assertThat(lifecycle.grandfatheredOnly(new TaxYearId(2022))).isFalse();
        }

        @Test
        void afterEntryClosesTheRegimeStillApplies() {
            // The whole point: a taxpayer who elected in by 31 March 2024 keeps using it
            RegimeLifecycle lifecycle = grandfathered();

            assertThat(lifecycle.openToNewEntrants(new TaxYearId(2026))).isFalse();
            assertThat(lifecycle.applicable(new TaxYearId(2026))).isTrue();
            assertThat(lifecycle.grandfatheredOnly(new TaxYearId(2026))).isTrue();
        }

        @Test
        void beforeTheRegimeExistsItIsNeitherOpenNorApplicable() {
            RegimeLifecycle lifecycle = grandfathered();

            assertThat(lifecycle.applicable(new TaxYearId(2017))).isFalse();
            assertThat(lifecycle.grandfatheredOnly(new TaxYearId(2017))).isFalse();
        }

        @Test
        void theSunsetBoundaryIsInclusiveOfTheYearItEndsOn() {
            // In force to 31 March 2026. TY 2025-26 runs 1 Apr 2025 to 31 Mar 2026, so the window
            // covers all of it — an off-by-one year here retires a regime twelve months early.
            RegimeLifecycle sunset = new RegimeLifecycle(
                    DateRange.between(LocalDate.of(2019, 4, 1), LocalDate.of(2024, 3, 31)),
                    DateRange.between(LocalDate.of(2019, 4, 1), LocalDate.of(2026, 3, 31)),
                    new ContinuationPolicy.FallToDefault(S201),
                    new InvalidationPolicy.NoInvalidation(),
                    provenance());

            assertThat(sunset.applicable(new TaxYearId(2025))).isTrue();
            assertThat(sunset.grandfatheredOnly(new TaxYearId(2025))).isTrue();
        }

        @Test
        void afterSunsetTheRegimeIsNotApplicable() {
            RegimeLifecycle sunset = new RegimeLifecycle(
                    DateRange.between(LocalDate.of(2019, 4, 1), LocalDate.of(2024, 3, 31)),
                    DateRange.between(LocalDate.of(2019, 4, 1), LocalDate.of(2026, 3, 31)),
                    new ContinuationPolicy.FallToDefault(S201),
                    new InvalidationPolicy.NoInvalidation(),
                    provenance());

            // TY 2026-27 begins the day after the window closes
            assertThat(sunset.applicable(new TaxYearId(2026))).isFalse();
            assertThat(sunset.grandfatheredOnly(new TaxYearId(2026))).isFalse();
        }

        @Test
        void anEntryWindowOutsideTheInForceWindowIsRejected() {
            // A regime cannot be entered in a year it does not apply
            assertThatThrownBy(() ->
                            new RegimeLifecycle(
                                    DateRange.between(LocalDate.of(2019, 4, 1), LocalDate.of(2030, 3, 31)),
                                    DateRange.between(LocalDate.of(2019, 4, 1), LocalDate.of(2026, 3, 31)),
                                    new ContinuationPolicy.ContinueIndefinitely(),
                                    new InvalidationPolicy.NoInvalidation(),
                                    provenance()))
                    .isInstanceOf(PackValidationException.class)
                    .hasMessageContaining("cannot be entered in a year it does not apply");
        }

        @Test
        void bothEdgePoliciesAreRequired() {
            // Leaving either undefined makes a taxpayer's position unanswerable
            assertThatThrownBy(() ->
                            new RegimeLifecycle(
                                    DateRange.fromOnwards(LocalDate.of(2019, 4, 1)),
                                    DateRange.fromOnwards(LocalDate.of(2019, 4, 1)),
                                    null,
                                    new InvalidationPolicy.NoInvalidation(),
                                    provenance()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("unanswerable");
        }
    }

    @Nested
    @DisplayName("ContinuationPolicy — what happens to an incumbent at sunset")
    class Continuation {

        @Test
        void continueIndefinitelyCitesNothingBecauseNothingEnds() {
            assertThat(new ContinuationPolicy.ContinueIndefinitely().citedRule()).isEmpty();
        }

        @Test
        void theOtherTwoMustCiteTheRuleThatEndsTheRegime() {
            // "Your regime ended and you moved" is a statement about the law a user may challenge
            assertThat(new ContinuationPolicy.FallToDefault(S201).citedRule()).contains(S201);
            assertThat(new ContinuationPolicy.MigrateTo(new RegimeCode("S204_SUCCESSOR"), S201).citedRule())
                    .contains(S201);

            assertThatThrownBy(() -> new ContinuationPolicy.FallToDefault(null))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new ContinuationPolicy.MigrateTo(null, S201))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void everyBranchIsAccountedFor() {
            java.util.List<ContinuationPolicy> all = java.util.List.of(
                    new ContinuationPolicy.FallToDefault(S201),
                    new ContinuationPolicy.MigrateTo(new RegimeCode("S204_SUCCESSOR"), S201),
                    new ContinuationPolicy.ContinueIndefinitely());

            for (ContinuationPolicy policy : all) {
                String outcome = switch (policy) {
                    case ContinuationPolicy.FallToDefault ignored -> "default";
                    case ContinuationPolicy.MigrateTo ignored -> "successor";
                    case ContinuationPolicy.ContinueIndefinitely ignored -> "continues";
                };
                assertThat(outcome).isNotBlank();
            }
            assertThat(all).hasSize(3);
        }
    }

    @Nested
    @DisplayName("InvalidationPolicy — s.203(2) is the severe one")
    class Invalidation {

        private static final RuleId S203 = new RuleId("REG-S203-FAIL-001");

        @Test
        void retroactiveInvalidationMustCiteItsBasis() {
            // It can reopen years the taxpayer considered closed, so the citation is not optional
            assertThat(new InvalidationPolicy.RetroactiveFromFailureYear(S203).citedRule()).contains(S203);

            assertThatThrownBy(() -> new InvalidationPolicy.RetroactiveFromFailureYear(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("considered closed");
        }

        @Test
        void theThreeOutcomesAreDistinct() {
            // Modelling this as a boolean would either overstate a taxpayer's exposure or hide it
            assertThat(new InvalidationPolicy.RetroactiveFromFailureYear(S203))
                    .isNotEqualTo(new InvalidationPolicy.ProspectiveOnly(S203));
            assertThat(new InvalidationPolicy.NoInvalidation().citedRule()).isEmpty();
        }

        @Test
        void everyBranchIsAccountedFor() {
            java.util.List<InvalidationPolicy> all = java.util.List.of(
                    new InvalidationPolicy.RetroactiveFromFailureYear(S203),
                    new InvalidationPolicy.ProspectiveOnly(S203),
                    new InvalidationPolicy.NoInvalidation());

            for (InvalidationPolicy policy : all) {
                String scope = switch (policy) {
                    case InvalidationPolicy.RetroactiveFromFailureYear ignored -> "that year and after";
                    case InvalidationPolicy.ProspectiveOnly ignored -> "after only";
                    case InvalidationPolicy.NoInvalidation ignored -> "none";
                };
                assertThat(scope).isNotBlank();
            }
            assertThat(all).hasSize(3);
        }
    }
}
