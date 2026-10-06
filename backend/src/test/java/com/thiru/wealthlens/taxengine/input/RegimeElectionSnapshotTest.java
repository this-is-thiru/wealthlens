package com.thiru.wealthlens.taxengine.input;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.input.CapitalGainsInput.BroughtForwardLoss;
import com.thiru.wealthlens.taxengine.input.CapitalGainsInput.Bucket;
import com.thiru.wealthlens.taxengine.input.CapitalGainsInput.Term;
import com.thiru.wealthlens.taxengine.input.FilingFacts.Payment;
import com.thiru.wealthlens.taxengine.rules.component.RateBasisHint;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.regime.ElectionState;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The facts about a taxpayer's position, all of them passed in rather than fetched.
 */
class RegimeElectionSnapshotTest {

    private static final RegimeCode NEW = new RegimeCode("NEW_TAX_REGIME");
    private static final RegimeCode OLD = new RegimeCode("FIRST_SCHEDULE_RATES");
    private static final Instant AS_OF = Instant.parse("2026-09-21T00:00:00Z");
    private static final LocalDate DUE = LocalDate.of(2026, 7, 31);

    @Nested
    @DisplayName("RegimeElectionSnapshot — the history is passed in, not fetched")
    class Elections {

        @Test
        void stateOfDefaultsToNeverElected() {
            // A3 puts the default here rather than at each call site, so that "no row" and
            // "never elected" cannot drift apart.
            assertThat(RegimeElectionSnapshot.none().stateOf(NEW))
                    .isEqualTo(ElectionState.NEVER_ELECTED);
        }

        @Test
        void aRecordedStateIsReturned() {
            RegimeElectionSnapshot snapshot = new RegimeElectionSnapshot(
                    Map.of(NEW, ElectionState.IN_EFFECT), Map.of());

            assertThat(snapshot.stateOf(NEW)).isEqualTo(ElectionState.IN_EFFECT);
            assertThat(snapshot.stateOf(OLD)).isEqualTo(ElectionState.NEVER_ELECTED);
        }

        @Test
        void withdrawalsDefaultToNone() {
            assertThat(RegimeElectionSnapshot.none().withdrawalsUsedFor(NEW)).isZero();
        }

        @Test
        void bothMapsAreDefensivelyCopied() {
            Map<RegimeCode, ElectionState> states = new HashMap<>();
            states.put(NEW, ElectionState.IN_EFFECT);
            Map<RegimeCode, Integer> withdrawals = new HashMap<>();
            withdrawals.put(NEW, 1);
            RegimeElectionSnapshot snapshot = new RegimeElectionSnapshot(states, withdrawals);

            states.put(NEW, ElectionState.PERMANENTLY_INELIGIBLE);
            withdrawals.put(NEW, 99);

            assertThat(snapshot.stateOf(NEW)).isEqualTo(ElectionState.IN_EFFECT);
            assertThat(snapshot.withdrawalsUsedFor(NEW)).isEqualTo(1);
        }

        @Test
        void aNullMapIsRejected() {
            assertThatThrownBy(() -> new RegimeElectionSnapshot(null, Map.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("never elected anything");
        }

        @Test
        void withdrawnAndPermanentlyIneligibleAreDistinct() {
            // The difference is not recoverable from the event log alone -- it depends on which
            // protocol applied at the time -- which is why it is a state and not a derivation.
            assertThat(ElectionState.values())
                    .containsExactly(
                            ElectionState.NEVER_ELECTED,
                            ElectionState.IN_EFFECT,
                            ElectionState.WITHDRAWN,
                            ElectionState.PERMANENTLY_INELIGIBLE,
                            ElectionState.LAPSED);
        }
    }

    @Nested
    @DisplayName("FilingFacts — dated instalments, because two rules read the date")
    class Filing {

        @Test
        void theTotalIsDerivedFromTheDatedPayments() {
            FilingFacts facts = new FilingFacts(
                    DUE,
                    Optional.empty(),
                    List.of(
                            new Payment(LocalDate.of(2025, 6, 15), Money.of("30000")),
                            new Payment(LocalDate.of(2026, 3, 15), Money.of("50000"))));

            assertThat(facts.totalAdvanceTaxPaid()).isEqualTo(Money.of("80000"));
        }

        @Test
        void advanceTaxIsCountedByTheDateItWasPaid() {
            // ADV-MAR31-001: what was paid by 31 March is the year's advance tax. A payment in
            // April is not, and a single total could not tell the two apart.
            FilingFacts facts = new FilingFacts(
                    DUE,
                    Optional.empty(),
                    List.of(
                            new Payment(LocalDate.of(2026, 3, 31), Money.of("50000")),
                            new Payment(LocalDate.of(2026, 4, 1), Money.of("20000"))));

            assertThat(facts.advanceTaxPaidBy(LocalDate.of(2026, 3, 31)))
                    .isEqualTo(Money.of("50000"));
            assertThat(facts.totalAdvanceTaxPaid()).isEqualTo(Money.of("70000"));
        }

        @Test
        void aPaymentWithoutADateIsRejected() {
            assertThatThrownBy(() -> new Payment(null, Money.of("1")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("ADV-MAR31-001");
        }

        @Test
        void notFurnishedIsDifferentFromFurnishedOnTime() {
            assertThat(FilingFacts.notYetFurnished(DUE).furnishedLate()).isFalse();
            assertThat(new FilingFacts(DUE, Optional.of(DUE), List.of()).furnishedLate()).isFalse();
            assertThat(new FilingFacts(DUE, Optional.of(DUE.plusDays(1)), List.of()).furnishedLate())
                    .isTrue();
        }

        @Test
        void thePaymentListIsDefensivelyCopied() {
            List<Payment> mutable = new ArrayList<>();
            mutable.add(new Payment(LocalDate.of(2025, 6, 15), Money.of("30000")));
            FilingFacts facts = new FilingFacts(DUE, Optional.empty(), mutable);

            mutable.add(new Payment(LocalDate.of(2025, 9, 15), Money.of("999999")));

            assertThat(facts.totalAdvanceTaxPaid()).isEqualTo(Money.of("30000"));
        }

        @Test
        void aNullFurnishedOnIsRejectedRatherThanMeaningToday() {
            assertThatThrownBy(() -> new FilingFacts(DUE, null, List.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("not yet furnished");
        }
    }

    @Nested
    @DisplayName("CapitalGainsInput — gross buckets with a hint, never a tax")
    class CapitalGains {

        private static CapitalGainsInput withBuckets() {
            return new CapitalGainsInput(
                    List.of(
                            new Bucket(
                                    Money.of("300000"),
                                    Term.LONG_TERM,
                                    RateBasisHint.SPECIAL_RATE_LISTED_EQUITY,
                                    Optional.empty()),
                            new Bucket(
                                    Money.of("100000"),
                                    Term.SHORT_TERM,
                                    RateBasisHint.SLAB,
                                    Optional.of("PF-FIFO-001"))),
                    List.of(
                            new BroughtForwardLoss(
                                    TaxYearId.of(2023), Term.LONG_TERM, Money.of("50000")),
                            new BroughtForwardLoss(
                                    TaxYearId.of(2024), Term.SHORT_TERM, Money.of("20000"))),
                    AS_OF,
                    "pf-run-77");
        }

        @Test
        void gainIsSummedPerRateBasis() {
            assertThat(withBuckets().grossGainOn(RateBasisHint.SPECIAL_RATE_LISTED_EQUITY))
                    .isEqualTo(Money.of("300000"));
            assertThat(withBuckets().grossGainOn(RateBasisHint.SPECIAL_RATE_OTHER))
                    .isEqualTo(Money.ZERO);
        }

        @Test
        void lossesArriveUnNettedAndKeepTheirTerm() {
            // F5: NOT pre-netted. s.108(2) is asymmetric -- a short-term loss goes against any
            // capital gain, a long-term loss only against long-term -- so netting early would
            // silently over-allow.
            CapitalGainsInput input = withBuckets();

            assertThat(input.broughtForwardLossOf(Term.LONG_TERM)).isEqualTo(Money.of("50000"));
            assertThat(input.broughtForwardLossOf(Term.SHORT_TERM)).isEqualTo(Money.of("20000"));
            assertThat(input.broughtForward()).hasSize(2);
        }

        @Test
        void aBucketWithoutARateBasisIsRejectedRatherThanFallingBackToSlab() {
            // R-10 and 13-edge-cases row 67: a basis the pack does not recognise is a blocking
            // error. Defaulting to slab produces a plausible number that is wrong.
            assertThatThrownBy(() -> new Bucket(
                            Money.of("1"), Term.LONG_TERM, null, Optional.empty()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("not a fallback to the slab ladder");
        }

        @Test
        void aBucketWithoutATermIsRejected() {
            assertThatThrownBy(() -> new Bucket(
                            Money.of("1"), null, RateBasisHint.SLAB, Optional.empty()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("s.108(2)");
        }

        @Test
        void bothListsAreDefensivelyCopied() {
            List<Bucket> buckets = new ArrayList<>();
            buckets.add(new Bucket(
                    Money.of("100"), Term.LONG_TERM, RateBasisHint.SLAB, Optional.empty()));
            CapitalGainsInput input =
                    new CapitalGainsInput(buckets, new ArrayList<>(), AS_OF, "pf-run-1");

            buckets.add(new Bucket(
                    Money.of("999999"), Term.SHORT_TERM, RateBasisHint.SLAB, Optional.empty()));

            assertThat(input.buckets()).hasSize(1);
        }

        @Test
        void thePinIsMandatoryBecauseADraftMustBeComparableLater() {
            // F4.1: the "portfolio has changed since you last computed" prompt is a comparison of
            // two asOf values, not a recomputation. Without the pin there is nothing to compare.
            assertThatThrownBy(() -> new CapitalGainsInput(List.of(), List.of(), null, "ref"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("asOf");
            assertThatThrownBy(() -> new CapitalGainsInput(List.of(), List.of(), AS_OF, "  "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("computation reference");
        }

        @Test
        void noCapitalGainsIsStillPinned() {
            CapitalGainsInput none = CapitalGainsInput.none(AS_OF, "pf-run-0");

            assertThat(none.buckets()).isEmpty();
            assertThat(none.asOf()).isEqualTo(AS_OF);
        }
    }

    @Nested
    @DisplayName("EnteredFacts — a declared seam, empty today")
    class Entered {

        @Test
        void none_whenNothingIsEntered_holdsNoEmployment() {
            // B-003's lesson: an ABSENT component is invisible to the cache key, a thin one is
            // not. The employments are its first field (D-090); "none" is an empty list, never null.
            assertThat(EnteredFacts.none().employments()).isEmpty();
            assertThat(EnteredFacts.none().tenancies()).isEmpty();
            assertThat(EnteredFacts.none()).isEqualTo(new EnteredFacts(java.util.List.of(), java.util.List.of(), List.of(), List.of(), Optional.empty(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), Optional.empty(), List.of(), List.of()));
        }
    }
}
