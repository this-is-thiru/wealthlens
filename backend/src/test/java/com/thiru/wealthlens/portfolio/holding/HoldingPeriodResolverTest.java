package com.thiru.wealthlens.portfolio.holding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.CapitalGainsType;
import com.thiru.wealthlens.portfolio.entity.HoldingPeriodPolicyEntity;
import com.thiru.wealthlens.portfolio.repository.HoldingPeriodPolicyRepository;
import com.thiru.wealthlens.shared.util.collection.TJsonMapper;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ClassPathResource;

/**
 * Classifying a disposal as short-term, long-term, or neither.
 *
 * <p>The rules this encodes are worked through in {@code holding-period-analysis.md}. Three of them
 * shape the design and are each pinned below:
 *
 * <ul>
 *   <li><b>A month count is not enough.</b> Some assets are short-term however long they are held,
 *       so the outcome has to be able to say so rather than return a threshold nobody can meet.
 *   <li><b>Two different dates govern.</b> The 12/24-month framework keys on the date of transfer;
 *       the specified-mutual-fund rule keys on the date of acquisition. A policy that applies its
 *       effective date to the wrong one misclassifies a whole class of trades.
 *   <li><b>Unknown is an answer.</b> A mutual fund whose category is not on file cannot be
 *       classified, and saying so beats guessing — the same argument {@code ChargeResolution} makes
 *       for a charge that could not be priced.
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class HoldingPeriodResolverTest {

    @Mock
    private HoldingPeriodPolicyRepository repository;

    private HoldingPeriodResolver resolver;

    /**
     * Seeded from the shipped JSON, so these tests exercise the policies that actually ship rather
     * than a convenient fixture. A rule that is wrong in the file is wrong here.
     */
    @BeforeEach
    void loadShippedPolicies() throws Exception {
        String json = new String(new ClassPathResource(
                "data/holding-periods/holding-period-policies.json").getInputStream().readAllBytes());
        when(repository.findAll()).thenReturn(
                TJsonMapper.readAsList(json, HoldingPeriodPolicyEntity.class));
        resolver = new HoldingPeriodResolver(repository);
    }

    private CapitalGainsType classify(AssetType assetType, String subClass, LocalDate bought, LocalDate sold) {
        return resolver.classify(new HoldingPeriodQuery(assetType, subClass, bought, sold)).capitalGainsType();
    }

    // ---------------------------------------------------------------- listed equity

    @Test
    void listedEquity_heldOverTwelveMonths_isLongTerm() {
        assertThat(classify(AssetType.EQUITY, null, LocalDate.of(2024, 1, 1), LocalDate.of(2025, 6, 1)))
                .isEqualTo(CapitalGainsType.LONG_TERM);
    }

    @Test
    void listedEquity_heldUnderTwelveMonths_isShortTerm() {
        assertThat(classify(AssetType.EQUITY, null, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 6, 1)))
                .isEqualTo(CapitalGainsType.SHORT_TERM);
    }

    // ------------------------------------------------- the 23 July 2024 framework change

    /**
     * The same holding, sold either side of the change. Thirty months was short-term under the
     * 36-month rule and is long-term under the 24-month one — and the rule is chosen by the date of
     * the <em>sale</em>, not the purchase.
     */
    @Test
    void nonEquity_heldThirtyMonths_soldBeforeTheChange_isShortTerm() {
        assertThat(classify(AssetType.BOND, "UNLISTED", LocalDate.of(2022, 1, 1), LocalDate.of(2024, 7, 1)))
                .isEqualTo(CapitalGainsType.SHORT_TERM);
    }

    @Test
    void nonEquity_heldThirtyMonths_soldAfterTheChange_isLongTerm() {
        assertThat(classify(AssetType.MUTUAL_FUND, "OTHER", LocalDate.of(2022, 6, 1), LocalDate.of(2024, 12, 1)))
                .isEqualTo(CapitalGainsType.LONG_TERM);
    }

    // ------------------------------------------------------- deemed short-term, however long held

    /**
     * A specified mutual fund acquired on or after 1 April 2023 is short-term whatever the holding
     * period. Five years here, and still short-term — which no month threshold could express.
     */
    @Test
    void specifiedMutualFund_acquiredAfterApril2023_isShortTermHoweverLongHeld() {
        assertThat(classify(AssetType.MUTUAL_FUND, "SPECIFIED", LocalDate.of(2023, 4, 1), LocalDate.of(2028, 4, 1)))
                .isEqualTo(CapitalGainsType.SHORT_TERM);
    }

    /** Keyed on acquisition: bought the day before the rule began, so it escapes it. */
    @Test
    void specifiedMutualFund_acquiredBeforeApril2023_followsTheOrdinaryFramework() {
        assertThat(classify(AssetType.MUTUAL_FUND, "SPECIFIED", LocalDate.of(2023, 3, 31), LocalDate.of(2026, 6, 1)))
                .isEqualTo(CapitalGainsType.LONG_TERM);
    }

    @Test
    void unlistedBond_soldAfterJuly2024_isShortTermHoweverLongHeld() {
        assertThat(classify(AssetType.BOND, "UNLISTED", LocalDate.of(2015, 1, 1), LocalDate.of(2025, 1, 1)))
                .isEqualTo(CapitalGainsType.SHORT_TERM);
    }

    // ------------------------------------------------------------------ not capital gains at all

    /** Interest income. Classifying it as a capital gain files it under the wrong head entirely. */
    @Test
    void fixedDeposit_isNotCapitalGains() {
        assertThat(classify(AssetType.FD, null, LocalDate.of(2020, 1, 1), LocalDate.of(2025, 1, 1)))
                .isEqualTo(CapitalGainsType.NOT_CAPITAL_GAINS);
    }

    // ------------------------------------------------------------------------------ unknown

    /**
     * A mutual fund whose category is not on file. Equity-oriented would be twelve months, specified
     * would be short-term regardless, and everything else twenty-four — so guessing is a coin toss
     * between three answers with different tax.
     */
    @Test
    void mutualFund_withNoCategoryOnFile_isUnclassified() {
        assertThat(classify(AssetType.MUTUAL_FUND, null, LocalDate.of(2020, 1, 1), LocalDate.of(2025, 1, 1)))
                .isEqualTo(CapitalGainsType.UNCLASSIFIED);
    }

    @Test
    void equityOrientedMutualFund_heldOverTwelveMonths_isLongTerm() {
        assertThat(classify(AssetType.MUTUAL_FUND, "EQUITY_ORIENTED", LocalDate.of(2024, 1, 1), LocalDate.of(2025, 6, 1)))
                .isEqualTo(CapitalGainsType.LONG_TERM);
    }

    // ------------------------------------------------------------- the reason it is resolved, not computed

    /** The resolution names the policy that decided it, so a figure can be traced to its rule. */
    @Test
    void classify_namesThePolicyThatDecided() {
        HoldingPeriodResolution resolution = resolver.classify(new HoldingPeriodQuery(
                AssetType.EQUITY, null, LocalDate.of(2024, 1, 1), LocalDate.of(2025, 6, 1)));

        assertThat(resolution.policyCode()).isNotBlank();
        assertThat(resolution.capitalGainsType()).isEqualTo(CapitalGainsType.LONG_TERM);
    }

    /** A boundary, because "greater than" and "at least" differ by exactly one trade. */
    @Test
    void listedEquity_soldOnTheTwelveMonthAnniversary_isStillShortTerm() {
        assertThat(classify(AssetType.EQUITY, null, LocalDate.of(2024, 6, 1), LocalDate.of(2025, 6, 1)))
                .isEqualTo(CapitalGainsType.SHORT_TERM);
    }
}
