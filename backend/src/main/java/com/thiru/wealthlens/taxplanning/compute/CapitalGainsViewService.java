package com.thiru.wealthlens.taxplanning.compute;

import com.thiru.wealthlens.shared.exception.BadRequestException;
import com.thiru.wealthlens.taxengine.api.ComputedTax;
import com.thiru.wealthlens.taxengine.api.TaxCalculator;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxplanning.assemble.EngineInputsAssembler;
import com.thiru.wealthlens.taxplanning.portfolio.CapitalGainsAdapter;
import com.thiru.wealthlens.taxplanning.profile.TaxProfileService;
import com.thiru.wealthlens.taxplanning.profile.TaxpayerProfile;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads a holder's realised gains, and held lots' terms as if sold on a date, through the engine's own rows (W-160g): no law here.
 * A lot the rows give no term for is named rather than refusing the rest.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CapitalGainsViewService {

    private final TaxProfileService taxProfileService;
    private final EngineInputsAssembler assembler;
    private final CapitalGainsAdapter capitalGainsAdapter;
    private final HeldAssetsViewService heldAssetsViewService;
    private final TaxCalculator taxCalculator;

    /** @throws BadRequestException unless this profile is the account's and holds its portfolio */
    public CapitalGainsView view(String email, String profileId, int taxYearStart, Optional<LocalDate> heldAsOf) {
        TaxpayerProfile profile = taxProfileService.owned(email, profileId);
        if (!profile.isHoldsPortfolio()) {
            throw new BadRequestException("This profile does not hold the account's portfolio");
        }
        TaxYearId year = TaxYearId.of(taxYearStart);
        RegimeCode regime = taxCalculator.defaultRegime(year);
        LocalDate readOn = year.startsOn().plusMonths(5);
        List<ComputedTax.Bucket> realised = taxCalculator.compute(assembler.forCapitalGains(profile, year, regime, readOn,
                capitalGainsAdapter.inputFor(email, year))).capitalGainsBuckets();
        List<CapitalGainsView.HeldLot> held = heldAsOf
                .map(date -> heldAssetsViewService.lots(email, profile, date).stream().map(CapitalGainsViewService::heldLot).toList())
                .orElseGet(List::of);
        return new CapitalGainsView(realised, heldAsOf.orElse(null), held);
    }

    /** A held lot as this view has always shown it; the note is the refusal only where the rows gave no term. */
    private static CapitalGainsView.HeldLot heldLot(HeldAssetsView.Lot lot) {
        return new CapitalGainsView.HeldLot(lot.stockCode(), lot.quantity(), lot.acquiredOn(), lot.term(), lot.termRow(),
                lot.monthsRead(), lot.term() == null ? lot.note() : null);
    }
}
