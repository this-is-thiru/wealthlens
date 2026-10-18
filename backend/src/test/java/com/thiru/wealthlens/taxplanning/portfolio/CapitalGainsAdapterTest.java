package com.thiru.wealthlens.taxplanning.portfolio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.thiru.wealthlens.portfolio.dto.CapitalGainsBucket;
import com.thiru.wealthlens.portfolio.dto.CapitalGainsSummary;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.CapitalGainsType;
import com.thiru.wealthlens.portfolio.service.CapitalGainsSummaryService;
import com.thiru.wealthlens.taxengine.input.CapitalGainsInput;
import com.thiru.wealthlens.taxengine.input.CapitalGainsInput.Term;
import com.thiru.wealthlens.taxengine.input.RateBasisHint;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Each read-model bucket's term and rate basis, from the conditions of S2 ss.196–198 and 1961 ss.111A, 112, 112A
 * (D-241; {@code sources/2026-10-18-capital-gains-basis.md}).
 */
@ExtendWith(MockitoExtension.class)
class CapitalGainsAdapterTest {

    private static final String EMAIL = "user@example.com";
    private static final TaxYearId YEAR = TaxYearId.of(2025);
    private static final Instant AS_OF = Instant.parse("2026-04-15T10:00:00Z");

    @Mock
    private CapitalGainsSummaryService capitalGainsSummaryService;

    @InjectMocks
    private CapitalGainsAdapter adapter;

    private static CapitalGainsBucket bucket(CapitalGainsType classification, AssetType assetType,
            boolean sttOnAcquisition, boolean sttOnTransfer, double gainRupees) {
        return new CapitalGainsBucket(classification, assetType, Optional.empty(), sttOnAcquisition, sttOnTransfer,
                gainRupees);
    }

    private static CapitalGainsBucket equity(CapitalGainsType classification, boolean sttOnAcquisition,
            boolean sttOnTransfer) {
        return bucket(classification, AssetType.EQUITY, sttOnAcquisition, sttOnTransfer, 1000.50);
    }

    private CapitalGainsInput inputOf(CapitalGainsBucket... buckets) {
        when(capitalGainsSummaryService.summaryFor(anyString(), anyString()))
                .thenReturn(new CapitalGainsSummary(EMAIL, "2025-2026", List.of(buckets), AS_OF, "trade_outcomes:ref"));
        return adapter.inputFor(EMAIL, YEAR);
    }

    private CapitalGainsInput.Bucket onlyBucketOf(CapitalGainsBucket bucket) {
        CapitalGainsInput input = inputOf(bucket);
        assertThat(input.buckets()).hasSize(1);
        return input.buckets().getFirst();
    }

    @Test
    void inputFor_whenTheYearIs2025_asksForPortfoliosFinancialYear() {
        // When
        CapitalGainsInput input = inputOf();

        // Then — portfolio's TLocalDate form; pinned to the summary's run
        verify(capitalGainsSummaryService).summaryFor(EMAIL, "2025-2026");
        assertThat(input.asOf()).isEqualTo(AS_OF);
        assertThat(input.portfolioComputationRef()).isEqualTo("trade_outcomes:ref");
    }

    @Test
    void inputFor_whenShortTermEquityWasSoldWithStt_isTheListedEquityBasis() {
        // When — S2 s.196(1); 1961 s.111A(1)
        CapitalGainsInput.Bucket bucket = onlyBucketOf(equity(CapitalGainsType.SHORT_TERM, true, true));

        // Then
        assertThat(bucket.gain()).isEqualTo(Money.of("1000.50"));
        assertThat(bucket.term()).isEqualTo(Term.SHORT_TERM);
        assertThat(bucket.basis()).isEqualTo(RateBasisHint.SPECIAL_RATE_LISTED_EQUITY);
    }

    @Test
    void inputFor_whenShortTermEquityWasSoldWithoutStt_isTheSlab() {
        // When — no special provision charges it
        CapitalGainsInput.Bucket bucket = onlyBucketOf(equity(CapitalGainsType.SHORT_TERM, true, false));

        // Then
        assertThat(bucket.term()).isEqualTo(Term.SHORT_TERM);
        assertThat(bucket.basis()).isEqualTo(RateBasisHint.SLAB);
    }

    @Test
    void inputFor_whenLongTermEquityWasBoughtAndSoldWithStt_isTheListedEquityBasis() {
        // When — S2 s.198(1); 1961 s.112A(1)
        CapitalGainsInput.Bucket bucket = onlyBucketOf(equity(CapitalGainsType.LONG_TERM, true, true));

        // Then
        assertThat(bucket.term()).isEqualTo(Term.LONG_TERM);
        assertThat(bucket.basis()).isEqualTo(RateBasisHint.SPECIAL_RATE_LISTED_EQUITY);
    }

    @Test
    void inputFor_whenLongTermEquityWasSoldWithoutStt_isTheOtherSpecialRate() {
        // When — S2 s.197(1); 1961 s.112(1)
        CapitalGainsInput.Bucket bucket = onlyBucketOf(equity(CapitalGainsType.LONG_TERM, false, false));

        // Then
        assertThat(bucket.term()).isEqualTo(Term.LONG_TERM);
        assertThat(bucket.basis()).isEqualTo(RateBasisHint.SPECIAL_RATE_OTHER);
    }

    @Test
    void inputFor_whenLongTermEquityWasBoughtWithoutStt_isRefusedNamingOq31() {
        // When / Then — which acquisitions the s.112A(4) / s.198(5) notification covers is unread
        assertThatThrownBy(() -> inputOf(equity(CapitalGainsType.LONG_TERM, false, true)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("OQ-31");
    }

    @Test
    void inputFor_whenAFundHasACapitalGainsClassification_isRefused() {
        // When / Then
        assertThatThrownBy(() -> inputOf(bucket(CapitalGainsType.SHORT_TERM, AssetType.MUTUAL_FUND, false, true, 10)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("MUTUAL_FUND have no rate-basis rule");
    }

    @Test
    void inputFor_whenAGainIsUnclassified_isRefused() {
        // When / Then — X1.3: never defaulted either way
        assertThatThrownBy(() -> inputOf(bucket(CapitalGainsType.UNCLASSIFIED, AssetType.BOND, false, false, 10)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unclassified");
    }

    @Test
    void inputFor_whenAnUnclassifiedBucketIsNil_leavesItOut() {
        // When
        CapitalGainsInput input = inputOf(bucket(CapitalGainsType.UNCLASSIFIED, AssetType.BOND, false, false, 0));

        // Then
        assertThat(input.buckets()).isEmpty();
    }

    @Test
    void inputFor_whenAGainIsSpeculative_isRefused() {
        // When / Then — business income, which this engine does not compute
        assertThatThrownBy(() -> inputOf(bucket(CapitalGainsType.SPECULATIVE, AssetType.EQUITY, true, true, 5)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("speculative business income");
    }

    @Test
    void inputFor_whenTheIncomeIsDepositInterest_leavesItOut() {
        // When — other-sources income, entered there
        CapitalGainsInput input = inputOf(bucket(CapitalGainsType.NOT_CAPITAL_GAINS, AssetType.FD, false, false, 900));

        // Then
        assertThat(input.buckets()).isEmpty();
    }
}
