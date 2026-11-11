package com.thiru.wealthlens.taxplanning.compute;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
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
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The held lots by term (W-262). The engine is a mock answering what TY 2026-27's twelve-month row would for each lot,
 * worked by hand: the service's own part is which lots it lists, what it totals, and when it gives a turning date.
 * Read on 6 November 2026, in TY 2026-27, which ends 31 March 2027.
 */
@ExtendWith(MockitoExtension.class)
class HeldAssetsViewServiceTest {

    private static final String EMAIL = "holder@example.com";
    private static final String ROW = "CG-TERM-LISTED-SECURITY";
    private static final LocalDate READ_ON = LocalDate.of(2026, 11, 6);
    private static final RegimeCode REGIME = new RegimeCode("NEW_TAX_REGIME");

    @Mock
    private TaxProfileService taxProfileService;
    @Mock
    private EngineInputsAssembler assembler;
    @Mock
    private PortfolioRepository portfolioRepository;
    @Mock
    private TaxCalculator taxCalculator;

    private HeldAssetsViewService service;
    private final TaxpayerProfile profile = new TaxpayerProfile();

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-11-06T06:30:00Z"), ZoneId.of("Asia/Kolkata"));
        service = new HeldAssetsViewService(taxProfileService, assembler, portfolioRepository, taxCalculator, clock);
        profile.setHoldsPortfolio(true);
        when(taxProfileService.owned(EMAIL, "p1")).thenReturn(profile);
        lenient().when(taxCalculator.defaultRegime(TaxYearId.of(2026))).thenReturn(REGIME);
    }

    private static AssetEntity lot(String stock, String acquiredOn, double quantity) {
        AssetEntity lot = new AssetEntity();
        lot.setStockCode(stock);
        lot.setAssetType(AssetType.EQUITY);
        lot.setExchangeName("NSE");
        lot.setBrokerName(BrokerName.ZERODHA);
        lot.setAccountHolder("Asha");
        lot.setQuantity(quantity);
        lot.setTransactionDate(LocalDate.parse(acquiredOn));
        return lot;
    }

    private void engineAnswers(String acquiredOn, LocalDate soldOn, String term, String longTermFrom) {
        when(taxCalculator.termIfSold(any(), argThat(facts -> facts != null && facts.acquiredOn().equals(LocalDate.parse(acquiredOn))
                && facts.transferredOn().equals(soldOn))))
                .thenReturn(new TaxCalculator.HeldTerm(term, ROW, 12, LocalDate.parse(longTermFrom)));
    }

    /** Lots A – D of the plan's table, a lot sold out, and a second stock. */
    private void theHoldings() {
        when(portfolioRepository.findByEmail(EMAIL)).thenReturn(List.of(lot("HELD", "2025-06-01", 10), lot("HELD", "2026-03-01", 5),
                lot("HELD", "2026-05-01", 3), lot("HELD", "2025-01-01", 2), lot("HELD", "2024-01-01", 0), lot("OTHR", "2026-10-01", 7)));
        engineAnswers("2025-06-01", READ_ON, "LONG_TERM", "2026-06-02");
        engineAnswers("2026-03-01", READ_ON, "SHORT_TERM", "2027-03-02");
        engineAnswers("2026-05-01", READ_ON, "SHORT_TERM", "2027-05-02");
        engineAnswers("2026-10-01", READ_ON, "SHORT_TERM", "2027-10-02");
        when(taxCalculator.termIfSold(any(), argThat(facts -> facts != null && facts.acquiredOn().equals(LocalDate.of(2025, 1, 1)))))
                .thenThrow(new IllegalArgumentException("No row covers this lot"));
        when(taxCalculator.packVersions(TaxYearId.of(2027))).thenReturn(Optional.empty());
    }

    @Test
    void view_whenNoTermIsAsked_listsEveryHeldLotWithItsTermAndTurningDate() {
        // Given
        theHoldings();

        // When — no date given, so today by the clock
        HeldAssetsView view = service.view(EMAIL, "p1", Optional.empty(), Optional.empty());

        // Then — the sold-out lot is not listed
        String laterYear = "TY 2027-28 decides when this lot turns long-term and no rule pack ships for it";
        assertThat(view.asOf()).isEqualTo(READ_ON);
        assertThat(view.lots()).containsExactly(
                new HeldAssetsView.Lot("HELD", "ZERODHA", "Asha", 10, LocalDate.of(2025, 6, 1), "LONG_TERM", ROW, 12,
                        LocalDate.of(2026, 6, 2), null),
                new HeldAssetsView.Lot("HELD", "ZERODHA", "Asha", 5, LocalDate.of(2026, 3, 1), "SHORT_TERM", ROW, 12,
                        LocalDate.of(2027, 3, 2), null),
                new HeldAssetsView.Lot("HELD", "ZERODHA", "Asha", 3, LocalDate.of(2026, 5, 1), "SHORT_TERM", ROW, 12, null, laterYear),
                new HeldAssetsView.Lot("HELD", "ZERODHA", "Asha", 2, LocalDate.of(2025, 1, 1), null, null, null, null,
                        "No row covers this lot"),
                new HeldAssetsView.Lot("OTHR", "ZERODHA", "Asha", 7, LocalDate.of(2026, 10, 1), "SHORT_TERM", ROW, 12, null, laterYear));
        // HELD: long 10; short 5 + 3 = 8; no term 2
        assertThat(view.totals()).containsExactly(new HeldAssetsView.StockTotal("HELD", 10, 8, 2),
                new HeldAssetsView.StockTotal("OTHR", 0, 7, 0));
    }

    @Test
    void view_whenATermIsAsked_listsOnlyItsLotsAndTotalsTheirStocksWhole() {
        // Given
        theHoldings();

        // When
        HeldAssetsView view = service.view(EMAIL, "p1", Optional.of(READ_ON), Optional.of(CapitalGainsInput.Term.LONG_TERM));

        // Then — OTHR has no long-term lot, so it is not listed; HELD's totals still show all of it
        assertThat(view.lots()).extracting(HeldAssetsView.Lot::acquiredOn).containsExactly(LocalDate.of(2025, 6, 1));
        assertThat(view.totals()).containsExactly(new HeldAssetsView.StockTotal("HELD", 10, 8, 2));
    }

    @Test
    void view_whenTheTurningDateFallsInALaterYearThatShips_readsTheDateFromThatYearsRow() {
        // Given — read on 15 January 2026, in TY 2025-26 (ends 31 March 2026); its row says 2 June 2026, which is in
        // TY 2026-27, whose row here says otherwise: that year's answer is the one given
        LocalDate readOn = LocalDate.of(2026, 1, 15);
        when(taxCalculator.defaultRegime(TaxYearId.of(2025))).thenReturn(REGIME);
        when(portfolioRepository.findByEmail(EMAIL)).thenReturn(List.of(lot("HELD", "2025-06-01", 10)));
        engineAnswers("2025-06-01", readOn, "SHORT_TERM", "2026-06-02");
        when(taxCalculator.packVersions(TaxYearId.of(2026))).thenReturn(Optional.of(List.of("law")));
        engineAnswers("2025-06-01", LocalDate.of(2026, 6, 2), "SHORT_TERM", "2027-06-02");

        // When
        HeldAssetsView view = service.view(EMAIL, "p1", Optional.of(readOn), Optional.empty());

        // Then
        assertThat(view.lots()).singleElement().satisfies(lot -> {
            assertThat(lot.term()).isEqualTo("SHORT_TERM");
            assertThat(lot.longTermFrom()).isEqualTo(LocalDate.of(2027, 6, 2));
            assertThat(lot.note()).isNull();
        });
    }

    @Test
    void view_whenTheProfileDoesNotHoldThePortfolio_refuses() {
        // Given
        profile.setHoldsPortfolio(false);

        // When / Then
        assertThatThrownBy(() -> service.view(EMAIL, "p1", Optional.empty(), Optional.empty()))
                .isInstanceOf(BadRequestException.class).hasMessage("This profile does not hold the account's portfolio");
    }
}
