package com.thiru.wealthlens.taxengine.node;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.input.CapitalGainsInput;
import com.thiru.wealthlens.taxengine.input.CapitalGainsInput.BucketFacts;
import com.thiru.wealthlens.taxengine.input.CapitalGainsInput.Term;
import com.thiru.wealthlens.taxengine.input.EngineInputs;
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
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The capital-gains head by part, after its own losses (S2 s.108(2); 1961 s.70(2), (3); D-243): the declared order is
 * short-term slab, short-term listed equity, long-term other, long-term listed equity. Each bucket's part is the one the
 * row of {@code RS-CG-PART} its facts meet names (D-289); the expected parts are S2 ss.196–198 and 1961 ss.111A, 112,
 * 112A's conditions, worked by hand in {@code plans/W-088f2a.md}.
 */
class CapitalGainsNodeTest {

    private static RulePack pack(int year) {
        TaxYearId taxYear = TaxYearId.of(year);
        return new ClasspathRulePackProvider("rulepacks", Set.of(taxYear),
                new JsonPackAssembler(LockedInstruments.of(taxYear))).load(taxYear);
    }

    private static final boolean PAID = true;

    private static final boolean NOT_PAID = false;

    /** An equity purchase with the given STT on record for the buy and the sale. */
    private static CapitalGainsInput.Bucket bucket(String gain, Term term, boolean sttOnBuy, boolean sttOnSale) {
        return bucket(gain, term, "EQUITY", sttOnBuy, sttOnSale);
    }

    private static CapitalGainsInput.Bucket bucket(String gain, Term term, String asset, boolean sttOnBuy,
            boolean sttOnSale) {
        BucketFacts facts = new BucketFacts(asset, sttOnBuy, sttOnSale, "PURCHASE", Optional.empty(),
                LocalDate.parse("2020-06-01"));
        return new CapitalGainsInput.Bucket(Money.of(gain), term, facts, Optional.empty());
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
        CapitalGainsHead head = run(year, bucket("50000", Term.SHORT_TERM, PAID, NOT_PAID),
                bucket("-30000", Term.SHORT_TERM, NOT_PAID, PAID),
                bucket("40000", Term.LONG_TERM, PAID, PAID));

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
        CapitalGainsHead head = run(year, bucket("-10000", Term.LONG_TERM, PAID, NOT_PAID),
                bucket("25000", Term.SHORT_TERM, PAID, NOT_PAID),
                bucket("15000", Term.LONG_TERM, PAID, PAID));

        // Then — "against … any other long-term capital asset" (S2 s.108(2)(b)): 15,000 − 10,000
        assertThat(head.part(CapitalGainsPart.SHORT_TERM_SLAB)).isEqualTo(Money.of("25000"));
        assertThat(head.part(CapitalGainsPart.LONG_TERM_LISTED_EQUITY)).isEqualTo(Money.of("5000"));
        assertThat(head.longTermLossLeft()).isEqualTo(Money.ZERO);
    }

    @Test
    void evaluate_whenTheHeadHasOnlyLosses_isNilAndShowsWhatIsLeft() {
        // When — C: short-term −5,000; long-term −8,000; nothing else
        NodeResult result = result(2026, bucket("-5000", Term.SHORT_TERM, NOT_PAID, PAID),
                bucket("-8000", Term.LONG_TERM, PAID, NOT_PAID));
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
        NodeResult result = result(2026, bucket("-20000", Term.SHORT_TERM, PAID, NOT_PAID),
                bucket("-10000", Term.LONG_TERM, PAID, PAID),
                bucket("25000", Term.LONG_TERM, PAID, NOT_PAID));
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

    @ParameterizedTest(name = "TY {0}: {1}, STT buy {2}, sale {3} → {4}")
    @CsvSource({
        "2025, SHORT_TERM, false, true, SHORT_TERM_LISTED_EQUITY",
        "2025, SHORT_TERM, true, false, SHORT_TERM_SLAB",
        "2025, LONG_TERM, true, true, LONG_TERM_LISTED_EQUITY",
        "2025, LONG_TERM, true, false, LONG_TERM_OTHER",
        "2025, LONG_TERM, false, false, LONG_TERM_OTHER",
        "2026, SHORT_TERM, false, true, SHORT_TERM_LISTED_EQUITY",
        "2026, SHORT_TERM, true, false, SHORT_TERM_SLAB",
        "2026, LONG_TERM, true, true, LONG_TERM_LISTED_EQUITY",
        "2026, LONG_TERM, true, false, LONG_TERM_OTHER",
        "2026, LONG_TERM, false, false, LONG_TERM_OTHER"
    })
    void evaluate_whenAnEquityBucketMeetsARow_isInThePartTheRowNames(int year, Term term, boolean sttOnBuy,
            boolean sttOnSale, CapitalGainsPart expected) {
        // When — cases 1–5: s.196(1)(b) / s.111A(1)(b) STT on the sale; s.198(1)(c)(i) / s.112A(1)(iii)(a) on both;
        // s.197(1) / s.112(1) any other long-term gain
        CapitalGainsHead head = run(year, bucket("1000", term, sttOnBuy, sttOnSale));

        // Then
        assertThat(head.part(expected)).isEqualTo(Money.of("1000"));
        assertThat(head.total()).isEqualTo(Money.of("1000"));
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenLongTermEquityWasBoughtWithoutStt_isRefusedNamingItsFacts(int year) {
        // When / Then — case 6: s.198(1)(c)(i) / s.112A(1)(iii)(a) unmet on the buy; S.O. 5054(E)'s waiver is not yet
        // a row (W-088f2b), so no row covers the bucket
        assertThatThrownBy(() -> run(year, bucket("1000", Term.LONG_TERM, NOT_PAID, PAID)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no row of RS-CG-PART")
                .hasMessageContaining("sttOnAcquisition=false")
                .hasMessageContaining("acquisitionMode=PURCHASE")
                .hasMessageContaining("2020-06-01");
    }

    @Test
    void evaluate_whenTheAssetIsNotEquity_isRefused() {
        // When / Then — case 7: only equity has rows; a fund unit waits on the instrument registry
        assertThatThrownBy(() -> run(2026, bucket("1000", Term.SHORT_TERM, "MUTUAL_FUND", NOT_PAID, PAID)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("assetKind=MUTUAL_FUND")
                .hasMessageContaining("no row of RS-CG-PART");
    }
}
