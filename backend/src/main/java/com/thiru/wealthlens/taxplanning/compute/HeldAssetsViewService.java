package com.thiru.wealthlens.taxplanning.compute;

import com.thiru.wealthlens.portfolio.entity.AssetEntity;
import com.thiru.wealthlens.portfolio.repository.PortfolioRepository;
import com.thiru.wealthlens.shared.exception.BadRequestException;
import com.thiru.wealthlens.taxengine.api.TaxCalculator;
import com.thiru.wealthlens.taxengine.input.CapitalGainsInput;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxplanning.assemble.EngineInputsAssembler;
import com.thiru.wealthlens.taxplanning.profile.TaxProfileService;
import com.thiru.wealthlens.taxplanning.profile.TaxpayerProfile;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads the lots a holder still holds and the term each would have if sold on a date, through the engine's own rows
 * (W-262): no law here, and no gain computed. The one reader of a held lot's term, which the capital-gains view uses too.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class HeldAssetsViewService {

    private final TaxProfileService taxProfileService;
    private final EngineInputsAssembler assembler;
    private final PortfolioRepository portfolioRepository;
    private final TaxCalculator taxCalculator;
    private final Clock clock;

    /**
     * @param asOf the date the lots are read on; today where none is given
     * @param term the one term to list; every lot, with or without a term, where none is given
     * @throws BadRequestException unless this profile is the account's and holds its portfolio
     * @throws IllegalArgumentException where no rule pack ships for the year {@code asOf} falls in
     */
    public HeldAssetsView view(String email, String profileId, Optional<LocalDate> asOf, Optional<CapitalGainsInput.Term> term) {
        TaxpayerProfile profile = taxProfileService.owned(email, profileId);
        if (!profile.isHoldsPortfolio()) {
            throw new BadRequestException("This profile does not hold the account's portfolio");
        }
        LocalDate readOn = asOf.orElseGet(() -> LocalDate.now(clock));
        List<HeldAssetsView.Lot> all = lots(email, profile, readOn);
        List<HeldAssetsView.Lot> listed = term.map(asked -> all.stream().filter(lot -> asked.name().equals(lot.term())).toList())
                .orElse(all);
        Set<String> stocks = new LinkedHashSet<>(listed.stream().map(HeldAssetsView.Lot::stockCode).toList());
        return new HeldAssetsView(readOn, listed, stocks.stream().map(stock -> total(stock, all)).toList());
    }

    /**
     * Every lot with something still held, read as if sold on {@code soldOn}. The date's year and regime are the
     * request's, not a lot's, so a year no pack ships for refuses the request rather than becoming each lot's note (W-263).
     */
    List<HeldAssetsView.Lot> lots(String email, TaxpayerProfile profile, LocalDate soldOn) {
        TaxYearId year = TaxYearId.containing(soldOn);
        RegimeCode regime = taxCalculator.defaultRegime(year);
        return portfolioRepository.findByEmail(email).stream()
                .filter(lot -> lot.getQuantity() != null && lot.getQuantity() > 0)
                .map(lot -> lot(profile, lot, soldOn, year, regime)).toList();
    }

    private HeldAssetsView.Lot lot(TaxpayerProfile profile, AssetEntity lot, LocalDate soldOn, TaxYearId year, RegimeCode regime) {
        try {
            TaxCalculator.HeldTerm term = termIfSold(profile, lot, soldOn, year, regime);
            Turning turning = term.longTermFrom().isAfter(year.endsOn()) ? decidedLater(profile, lot, term.longTermFrom())
                    : new Turning(term.longTermFrom(), null);
            return described(lot, term, turning);
        } catch (IllegalArgumentException e) {
            return described(lot, null, new Turning(null, e.getMessage()));
        }
    }

    /**
     * The turning date where it falls after the year read: that later year's row decides it, so the date is read from
     * that row, and where no pack ships for the year no date is given (D-533 C1a).
     */
    private Turning decidedLater(TaxpayerProfile profile, AssetEntity lot, LocalDate candidate) {
        TaxYearId later = TaxYearId.containing(candidate);
        if (taxCalculator.packVersions(later).isEmpty()) {
            return new Turning(null, "TY " + later.label() + " decides when this lot turns long-term and no rule pack ships for it");
        }
        return new Turning(termIfSold(profile, lot, candidate, later, taxCalculator.defaultRegime(later)).longTermFrom(), null);
    }

    private TaxCalculator.HeldTerm termIfSold(TaxpayerProfile profile, AssetEntity lot, LocalDate soldOn, TaxYearId year,
            RegimeCode regime) {
        CapitalGainsInput.BucketFacts facts = new CapitalGainsInput.BucketFacts(lot.getAssetType().name(), "UNKNOWN", "UNKNOWN",
                "PURCHASE", Optional.empty(), lot.getTransactionDate(), soldOn, CapitalGainsInput.SideVenue.UNRECORDED,
                new CapitalGainsInput.SideVenue(Optional.ofNullable(lot.getExchangeName()), Optional.empty()));
        CapitalGainsInput gains = CapitalGainsInput.none(soldOn.atStartOfDay(ZoneOffset.UTC).toInstant(), "held-lot-what-if");
        return taxCalculator.termIfSold(assembler.forCapitalGains(profile, year, regime, soldOn, gains), facts);
    }

    private static HeldAssetsView.Lot described(AssetEntity lot, TaxCalculator.HeldTerm term, Turning turning) {
        String broker = lot.getBrokerName() == null ? null : lot.getBrokerName().name();
        return new HeldAssetsView.Lot(lot.getStockCode(), broker, lot.getAccountHolder(), lot.getQuantity(), lot.getTransactionDate(),
                term == null ? null : term.term(), term == null ? null : term.row(), term == null ? null : term.months(),
                turning.longTermFrom(), turning.note());
    }

    private static HeldAssetsView.StockTotal total(String stock, List<HeldAssetsView.Lot> all) {
        List<HeldAssetsView.Lot> ofStock = all.stream().filter(lot -> stock.equals(lot.stockCode())).toList();
        return new HeldAssetsView.StockTotal(stock, quantity(ofStock, CapitalGainsInput.Term.LONG_TERM.name()),
                quantity(ofStock, CapitalGainsInput.Term.SHORT_TERM.name()), quantity(ofStock, null));
    }

    private static double quantity(List<HeldAssetsView.Lot> lots, String term) {
        return lots.stream().filter(lot -> Objects.equals(term, lot.term())).mapToDouble(HeldAssetsView.Lot::quantity).sum();
    }

    /** When a lot turns long-term, or why that is not given. */
    private record Turning(LocalDate longTermFrom, String note) {
    }
}
