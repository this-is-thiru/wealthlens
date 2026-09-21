package com.thiru.wealthlens.taxengine.input;

import com.thiru.wealthlens.taxrules.id.TaxYearId;
import com.thiru.wealthlens.taxrules.money.Money;
import java.util.Map;
import java.util.Optional;

/**
 * The cross-year state a single-year computation cannot hold.
 *
 * <h2>Why a year is not enough</h2>
 *
 * <p>{@code 05-domain-model.md} M3.4. Six rules reach outside the year being computed, and each
 * would silently over-allow without this record: a four-calendar-year LTA block, a once-in-a-lifetime
 * VRS exemption, gratuity and leave-encashment ceilings expressed as {@code A − B} across every
 * earlier year <b>and employer</b>, five equal pre-construction instalments, an eight-year
 * house-property loss carry-forward, and {@code DED-80E-002}'s eight-year window.
 *
 * <p>M3.4's own recommendation is that each is persisted as an <b>append-only ledger keyed on
 * (profile, rule, tax year)</b> rather than a mutable counter, because a recomputation must be able
 * to reproduce a past year's answer and a decremented counter cannot. This record is the
 * <i>projection</i> of those ledgers at one point in time — which is why every figure here is a
 * total already consumed, never a remaining balance. A remaining balance is a subtraction, and a
 * subtraction is the engine's job.
 *
 * @param ltaJourneysClaimedInBlock journeys already claimed in the current four-calendar-year block
 *     ({@code SAL-LTA-BLOCK-001})
 * @param ltaCarryOverAvailable whether the carried journey is still available; it does <b>not</b>
 *     count against the new block ({@code SAL-LTA-CARRY-001})
 * @param vrsClaimedIn the tax year VRS was claimed, if ever. Once in a lifetime, and mutually
 *     exclusive with s.157 relief on the same amount ({@code RET-VRS-002})
 * @param gratuityAlreadyAllowed the {@code B} of {@code RET-GRAT-AGG-001}'s {@code A − B}
 * @param leaveEncashmentAlreadyAllowed the {@code B} of {@code RET-LEAVE-AGG-001}'s {@code A − B}
 * @param preConstructionInstalmentsClaimed how many of the five are gone
 *     ({@code HP-DED-PRECON-001})
 * @param housePropertyLossCarriedForward loss by the year it arose, so the eight-year expiry is
 *     computable ({@code SETOFF-CF-HP-001}). <b>Destroyed, not deferred, in the new regime</b>
 *     ({@code SETOFF-NEW-002}) — which is a rule about this map, not a reason to omit it
 * @param educationLoanInterestFirstYear starts {@code DED-80E-002}'s eight-year window
 */
public record LedgerSnapshot(
        int ltaJourneysClaimedInBlock,
        boolean ltaCarryOverAvailable,
        Optional<TaxYearId> vrsClaimedIn,
        Money gratuityAlreadyAllowed,
        Money leaveEncashmentAlreadyAllowed,
        int preConstructionInstalmentsClaimed,
        Map<TaxYearId, Money> housePropertyLossCarriedForward,
        Optional<TaxYearId> educationLoanInterestFirstYear) {

    public LedgerSnapshot {
        if (ltaJourneysClaimedInBlock < 0) {
            throw new IllegalArgumentException(
                    "LedgerSnapshot.ltaJourneysClaimedInBlock must not be negative, but was "
                            + ltaJourneysClaimedInBlock);
        }
        if (preConstructionInstalmentsClaimed < 0) {
            throw new IllegalArgumentException(
                    "LedgerSnapshot.preConstructionInstalmentsClaimed must not be negative, but was "
                            + preConstructionInstalmentsClaimed);
        }
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
    }

    /** A taxpayer with no history — a first return, and the ordinary case in a golden test. */
    public static LedgerSnapshot empty() {
        return new LedgerSnapshot(
                0,
                false,
                Optional.empty(),
                Money.ZERO,
                Money.ZERO,
                0,
                Map.of(),
                Optional.empty());
    }

    /** Whether VRS has ever been claimed — {@code RET-VRS-002}'s once-in-a-lifetime test. */
    public boolean vrsAlreadyClaimed() {
        return vrsClaimedIn.isPresent();
    }

    /** The carried house-property loss that arose in one year, or zero. */
    public Money housePropertyLossFrom(TaxYearId year) {
        return housePropertyLossCarriedForward.getOrDefault(year, Money.ZERO);
    }
}
