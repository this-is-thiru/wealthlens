package com.thiru.wealthlens.taxplanning.compute;

import com.thiru.wealthlens.portfolio.entity.AssetEntity;
import com.thiru.wealthlens.portfolio.repository.PortfolioRepository;
import com.thiru.wealthlens.shared.exception.BadRequestException;
import com.thiru.wealthlens.taxengine.api.ComputedTax;
import com.thiru.wealthlens.taxengine.api.TaxCalculator;
import com.thiru.wealthlens.taxengine.input.CapitalGainsInput;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxplanning.assemble.EngineInputsAssembler;
import com.thiru.wealthlens.taxplanning.portfolio.CapitalGainsAdapter;
import com.thiru.wealthlens.taxplanning.profile.TaxProfileService;
import com.thiru.wealthlens.taxplanning.profile.TaxpayerProfile;
import java.time.LocalDate;
import java.time.ZoneOffset;
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
    private final PortfolioRepository portfolioRepository;
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
        List<CapitalGainsView.HeldLot> held = heldAsOf.map(date -> heldLots(email, profile, date)).orElseGet(List::of);
        return new CapitalGainsView(realised, heldAsOf.orElse(null), held);
    }

    /**
     * Every held lot's term on {@code soldOn}. The date's year and regime are the request's, not a lot's, so a year no
     * pack ships for refuses the request here rather than becoming each lot's note (W-263).
     */
    private List<CapitalGainsView.HeldLot> heldLots(String email, TaxpayerProfile profile, LocalDate soldOn) {
        TaxYearId year = TaxYearId.containing(soldOn);
        RegimeCode regime = taxCalculator.defaultRegime(year);
        return portfolioRepository.findByEmail(email).stream().map(lot -> heldLot(profile, lot, soldOn, year, regime)).toList();
    }

    private CapitalGainsView.HeldLot heldLot(TaxpayerProfile profile, AssetEntity lot, LocalDate soldOn, TaxYearId year,
            RegimeCode regime) {
        CapitalGainsInput.BucketFacts facts = new CapitalGainsInput.BucketFacts(lot.getAssetType().name(), "UNKNOWN", "UNKNOWN",
                "PURCHASE", Optional.empty(), lot.getTransactionDate(), soldOn, CapitalGainsInput.SideVenue.UNRECORDED,
                new CapitalGainsInput.SideVenue(Optional.ofNullable(lot.getExchangeName()), Optional.empty()));
        CapitalGainsInput gains = CapitalGainsInput.none(soldOn.atStartOfDay(ZoneOffset.UTC).toInstant(), "held-lot-what-if");
        try {
            TaxCalculator.HeldTerm term = taxCalculator.termIfSold(
                    assembler.forCapitalGains(profile, year, regime, soldOn, gains), facts);
            return new CapitalGainsView.HeldLot(lot.getStockCode(), lot.getQuantity(), lot.getTransactionDate(), term.term(), term.row(),
                    term.months(), null);
        } catch (IllegalArgumentException e) {
            return new CapitalGainsView.HeldLot(lot.getStockCode(), lot.getQuantity(), lot.getTransactionDate(), null, null, null,
                    e.getMessage());
        }
    }
}
