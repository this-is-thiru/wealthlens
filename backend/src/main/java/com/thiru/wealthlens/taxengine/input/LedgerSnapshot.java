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
 * @param vrsClaimedIn the tax year VRS was claimed, if ever. Once in a lifetime, and mutually
 *     exclusive with s.157 relief on the same amount ({@code RET-VRS-002})
 * @param gratuityAlreadyAllowed the {@code B} of {@code RET-GRAT-AGG-001}'s {@code A − B}
 * @param leaveEncashmentAlreadyAllowed the {@code B} of {@code RET-LEAVE-AGG-001}'s {@code A − B}
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
        Optional<TaxYearId> vrsClaimedIn,
        Money gratuityAlreadyAllowed,
        Money leaveEncashmentAlreadyAllowed,
        Map<TaxYearId, CarriedHousePropertyLoss> housePropertyLossCarriedForward,
        Optional<TaxYearId> educationLoanInterestFirstYear,
        Map<TaxYearId, CarriedCapitalLoss> capitalLossCarriedForward) {

    public LedgerSnapshot {
        if (leaveTravelByBlock == null) {
            throw new IllegalArgumentException(
                    "LedgerSnapshot.leaveTravelByBlock may not be null; pass an empty map where nothing was claimed");
        }
        leaveTravelByBlock = Map.copyOf(leaveTravelByBlock);
        if (gratuityAlreadyAllowed == null || leaveEncashmentAlreadyAllowed == null) {
            throw new IllegalArgumentException(
                    "LedgerSnapshot aggregate figures may not be null; pass Money.ZERO where"
                            + " nothing has been allowed, because 'nothing yet' and 'never asked'"
                            + " give different ceilings");
        }
        if (vrsClaimedIn == null || educationLoanInterestFirstYear == null) {
            throw new IllegalArgumentException(
                    "LedgerSnapshot optionals may not be null; pass Optional.empty() where the"
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
                Optional.empty(),
                Money.ZERO,
                Money.ZERO,
                Map.of(),
                Optional.empty(),
                Map.of());
    }

    /** Whether VRS has ever been claimed — {@code RET-VRS-002}'s once-in-a-lifetime test. */
    public boolean vrsAlreadyClaimed() {
        return vrsClaimedIn.isPresent();
    }

    /** What one LTA block, by its first calendar year, already holds — nothing where nothing was claimed. */
    public LeaveTravelBlock leaveTravelBlock(int firstCalendarYear) {
        return leaveTravelByBlock.getOrDefault(firstCalendarYear, new LeaveTravelBlock(0, false));
    }
}
