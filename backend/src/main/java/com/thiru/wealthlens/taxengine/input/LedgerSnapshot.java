package com.thiru.wealthlens.taxengine.input;

import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.util.Map;
import java.util.Optional;

/**
 * The cross-year state a single-year computation cannot hold.
 *
 * <h2>Why a year is not enough</h2>
 *
 * <p>{@code 05-domain-model.md} M3.4. Rules reach outside the year being computed, and each would
 * silently over-allow without this record: a four-calendar-year LTA block, a once-in-a-lifetime
 * VRS exemption, gratuity and leave-encashment ceilings expressed as {@code A − B} across every
 * earlier year <b>and employer</b>, an eight-year house-property loss carry-forward, the same for a capital
 * loss, and {@code DED-80E-002}'s eight-year window. The five pre-construction instalments need no ledger: each
 * tax year's instalment is fixed by the year of acquisition (S2 s.22(1)(c); D-159).
 *
 * <p>M3.4's own recommendation is that each is persisted as an <b>append-only ledger keyed on
 * (profile, rule, tax year)</b> rather than a mutable counter, because a recomputation must be able
 * to reproduce a past year's answer and a decremented counter cannot. This record is the
 * <i>projection</i> of those ledgers at one point in time — which is why every figure here is a
 * total already consumed, never a remaining balance. A remaining balance is a subtraction, and a
 * subtraction is the engine's job.
 *
 * @param leaveTravelByBlock what each four-calendar-year LTA block already holds, keyed by the block's first
 *     calendar year ({@code SAL-LTA-BLOCK-001}, {@code SAL-LTA-CARRY-001}). By block, not as one current block,
 *     because a tax year spans two calendar blocks ({@code DR 13} R-12; D-100)
 * @param deductionAllowedIn the tax year a deduction under each retirement entry was allowed, where one was. Keyed by
 *     entry, not held for voluntary retirement alone, because which entries are allowed once only is the
 *     {@code RS-RET-BAR} rows' to say, not this record's (W-155b; D-336)
 * @param retirementAllowedBefore what each retirement entry allowed in earlier tax years, from every employer. A fact
 *     per entry, not an aggregate's {@code B}: which entries form the {@code B} of {@code RET-GRATUITY-AGGREGATE}'s
 *     and {@code RET-LEAVE-ENCASHMENT-AGGREGATE}'s {@code A − B} is the {@code RS-RET-AGGREGATE-HISTORY} rows' to say
 *     (S2 s.19(2)(a), (f); W-155c1, D-339)
 * @param housePropertyLossCarriedForward each carried house-property loss by the year it arose, so the
 *     eight-year expiry is computable (S2 s.110; 1961 s.71B; D-218). The part attributable to a deduction
 *     the new regime disallows is <b>destroyed, not deferred,</b> in a new-regime year (S2 s.202(3);
 *     {@code SETOFF-NEW-002})
 * @param educationLoanInterestFirstYear starts {@code DED-80E-002}'s eight-year window
 * @param capitalLossCarriedForward each carried capital loss by the year it arose, by term, so the eight-year expiry
 *     and the long-term asymmetry are computable (S2 s.111, s.536(2)(n); 1961 s.74; D-247)
 */
public record LedgerSnapshot(
        Map<Integer, LeaveTravelBlock> leaveTravelByBlock,
        Map<RetirementEntry, TaxYearId> deductionAllowedIn,
        Map<RetirementEntry, Money> retirementAllowedBefore,
        Map<TaxYearId, CarriedHousePropertyLoss> housePropertyLossCarriedForward,
        Optional<TaxYearId> educationLoanInterestFirstYear,
        Map<TaxYearId, CarriedCapitalLoss> capitalLossCarriedForward) {

    public LedgerSnapshot {
        if (leaveTravelByBlock == null) {
            throw new IllegalArgumentException(
                    "LedgerSnapshot.leaveTravelByBlock may not be null; pass an empty map where nothing was claimed");
        }
        leaveTravelByBlock = Map.copyOf(leaveTravelByBlock);
        if (retirementAllowedBefore == null) {
            throw new IllegalArgumentException(
                    "LedgerSnapshot.retirementAllowedBefore may not be null; pass an empty map where"
                            + " nothing has been allowed, because 'nothing yet' and 'never asked'"
                            + " give different ceilings");
        }
        retirementAllowedBefore.forEach((entry, allowed) -> {
            if (allowed.isLessThan(Money.ZERO)) {
                throw new IllegalArgumentException("LedgerSnapshot.retirementAllowedBefore: " + entry + " allowed "
                        + allowed + " in earlier years; an amount allowed is never negative");
            }
        });
        retirementAllowedBefore = Map.copyOf(retirementAllowedBefore);
        if (deductionAllowedIn == null) {
            throw new IllegalArgumentException(
                    "LedgerSnapshot.deductionAllowedIn may not be null; pass an empty map where no retirement deduction"
                            + " was allowed");
        }
        deductionAllowedIn = Map.copyOf(deductionAllowedIn);
        if (educationLoanInterestFirstYear == null) {
            throw new IllegalArgumentException(
                    "LedgerSnapshot.educationLoanInterestFirstYear may not be null; pass Optional.empty() where the"
                            + " event has not happened");
        }
        if (housePropertyLossCarriedForward == null) {
            throw new IllegalArgumentException(
                    "LedgerSnapshot.housePropertyLossCarriedForward may not be null; pass an empty"
                            + " map where there is no carried loss");
        }
        housePropertyLossCarriedForward = Map.copyOf(housePropertyLossCarriedForward);
        if (capitalLossCarriedForward == null) {
            throw new IllegalArgumentException(
                    "LedgerSnapshot.capitalLossCarriedForward may not be null; pass an empty map where there is no"
                            + " carried loss");
        }
        capitalLossCarriedForward = Map.copyOf(capitalLossCarriedForward);
    }

    /** A taxpayer with no history — a first return, and the ordinary case in a golden test. */
    public static LedgerSnapshot empty() {
        return new LedgerSnapshot(
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Optional.empty(),
                Map.of());
    }

    /** What one LTA block, by its first calendar year, already holds — nothing where nothing was claimed. */
    public LeaveTravelBlock leaveTravelBlock(int firstCalendarYear) {
        return leaveTravelByBlock.getOrDefault(firstCalendarYear, new LeaveTravelBlock(0, false));
    }
}
