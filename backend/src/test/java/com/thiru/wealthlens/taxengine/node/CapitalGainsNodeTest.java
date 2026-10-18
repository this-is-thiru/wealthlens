package com.thiru.wealthlens.taxengine.node;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.input.CapitalGainsInput;
import com.thiru.wealthlens.taxengine.input.CapitalGainsInput.Term;
import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.input.RateBasisHint;
import com.thiru.wealthlens.taxengine.pipeline.CapitalGainsHead;
import com.thiru.wealthlens.taxengine.pipeline.NodeResult;
import com.thiru.wealthlens.taxengine.rules.definition.CapitalGainsPart;
import com.thiru.wealthlens.taxengine.rules.generated.RuleIds;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.json.JsonPackAssembler;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.RulePack;
import com.thiru.wealthlens.taxengine.rules.temporal.TemporalContext;
import com.thiru.wealthlens.taxengine.rules.timeline.ClasspathRulePackProvider;
import com.thiru.wealthlens.taxengine.test.LockedInstruments;
import com.thiru.wealthlens.taxengine.test.NodeHarness;
import com.thiru.wealthlens.taxengine.trace.StepKind;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The capital-gains head by part, after its own losses (S2 s.108(2); 1961 s.70(2), (3); D-243): the declared order is
 * short-term slab, short-term listed equity, long-term other, long-term listed equity.
 */
class CapitalGainsNodeTest {

    private static RulePack pack(int year) {
        TaxYearId taxYear = TaxYearId.of(year);
        return new ClasspathRulePackProvider("rulepacks", Set.of(taxYear),
                new JsonPackAssembler(LockedInstruments.of(taxYear))).load(taxYear);
    }

    private static CapitalGainsInput.Bucket bucket(String gain, Term term, RateBasisHint basis) {
        return new CapitalGainsInput.Bucket(Money.of(gain), term, basis, Optional.empty());
    }

    private static CapitalGainsHead run(int year, CapitalGainsInput.Bucket... buckets) {
        return (CapitalGainsHead) result(year, buckets).value();
    }

    private static NodeResult result(int year, CapitalGainsInput.Bucket... buckets) {
        TaxYearId taxYear = TaxYearId.of(year);
        EngineInputs plain = NodeHarness.plainTaxpayer();
        EngineInputs inputs = new EngineInputs(taxYear, plain.regime(),
                TemporalContext.forTaxYear(taxYear, taxYear.startsOn().plusMonths(5)), plain.entered(),
                plain.assessee(), plain.family(), plain.ledgers(), plain.elections(),
                new CapitalGainsInput(List.of(buckets), Instant.parse("2026-04-15T10:00:00Z"), "run-1"),
                plain.filing());
        return NodeHarness.with(pack(year)).regime(plain.regime()).inputs(inputs).run(new CapitalGainsNode());
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenAShortTermLossMeetsGainsOnSeveralParts_takesTheSlabPartFirst(int year) {
        // When — A: short-term slab 50,000; short-term listed −30,000; long-term listed 40,000
        CapitalGainsHead head = run(year, bucket("50000", Term.SHORT_TERM, RateBasisHint.SLAB),
                bucket("-30000", Term.SHORT_TERM, RateBasisHint.SPECIAL_RATE_LISTED_EQUITY),
                bucket("40000", Term.LONG_TERM, RateBasisHint.SPECIAL_RATE_LISTED_EQUITY));

        // Then — 50,000 − 30,000 = 20,000 slab; the listed long-term part untouched
        assertThat(head.part(CapitalGainsPart.SHORT_TERM_SLAB)).isEqualTo(Money.of("20000"));
        assertThat(head.part(CapitalGainsPart.SHORT_TERM_LISTED_EQUITY)).isEqualTo(Money.ZERO);
        assertThat(head.part(CapitalGainsPart.LONG_TERM_LISTED_EQUITY)).isEqualTo(Money.of("40000"));
        assertThat(head.total()).isEqualTo(Money.of("60000"));
        assertThat(head.shortTermLossLeft()).isEqualTo(Money.ZERO);
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenALongTermLossMeetsAShortTermGain_leavesTheShortTermGainAlone(int year) {
        // When — B: long-term other −10,000; short-term slab 25,000; long-term listed 15,000
        CapitalGainsHead head = run(year, bucket("-10000", Term.LONG_TERM, RateBasisHint.SPECIAL_RATE_OTHER),
                bucket("25000", Term.SHORT_TERM, RateBasisHint.SLAB),
                bucket("15000", Term.LONG_TERM, RateBasisHint.SPECIAL_RATE_LISTED_EQUITY));

        // Then — "against … any other long-term capital asset" (S2 s.108(2)(b)): 15,000 − 10,000
        assertThat(head.part(CapitalGainsPart.SHORT_TERM_SLAB)).isEqualTo(Money.of("25000"));
        assertThat(head.part(CapitalGainsPart.LONG_TERM_LISTED_EQUITY)).isEqualTo(Money.of("5000"));
        assertThat(head.longTermLossLeft()).isEqualTo(Money.ZERO);
    }

    @Test
    void evaluate_whenTheHeadHasOnlyLosses_isNilAndShowsWhatIsLeft() {
        // When — C: short-term −5,000; long-term −8,000; nothing else
        NodeResult result = result(2026, bucket("-5000", Term.SHORT_TERM, RateBasisHint.SPECIAL_RATE_LISTED_EQUITY),
                bucket("-8000", Term.LONG_TERM, RateBasisHint.SPECIAL_RATE_OTHER));
        CapitalGainsHead head = (CapitalGainsHead) result.value();

        // Then — not set off against any other head (S2 s.109(2)); carried forward (S2 s.111; D-247)
        assertThat(head.total()).isEqualTo(Money.ZERO);
        assertThat(head.shortTermLossLeft()).isEqualTo(Money.of("5000"));
        assertThat(head.longTermLossLeft()).isEqualTo(Money.of("8000"));
        assertThat(result.trace()).filteredOn(line -> line.limitedBy().orElse("").contains("carried forward (S2 s.111(1)")).hasSize(2)
                .allSatisfy(line -> {
                    assertThat(line.rule()).isEqualTo(RuleIds.SETOFF_WITHIN_CAPITAL_GAINS);
                    assertThat(line.kind()).isEqualTo(StepKind.MEASURE);
                });
    }

    @Test
    void evaluate_whenThereAreBothLosses_setsTheLongTermOneOffFirst() {
        // Given — short-term −20,000, long-term −10,000; long-term other 25,000 the only gain
        NodeResult result = result(2026, bucket("-20000", Term.SHORT_TERM, RateBasisHint.SLAB),
                bucket("-10000", Term.LONG_TERM, RateBasisHint.SPECIAL_RATE_LISTED_EQUITY),
                bucket("25000", Term.LONG_TERM, RateBasisHint.SPECIAL_RATE_OTHER));
        CapitalGainsHead head = (CapitalGainsHead) result.value();

        // Then — the long-term loss can use only this part, so it goes first: 25,000 − 10,000 − 15,000 = 0, and
        // 5,000 of the short-term loss is left; the other way round 10,000 of the long-term loss would be left
        assertThat(head.part(CapitalGainsPart.LONG_TERM_OTHER)).isEqualTo(Money.ZERO);
        assertThat(head.longTermLossLeft()).isEqualTo(Money.ZERO);
        assertThat(head.shortTermLossLeft()).isEqualTo(Money.of("5000"));
        List<TraceLine> setOff = result.trace().stream().filter(line -> line.kind() == StepKind.SET_OFF).toList();
        assertThat(setOff).hasSize(2);
        assertThat(setOff.getFirst().limitedBy().orElseThrow()).startsWith("long-term loss").contains("OQ-32");
    }

    @Test
    void evaluate_whenThereAreNoBuckets_isNil() {
        // When — D
        CapitalGainsHead head = run(2026);

        // Then
        assertThat(head).isEqualTo(CapitalGainsHead.none());
    }

    @Test
    void evaluate_whenAShortTermGainIsOnTheOtherSpecialRate_isRefused() {
        // When / Then — S2 s.197 charges only long-term gains
        assertThatThrownBy(() -> run(2026, bucket("100", Term.SHORT_TERM, RateBasisHint.SPECIAL_RATE_OTHER)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("falls under no provision");
    }
}
