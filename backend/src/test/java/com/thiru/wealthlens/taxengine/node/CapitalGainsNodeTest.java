package com.thiru.wealthlens.taxengine.node;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.input.CapitalGainsInput;
import com.thiru.wealthlens.taxengine.input.CapitalGainsInput.BucketFacts;
import com.thiru.wealthlens.taxengine.input.CapitalGainsInput.SideVenue;
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

    private static final String PAID = "PAID";

    private static final String NOT_PAID = "NOT_PAID";

    private static final String UNKNOWN = "UNKNOWN";

    /**
     * A sale recorded on NSE with no segment: what shows the share listed for {@code RS-CG-TERM} (D-305 C1), and no
     * more — with no segment, no STT is derived from it (D-298), so a case's STT reads as it states.
     */
    private static final SideVenue SOLD_ON_NSE = new SideVenue(Optional.of("NSE"), Optional.empty());

    /** When a lot acquired on {@code acquiredOn} is sold so that the engine finds {@code term}: six months or two years on. */
    private static LocalDate soldFor(Term term, LocalDate acquiredOn) {
        return term == Term.LONG_TERM ? acquiredOn.plusYears(2) : acquiredOn.plusMonths(6);
    }

    /** An equity purchase with the given STT on record for the buy and the sale. */
    private static CapitalGainsInput.Bucket bucket(String gain, Term term, String sttOnBuy, String sttOnSale) {
        return bucket(gain, term, "EQUITY", sttOnBuy, sttOnSale);
    }

    private static CapitalGainsInput.Bucket bucket(String gain, Term term, String asset, String sttOnBuy,
            String sttOnSale) {
        LocalDate acquiredOn = LocalDate.parse("2020-06-01");
        BucketFacts facts = new BucketFacts(asset, sttOnBuy, sttOnSale, "PURCHASE", Optional.empty(), acquiredOn,
                soldFor(term, acquiredOn), SideVenue.UNRECORDED, SOLD_ON_NSE);
        return new CapitalGainsInput.Bucket(Money.of(gain), facts, Optional.empty());
    }

    /** Long-term equity sold with STT and bought without it, acquired as stated — S.O. 5054(E)'s question. */
    private static CapitalGainsInput.Bucket boughtWithoutStt(String mode, String corporateAction, String acquiredOn) {
        return soldWithStt(NOT_PAID, mode, corporateAction, acquiredOn);
    }

    /** Long-term equity sold with STT, the buy's STT as stated, acquired as stated. */
    private static CapitalGainsInput.Bucket soldWithStt(String sttOnBuy, String mode, String corporateAction,
            String acquiredOn) {
        LocalDate acquired = LocalDate.parse(acquiredOn);
        BucketFacts facts = new BucketFacts("EQUITY", sttOnBuy, PAID, mode, Optional.ofNullable(corporateAction),
                acquired, soldFor(Term.LONG_TERM, acquired), SideVenue.UNRECORDED, SOLD_ON_NSE);
        return new CapitalGainsInput.Bucket(Money.of("1000"), facts, Optional.empty());
    }

    /**
     * An equity bucket of 1000 with each side's STT and venue ("NSE/DELIVERY", "NSE" for an exchange with no segment,
     * or "-" for none on record), acquired by the mode given ("PURCHASE", or "CORPORATE_ACTION/BONUS" with its
     * action) and sold so that the term is {@code term}.
     */
    private static CapitalGainsInput.Bucket traded(Term term, String sttOnBuy, String buyVenue, String sttOnSale,
            String saleVenue, String mode, String acquiredOn) {
        String[] acquisition = mode.split("/");
        LocalDate acquired = LocalDate.parse(acquiredOn);
        BucketFacts facts = new BucketFacts("EQUITY", sttOnBuy, sttOnSale, acquisition[0],
                acquisition.length == 2 ? Optional.of(acquisition[1]) : Optional.empty(), acquired,
                soldFor(term, acquired), venue(buyVenue), venue(saleVenue));
        return new CapitalGainsInput.Bucket(Money.of("1000"), facts, Optional.empty());
    }

    /**
     * Equity of 1000 bought and sold with STT on record, acquired and transferred on the dates given, the sale on the
     * venue given ("NSE", "-" for none), acquired by the mode given — what {@code RS-CG-TERM} decides from (D-305).
     */
    private static CapitalGainsInput.Bucket held(String acquiredOn, String transferredOn, String saleVenue,
            String mode) {
        String[] acquisition = mode.split("/");
        BucketFacts facts = new BucketFacts("EQUITY", acquisition.length == 2 ? NOT_PAID : PAID, PAID, acquisition[0],
                acquisition.length == 2 ? Optional.of(acquisition[1]) : Optional.empty(), LocalDate.parse(acquiredOn),
                LocalDate.parse(transferredOn), SideVenue.UNRECORDED, venue(saleVenue));
        return new CapitalGainsInput.Bucket(Money.of("1000"), facts, Optional.empty());
    }

    private static SideVenue venue(String recorded) {
        if (recorded.equals("-")) {
            return SideVenue.UNRECORDED;
        }
        String[] parts = recorded.split("/");
        return new SideVenue(Optional.of(parts[0]), parts.length == 2 ? Optional.of(parts[1]) : Optional.empty());
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
        "2025, SHORT_TERM, NOT_PAID, PAID, SHORT_TERM_LISTED_EQUITY",
        "2025, SHORT_TERM, PAID, NOT_PAID, SHORT_TERM_SLAB",
        "2025, LONG_TERM, PAID, PAID, LONG_TERM_LISTED_EQUITY",
        "2025, LONG_TERM, PAID, NOT_PAID, LONG_TERM_OTHER",
        "2025, LONG_TERM, NOT_PAID, NOT_PAID, LONG_TERM_OTHER",
        "2026, SHORT_TERM, NOT_PAID, PAID, SHORT_TERM_LISTED_EQUITY",
        "2026, SHORT_TERM, PAID, NOT_PAID, SHORT_TERM_SLAB",
        "2026, LONG_TERM, PAID, PAID, LONG_TERM_LISTED_EQUITY",
        "2026, LONG_TERM, PAID, NOT_PAID, LONG_TERM_OTHER",
        "2026, LONG_TERM, NOT_PAID, NOT_PAID, LONG_TERM_OTHER"
    })
    void evaluate_whenAnEquityBucketMeetsARow_isInThePartTheRowNames(int year, Term term, String sttOnBuy,
            String sttOnSale, CapitalGainsPart expected) {
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
        // When / Then — case 6 (G): s.198(1)(c)(i) / s.112A(1)(iii)(a) unmet on the buy, and a purchase after
        // 1-10-2004 is outside what CG-PART-LONG-NOTIFIED can tell (S.O. 5054(E) (a)–(c)), so no row covers it
        assertThatThrownBy(() -> run(year, bucket("1000", Term.LONG_TERM, NOT_PAID, PAID)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no row of RS-CG-PART")
                .hasMessageContaining("sttOnAcquisition=NOT_PAID")
                .hasMessageContaining("acquisitionMode=PURCHASE")
                .hasMessageContaining("2020-06-01");
    }

    @ParameterizedTest(name = "TY {0}: {1} {2} on {3}")
    @CsvSource({
        "2025, PURCHASE, , 2004-09-30",
        "2025, CORPORATE_ACTION, BONUS, 2020-01-10",
        "2025, CORPORATE_ACTION, DEMERGER, 2020-01-10",
        "2025, UNRECORDED, , 2003-06-01",
        "2026, PURCHASE, , 2004-09-30",
        "2026, CORPORATE_ACTION, BONUS, 2020-01-10",
        "2026, CORPORATE_ACTION, DEMERGER, 2020-01-10",
        "2026, UNRECORDED, , 2003-06-01"
    })
    void evaluate_whenSoNotified_isTheListedLongTermPartDespiteNoSttOnTheBuy(int year, String mode,
            String corporateAction, String acquiredOn) {
        // When — cases A, C, D, F: S.O. 5054(E) clause (I) "before the 1st day of October, 2004", or (II) an
        // allotment by the company, proviso (i) (and (ix) for a demerger); D-289 C3(a)
        CapitalGainsHead head = run(year, boughtWithoutStt(mode, corporateAction, acquiredOn));

        // Then
        assertThat(head.part(CapitalGainsPart.LONG_TERM_LISTED_EQUITY)).isEqualTo(Money.of("1000"));
        assertThat(head.total()).isEqualTo(Money.of("1000"));
    }

    @ParameterizedTest(name = "TY {0}: {1} {2} on {3}")
    @CsvSource({
        "2025, PURCHASE, , 2004-10-01",
        "2025, CORPORATE_ACTION, RIGHTS_ISSUANCE, 2020-01-10",
        "2025, UNRECORDED, , 2004-10-01",
        "2026, PURCHASE, , 2004-10-01",
        "2026, CORPORATE_ACTION, RIGHTS_ISSUANCE, 2020-01-10",
        "2026, UNRECORDED, , 2004-10-01"
    })
    void evaluate_whenNotSoNotified_isRefusedNamingItsFacts(int year, String mode, String corporateAction,
            String acquiredOn) {
        // When / Then — cases B, E: on or after 1-10-2004 a purchase turns on (a)–(c), which portfolio does not
        // record, and a rights allotment is not in the row's list; neither is covered
        assertThatThrownBy(() -> run(year, boughtWithoutStt(mode, corporateAction, acquiredOn)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no row of RS-CG-PART")
                .hasMessageContaining(acquiredOn);
    }

    @ParameterizedTest(name = "TY {0}: {1}, STT buy {2}, sale {3} → {4}")
    @CsvSource({
        "2025, SHORT_TERM, UNKNOWN, PAID, SHORT_TERM_LISTED_EQUITY",
        "2025, SHORT_TERM, UNKNOWN, NOT_PAID, SHORT_TERM_SLAB",
        "2025, LONG_TERM, UNKNOWN, NOT_PAID, LONG_TERM_OTHER",
        "2026, SHORT_TERM, UNKNOWN, PAID, SHORT_TERM_LISTED_EQUITY",
        "2026, SHORT_TERM, UNKNOWN, NOT_PAID, SHORT_TERM_SLAB",
        "2026, LONG_TERM, UNKNOWN, NOT_PAID, LONG_TERM_OTHER"
    })
    void evaluate_whenTheBuysSttIsUnknownButNoRowAsksIt_isInThePartTheSaleDecides(int year, Term term,
            String sttOnBuy, String sttOnSale, CapitalGainsPart expected) {
        // When — s.196(1)(b) / s.111A(1)(b) turn on the sale only; s.197 / s.112 has no STT condition (D-292)
        CapitalGainsHead head = run(year, bucket("1000", term, sttOnBuy, sttOnSale));

        // Then
        assertThat(head.part(expected)).isEqualTo(Money.of("1000"));
    }

    @ParameterizedTest(name = "TY {0}: {1}, STT buy {2}")
    @CsvSource({
        "2025, SHORT_TERM, PAID",
        "2025, LONG_TERM, PAID",
        "2025, LONG_TERM, UNKNOWN",
        "2026, SHORT_TERM, PAID",
        "2026, LONG_TERM, PAID",
        "2026, LONG_TERM, UNKNOWN"
    })
    void evaluate_whenTheSalesSttIsUnknown_isRefusedNamingIt(int year, Term term, String sttOnBuy) {
        // When / Then — every special rate turns on the sale's STT, and no row accepts UNKNOWN there (D-292)
        assertThatThrownBy(() -> run(year, bucket("1000", term, sttOnBuy, UNKNOWN)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no row of RS-CG-PART")
                .hasMessageContaining("sttOnTransfer=UNKNOWN");
    }

    @ParameterizedTest(name = "TY {0}: {1} {2} on {3}")
    @CsvSource({
        "2025, PURCHASE, , 2003-06-01",
        "2025, CORPORATE_ACTION, BONUS, 2020-01-10",
        "2026, PURCHASE, , 2003-06-01",
        "2026, CORPORATE_ACTION, BONUS, 2020-01-10"
    })
    void evaluate_whenTheBuysSttIsUnknownAndTheWaiverSettlesIt_isTheListedLongTermPart(int year, String mode,
            String corporateAction, String acquiredOn) {
        // When — S.O. 5054(E): before 1-10-2004 no STT existed, and an allotment is not chargeable to it, so the
        // waiver's own facts decide (D-292 C2(a))
        CapitalGainsHead head = run(year, soldWithStt(UNKNOWN, mode, corporateAction, acquiredOn));

        // Then
        assertThat(head.part(CapitalGainsPart.LONG_TERM_LISTED_EQUITY)).isEqualTo(Money.of("1000"));
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenAPurchaseSince2004HasAnUnknownBuy_isRefused(int year) {
        // When / Then — s.198(1)(c)(i) turns on the buy, and the waiver cannot settle a purchase
        assertThatThrownBy(() -> run(year, soldWithStt(UNKNOWN, "PURCHASE", null, "2020-06-01")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sttOnAcquisition=UNKNOWN");
    }

    @Test
    void evaluate_whenTheAssetIsNotEquity_isRefused() {
        // When / Then — case 7: only equity has rows; a fund unit waits on the instrument registry, and since W-088k3
        // its term is refused first — no holding period is shipped for a fund (D-305 C2)
        assertThatThrownBy(() -> run(2026, bucket("1000", Term.SHORT_TERM, "MUTUAL_FUND", NOT_PAID, PAID)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("assetKind=MUTUAL_FUND")
                .hasMessageContaining("no row of RS-CG-TERM");
    }

    @ParameterizedTest(name = "TY {0}: {1}, buy {2}, sale {3} → {6}")
    @CsvSource({
        "2025, LONG_TERM, UNKNOWN@NSE/DELIVERY, PAID@NSE, PURCHASE, 2020-01-10, LONG_TERM_LISTED_EQUITY",
        "2025, LONG_TERM, UNKNOWN@BSE/DELIVERY, PAID@NSE, PURCHASE, 2020-01-10, LONG_TERM_LISTED_EQUITY",
        "2025, LONG_TERM, PAID@-, UNKNOWN@NSE/DELIVERY, PURCHASE, 2020-01-10, LONG_TERM_LISTED_EQUITY",
        "2025, SHORT_TERM, UNKNOWN@-, UNKNOWN@NSE/DELIVERY, PURCHASE, 2025-01-10, SHORT_TERM_LISTED_EQUITY",
        "2025, SHORT_TERM, PAID@-, UNKNOWN@NSE/INTRADAY, PURCHASE, 2025-01-10, SHORT_TERM_LISTED_EQUITY",
        "2025, LONG_TERM, UNKNOWN@NSE/DELIVERY, UNKNOWN@NSE/DELIVERY, UNRECORDED, 2003-06-01, LONG_TERM_LISTED_EQUITY",
        "2026, LONG_TERM, UNKNOWN@NSE/DELIVERY, PAID@NSE, PURCHASE, 2020-01-10, LONG_TERM_LISTED_EQUITY",
        "2026, LONG_TERM, UNKNOWN@BSE/DELIVERY, PAID@NSE, PURCHASE, 2020-01-10, LONG_TERM_LISTED_EQUITY",
        "2026, LONG_TERM, PAID@-, UNKNOWN@NSE/DELIVERY, PURCHASE, 2020-01-10, LONG_TERM_LISTED_EQUITY",
        "2026, SHORT_TERM, UNKNOWN@-, UNKNOWN@NSE/DELIVERY, PURCHASE, 2025-01-10, SHORT_TERM_LISTED_EQUITY",
        "2026, SHORT_TERM, PAID@-, UNKNOWN@NSE/INTRADAY, PURCHASE, 2025-01-10, SHORT_TERM_LISTED_EQUITY",
        "2026, LONG_TERM, UNKNOWN@NSE/DELIVERY, UNKNOWN@NSE/DELIVERY, UNRECORDED, 2003-06-01, LONG_TERM_LISTED_EQUITY"
    })
    void evaluate_whenAnUnknownSideWasChargeable_isInThePartTheLawGives(int year, Term term, String buy, String sale,
            String mode, String acquiredOn, CapitalGainsPart expected) {
        // When — cases 1, 2, 6, 7, 8, 9 (each side "evidence@venue"): Chapter VII s.98 Sl. 1–3 on NSE / BSE (D-297);
        // s.196 "chargeable"; s.198 "paid" by the owner's ruling D-294 (D-298)
        String[] b = buy.split("@");
        String[] s = sale.split("@");
        CapitalGainsHead head = run(year, traded(term, b[0], b[1], s[0], s[1], mode, acquiredOn));

        // Then
        assertThat(head.part(expected)).isEqualTo(Money.of("1000"));
    }

    @ParameterizedTest(name = "TY {0}: buy {1} {2}")
    @CsvSource({
        "2025, UNKNOWN, -",
        "2025, UNKNOWN, NSE IFSC Limited/DELIVERY",
        "2025, NOT_PAID, NSE/DELIVERY",
        "2026, UNKNOWN, -",
        "2026, UNKNOWN, NSE IFSC Limited/DELIVERY",
        "2026, NOT_PAID, NSE/DELIVERY"
    })
    void evaluate_whenTheBuysSttIsNotShownChargeable_isRefused(int year, String sttOnBuy, String buyVenue) {
        // When / Then — cases 3, 4, 5: no venue; an exchange not on the recognised list; a record that says
        // NOT_PAID stands (D-298 C2a) — and a 2020 purchase is outside S.O. 5054(E)
        assertThatThrownBy(() -> run(year, traded(Term.LONG_TERM, sttOnBuy, buyVenue, PAID, "NSE", "PURCHASE",
                "2020-01-10")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no row of RS-CG-PART");
    }

    @Test
    void evaluate_whenALongTermBuysSttIsDerived_tracesTheRowAndTheRuling() {
        // When — case 1
        NodeResult result = result(2026, traded(Term.LONG_TERM, UNKNOWN, "NSE/DELIVERY", PAID, "NSE", "PURCHASE",
                "2020-01-10"));

        // Then
        assertThat(result.trace()).filteredOn(line -> line.rule().equals(RuleIds.STT_PURCHASE_DELIVERY))
                .singleElement()
                .satisfies(line -> {
                    assertThat(line.kind()).isEqualTo(StepKind.MEASURE);
                    assertThat(line.limitedBy().orElseThrow()).startsWith("STT on the buy: CHARGEABLE (NSE, DELIVERY)")
                            .contains("by the owner's ruling D-294");
                });
    }

    @Test
    void evaluate_whenAShortTermSalesSttIsDerived_tracesTheRowWithoutTheRuling() {
        // When — case 7: s.196 asks for "chargeable" itself, so no ruling is needed
        NodeResult result = result(2026, traded(Term.SHORT_TERM, UNKNOWN, "-", UNKNOWN, "NSE/DELIVERY", "PURCHASE",
                "2025-01-10"));

        // Then
        assertThat(result.trace()).filteredOn(line -> line.rule().equals(RuleIds.STT_SALE_DELIVERY))
                .singleElement()
                .satisfies(line -> assertThat(line.limitedBy().orElseThrow())
                        .isEqualTo("STT on the sale: CHARGEABLE (NSE, DELIVERY)"));
    }

    @ParameterizedTest(name = "TY {0}: {1}")
    @CsvSource({
        "2025, CORPORATE_ACTION/BONUS",
        "2025, CORPORATE_ACTION/DEMERGER",
        "2026, CORPORATE_ACTION/BONUS",
        "2026, CORPORATE_ACTION/DEMERGER"
    })
    void evaluate_whenAnAllotmentIsOnAnExchangeVenue_isListedUnderTheNotificationNotSl1(int year, String mode) {
        // When — W-088j2e cases 2, 3: an allotment inherits its parent's exchange and the DELIVERY default, but Sl. 1
        // charges a "Purchase" only (D-300); S.O. 5054(E) proviso (i), (ix) settles it instead
        NodeResult result = result(year, traded(Term.LONG_TERM, UNKNOWN, "NSE/DELIVERY", PAID, "NSE", mode,
                "2020-01-10"));

        // Then
        assertThat(((CapitalGainsHead) result.value()).part(CapitalGainsPart.LONG_TERM_LISTED_EQUITY))
                .isEqualTo(Money.of("1000"));
        assertThat(result.trace()).noneMatch(line -> line.rule().equals(RuleIds.STT_PURCHASE_DELIVERY));
    }

    @ParameterizedTest(name = "TY {0}: {1}")
    @CsvSource({
        "2025, CORPORATE_ACTION/RIGHTS_ISSUANCE",
        "2025, UNRECORDED",
        "2026, CORPORATE_ACTION/RIGHTS_ISSUANCE",
        "2026, UNRECORDED"
    })
    void evaluate_whenABuyOnAnExchangeVenueIsNotShownAPurchase_isRefusedNamingTheMode(int year, String mode) {
        // When / Then — W-088j2e cases 4, 5: neither Sl. 1 (not shown a purchase, D-300) nor S.O. 5054(E) as applied
        // (a 2020 lot that is not a bonus or demerger allotment) covers the buy
        assertThatThrownBy(() -> run(year, traded(Term.LONG_TERM, UNKNOWN, "NSE/DELIVERY", PAID, "NSE", mode,
                "2020-01-10")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no row of RS-CG-PART")
                .hasMessageContaining("acquisitionMode=" + mode.split("/")[0]);
    }

    @ParameterizedTest(name = "TY {0}: a purchase on {1} → Sl. 1 traced {2}")
    @CsvSource({
        "2025, 2003-06-01, false",
        "2025, 2004-09-30, false",
        "2025, 2004-10-01, true",
        "2026, 2003-06-01, false",
        "2026, 2004-09-30, false",
        "2026, 2004-10-01, true"
    })
    void evaluate_whenAPurchaseOnAnExchangeVenuePredatesChapterVii_isListedUnderTheNotificationNotSl1(int year,
            String acquiredOn, boolean tracedUnderSl1) {
        // When — W-088j2f cases 1–3: Chapter VII came into force on 1-10-2004 (S.O. 1058(E), D-301) and applies to
        // transactions entered into on or after it (s.96(3)); before it, S.O. 5054(E) clause (I) settles the buy
        NodeResult result = result(year, traded(Term.LONG_TERM, UNKNOWN, "NSE/DELIVERY", PAID, "NSE", "PURCHASE",
                acquiredOn));

        // Then
        assertThat(((CapitalGainsHead) result.value()).part(CapitalGainsPart.LONG_TERM_LISTED_EQUITY))
                .isEqualTo(Money.of("1000"));
        assertThat(result.trace().stream().anyMatch(line -> line.rule().equals(RuleIds.STT_PURCHASE_DELIVERY)))
                .isEqualTo(tracedUnderSl1);
    }

    @ParameterizedTest(name = "TY {0}: {1} → {2} on {3} → {4}")
    @CsvSource({
        "2025, 2024-04-10, 2025-04-10, NSE, SHORT_TERM_LISTED_EQUITY",
        "2025, 2024-04-10, 2025-04-11, NSE, LONG_TERM_LISTED_EQUITY",
        "2025, 2023-03-01, 2024-03-01, BSE, SHORT_TERM_LISTED_EQUITY",
        "2026, 2024-04-10, 2025-04-10, NSE, SHORT_TERM_LISTED_EQUITY",
        "2026, 2024-04-10, 2025-04-11, NSE, LONG_TERM_LISTED_EQUITY",
        "2026, 2023-03-01, 2024-03-01, BSE, SHORT_TERM_LISTED_EQUITY"
    })
    void evaluate_whenAListedShareIsHeld_isLongTermOnlyAfterTheSameDateTwelveMonthsOn(int year, String acquiredOn,
            String transferredOn, String saleVenue, CapitalGainsPart expected) {
        // When — W-088k3 cases 1–3: 1961 s.2(42A) first proviso / 2025 s.2(101)(b)(i), twelve months, counted in
        // calendar months (D-303): exactly twelve months is "not more than", so short-term; 1-3-2023 → 1-3-2024 is
        // 366 days but twelve months
        CapitalGainsHead head = run(year, held(acquiredOn, transferredOn, saleVenue, "PURCHASE"));

        // Then
        assertThat(head.part(expected)).isEqualTo(Money.of("1000"));
        assertThat(head.total()).isEqualTo(Money.of("1000"));
    }

    @ParameterizedTest(name = "TY {0}: sold on {1}")
    @CsvSource({
        "2025, -",
        "2025, NSE IFSC Limited",
        "2026, -",
        "2026, NSE IFSC Limited"
    })
    void evaluate_whenTheSaleIsNotShownOnARecognisedExchange_isRefusedForTheTerm(int year, String saleVenue) {
        // When / Then — W-088k3 cases 4, 5: nothing shows the share listed (D-305 C1a), and no row gives another
        // period (C2a)
        assertThatThrownBy(() -> run(year, held("2023-04-03", "2025-06-10", saleVenue, "PURCHASE")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no row of RS-CG-TERM");
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenTwoSalesFallEitherSideOfTheBoundary_placesEachInItsOwnTerm(int year) {
        // When — W-088k3 case 8: one lot, sold on the anniversary and the day after
        CapitalGainsHead head = run(year, held("2024-04-10", "2025-04-10", "NSE", "PURCHASE"),
                held("2024-04-10", "2025-04-11", "NSE", "PURCHASE"));

        // Then
        assertThat(head.part(CapitalGainsPart.SHORT_TERM_LISTED_EQUITY)).isEqualTo(Money.of("1000"));
        assertThat(head.part(CapitalGainsPart.LONG_TERM_LISTED_EQUITY)).isEqualTo(Money.of("1000"));
    }

    @Test
    void evaluate_whenTheTermIsDecided_tracesTheRowAndTheBoundary() {
        // When — case 2
        NodeResult result = result(2026, held("2024-04-10", "2025-04-11", "NSE", "PURCHASE"));

        // Then
        assertThat(result.trace()).filteredOn(line -> line.rule().equals(RuleIds.CG_TERM_LISTED_SECURITY))
                .singleElement()
                .satisfies(line -> {
                    assertThat(line.kind()).isEqualTo(StepKind.MEASURE);
                    assertThat(line.limitedBy().orElseThrow()).isEqualTo(
                            "Held 2024-04-10 → 2025-04-11: more than 12 months, LONG_TERM (calendar months, D-303)");
                });
    }

    @Test
    void evaluate_whenABonusLotIsHeld_tracesTheExDateApproximation() {
        // When — case 6: a bonus lot is dated at its ex-date until W-088k4 (Explanation 1(i)(f))
        NodeResult result = result(2026, held("2024-06-05", "2025-06-06", "NSE", "CORPORATE_ACTION/BONUS"));

        // Then
        assertThat(((CapitalGainsHead) result.value()).part(CapitalGainsPart.LONG_TERM_LISTED_EQUITY))
                .isEqualTo(Money.of("1000"));
        assertThat(result.trace()).filteredOn(line -> line.rule().equals(RuleIds.CG_TERM_LISTED_SECURITY))
                .singleElement()
                .satisfies(line -> assertThat(line.limitedBy().orElseThrow())
                        .endsWith("; a bonus lot is dated at its ex-date, not its allotment (W-088k4)"));
    }
}
