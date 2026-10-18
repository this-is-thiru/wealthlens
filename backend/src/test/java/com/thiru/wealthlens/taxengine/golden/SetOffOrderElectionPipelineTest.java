package com.thiru.wealthlens.taxengine.golden;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.taxengine.election.ElectionMode;
import com.thiru.wealthlens.taxengine.election.ElectionResolver;
import com.thiru.wealthlens.taxengine.election.PricedAlternative;
import com.thiru.wealthlens.taxengine.rules.condition.HeadCode;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.generated.RuleIds;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.trace.StepKind;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The set-off order resolved over the real engine (D-221): {@code DR 08} T6-2's house-property loss, with ₹1,00,000 of
 * basic pay added so that salaries and other sources can both absorb it.
 *
 * <p>Worked by hand: every order sets off the same loss against the same heads, so gross total income and every step
 * after it are the same; no dividend is in the income, so the order cannot reach the surcharge's excluding ladder
 * (D-200). Every order therefore nets the same, and the tie goes to the declared default.
 */
class SetOffOrderElectionPipelineTest {

    private static final GoldenCaseRunner RUNNER = new GoldenCaseRunner();

    @Test
    void resolveSetOffOrder_whenNoDividendIsInTheIncome_pricesEveryOrderTheSameAndTakesTheDefault() {
        // Given
        HouseAndOtherSourcesGoldenCase golden = HouseAndOtherSourcesGoldenCase.loadAll("taxengine/golden/T6").stream()
                .filter(c -> c.id().equals("T6-2")).findFirst().orElseThrow();

        // When
        ElectionResolver.Resolution<List<HeadCode>> resolution =
                RUNNER.resolveSetOffOrder(golden, TaxYearId.of(2026), Money.of("100000"));

        // Then — six orders, one price; the default chosen
        Money netPayable = resolution.run().valueOf(NodeIds.TAX_NETPAYABLE).asMoney();
        assertThat(resolution.outcome().mode()).isEqualTo(ElectionMode.AUTO_OPTIMISE);
        assertThat(resolution.outcome().alternatives()).hasSize(6).extracting(PricedAlternative::netPayable)
                .containsOnly(netPayable);
        assertThat(resolution.outcome().chosen())
                .isEqualTo(List.of(HeadCode.SALARIES, HeadCode.HOUSE_PROPERTY, HeadCode.OTHER_SOURCES));

        // and the chosen run sets the ₹2,00,000 off salaries first, the rest against other sources
        List<TraceLine> setOff = resolution.run().trace().stream()
                .filter(line -> line.rule().equals(RuleIds.SETOFF_HOUSE_PROPERTY) && line.kind() == StepKind.SET_OFF)
                .toList();
        assertThat(setOff).hasSize(2);
        assertThat(setOff.get(0).limitedBy().orElseThrow()).contains("against SALARIES");
        assertThat(setOff.get(1).limitedBy().orElseThrow()).contains("against OTHER_SOURCES");
        assertThat(setOff.get(0).out().asMoney().plus(setOff.get(1).out().asMoney())).isEqualTo(Money.of("200000"));
    }
}
