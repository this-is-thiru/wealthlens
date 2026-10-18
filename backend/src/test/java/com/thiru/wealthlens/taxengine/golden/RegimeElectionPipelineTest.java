package com.thiru.wealthlens.taxengine.golden;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.taxengine.election.ElectionMode;
import com.thiru.wealthlens.taxengine.election.ElectionResolver;
import com.thiru.wealthlens.taxengine.election.PricedAlternative;
import com.thiru.wealthlens.taxengine.election.SetOffOrderElection;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.test.NodeHarness;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The regime election over the real engine (D-222): other-sources interest as the only income, so total income is the
 * interest; TY 2026-27, the harness taxpayer aged 41. The figures are {@code DR 08}'s, never computed here.
 */
class RegimeElectionPipelineTest {

    private static final GoldenCaseRunner RUNNER = new GoldenCaseRunner();
    private static final RegimeCode NEW = new RegimeCode("NEW_TAX_REGIME");
    private static final RegimeCode OLD = new RegimeCode("FIRST_SCHEDULE_RATES");

    @Test
    void resolveRegime_whenTotalIncomeIsFiveCrore_takesTheNewRegimeAtDr08sFigure() {
        // When — DR 08 T2-4: ₹5,00,00,000 nets ₹1,92,56,250 in the old regime and ₹1,89,54,000 in the new
        ElectionResolver.Resolution<RegimeCode> resolution =
                RUNNER.resolveRegime(TaxYearId.of(2026), Money.of("50000000"));

        // Then
        assertThat(resolution.outcome().mode()).isEqualTo(ElectionMode.AUTO_OPTIMISE);
        assertThat(resolution.outcome().chosen()).isEqualTo(NEW);
        assertThat(resolution.run().valueOf(NodeIds.ROUND_NETPAYABLE).asMoney()).isEqualTo(Money.of("18954000"));
        assertThat(resolution.outcome().alternatives()).containsExactly(
                new PricedAlternative<>(NEW, Money.of("18954000")), new PricedAlternative<>(OLD, Money.of("19256250")));
    }

    @Test
    void resolveRegimeAndSetOffOrder_whenTotalIncomeIsFiveCrore_pricesTheRegimesAsAlone() {
        // When — DR 08 T2-4's facts with the set-off order elected too: no loss, so every order costs the same
        ElectionResolver.JointResolution resolution =
                RUNNER.resolveRegimeAndSetOffOrder(TaxYearId.of(2026), Money.of("50000000"));

        // Then — each regime under its own pipeline (D-222): 1,89,54,000 new, 1,92,56,250 old; the declared order
        assertThat(resolution.outcomes().get(0).chosen()).isEqualTo(NEW);
        assertThat(List.<Object>copyOf(resolution.outcomes().get(0).alternatives())).containsExactly(
                new PricedAlternative<>(NEW, Money.of("18954000")), new PricedAlternative<>(OLD, Money.of("19256250")));
        assertThat(resolution.outcomes().get(1).chosen()).isEqualTo(new SetOffOrderElection().candidates(
                NodeHarness.plainTaxpayer(), RUNNER.pack(TaxYearId.of(2026))).getFirst());
        assertThat(resolution.run().valueOf(NodeIds.ROUND_NETPAYABLE).asMoney()).isEqualTo(Money.of("18954000"));
    }

    @Test
    void resolveRegime_whenBothRegimesNetNil_takesTheDefault() {
        // When — DR 08 T1-O1 (old) and T1-A1e (new): ₹5,00,000 nets ₹0 in each
        ElectionResolver.Resolution<RegimeCode> resolution =
                RUNNER.resolveRegime(TaxYearId.of(2026), Money.of("500000"));

        // Then
        assertThat(resolution.outcome().alternatives()).extracting(PricedAlternative::netPayable)
                .containsOnly(Money.ZERO);
        assertThat(resolution.outcome().chosen()).isEqualTo(NEW);
    }
}
