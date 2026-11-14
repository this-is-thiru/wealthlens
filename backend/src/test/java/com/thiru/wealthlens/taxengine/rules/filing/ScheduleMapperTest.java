package com.thiru.wealthlens.taxengine.rules.filing;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.taxengine.rules.condition.HeadCode;
import com.thiru.wealthlens.taxengine.rules.condition.PersonType;
import com.thiru.wealthlens.taxengine.rules.condition.ReturnFacts;
import com.thiru.wealthlens.taxengine.rules.definition.CapitalGainsPart;
import com.thiru.wealthlens.taxengine.rules.filing.FieldSource.FromCapitalGainsParts;
import com.thiru.wealthlens.taxengine.rules.filing.FieldSource.FromNodes;
import com.thiru.wealthlens.taxengine.rules.filing.FieldSource.FromRules;
import com.thiru.wealthlens.taxengine.rules.filing.FieldSource.FromTaxBasis;
import com.thiru.wealthlens.taxengine.rules.filing.FieldSource.ManualEntry;
import com.thiru.wealthlens.taxengine.rules.filing.FieldSource.SumOf;
import com.thiru.wealthlens.taxengine.rules.id.CellCode;
import com.thiru.wealthlens.taxengine.rules.id.FurnishingManner;
import com.thiru.wealthlens.taxengine.rules.id.ItrFormCode;
import com.thiru.wealthlens.taxengine.rules.id.NodeId;
import com.thiru.wealthlens.taxengine.rules.id.ReturnKind;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.id.ScheduleCode;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.registry.Provenance;
import com.thiru.wealthlens.taxengine.rules.registry.VerificationLevel;
import java.net.URI;
import java.time.LocalDate;
import java.time.MonthDay;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The fold from a computed return's figures to a form's cells (DR 21 I5; W-093, D-654), on a synthetic form: each source
 * kind once, a cell shared by two forms at different paths (T21-14), and a cell the run cannot fill (T21-15, C4a).
 */
class ScheduleMapperTest {

    private static final ScheduleMapper.Accruals NO_ACCRUALS = new ScheduleMapper.Accruals(LocalDate.of(2025, 4, 1),
            Map.of());

    private static final Provenance SOURCE = new Provenance(URI.create("https://example.invalid/w-092"),
            "Synthetic, tests only", LocalDate.of(2026, 11, 13), VerificationLevel.UNVERIFIED, "Nothing: synthetic");
    private static final NodeId GROSS_NODE = new NodeId("salary.gross");
    private static final NodeId TRAVEL_NODE = new NodeId("salary.medicaltravel");
    private static final RuleId NEW_DEDUCTION = new RuleId("SYN-STANDARD-NEW");
    private static final RuleId OLD_DEDUCTION = new RuleId("SYN-STANDARD-OLD");
    private static final CapitalGainsPart LONG = new CapitalGainsPart("LONG_TERM_LISTED_EQUITY");

    private static final ScheduleCell GROSS = new ScheduleCell(new CellCode("SYN_GROSS"), "Gross salary",
            new FromNodes(List.of(GROSS_NODE)), ScheduleCell.Sign.AS_COMPUTED);
    private static final ScheduleCell DEDUCTION = new ScheduleCell(new CellCode("SYN_DEDUCTION"), "Standard deduction",
            new FromRules(List.of(NEW_DEDUCTION, OLD_DEDUCTION), Optional.empty()), ScheduleCell.Sign.AS_COMPUTED);
    private static final ScheduleCell NET = new ScheduleCell(new CellCode("SYN_NET"), "Net salary",
            new SumOf(List.of(new SumOf.Term(GROSS.code(), false), new SumOf.Term(DEDUCTION.code(), true))), ScheduleCell.Sign.AS_COMPUTED);
    private static final ScheduleCell GAIN = new ScheduleCell(new CellCode("SYN_GAIN"), "Long-term gains",
            new FromCapitalGainsParts(List.of(LONG)), ScheduleCell.Sign.AS_COMPUTED);
    private static final ScheduleCell EMPLOYER = new ScheduleCell(new CellCode("SYN_EMPLOYER"), "TAN of employer",
            new ManualEntry("Enter the employer's TAN from Form 16; the computation does not hold it"),
            ScheduleCell.Sign.AS_COMPUTED);

    private static ItrFormSpec form(String code, String grossPath, List<ScheduleCell> others) {
        List<ScheduleField> fields = new java.util.ArrayList<>(List.of(new ScheduleField(grossPath, GROSS)));
        others.forEach(cell -> fields.add(new ScheduleField(cell.code().value(), cell)));
        return new ItrFormSpec(new ItrFormCode(code), code, 1, Set.of(PersonType.INDIVIDUAL),
                Set.of(new ReturnKind("SYN_ORIGINAL")), List.of(), List.of(), Set.of(),
                List.of(new FurnishingOption(new FurnishingManner("SYN_ELECTRONIC"), List.of(), SOURCE)),
                List.of(new ItrSchedule(new ScheduleCode("S"), "Synthetic salary", fields, List.of(), SOURCE)), SOURCE);
    }

    private static ScheduleMapper.Figures figures(Map<RuleId, Money> rules) {
        ReturnFacts returned = ReturnFacts.of(Map.of(HeadCode.SALARIES, Money.of("1000000"), HeadCode.HOUSE_PROPERTY,
                Money.ZERO, HeadCode.OTHER_SOURCES, Money.ZERO, HeadCode.CAPITAL_GAINS, Money.of("150000")),
                Map.of(LONG, Money.of("150000")), Set.of(), Map.of(), Map.of());
        return new ScheduleMapper.Figures(Map.of(GROSS_NODE, Money.of("1075000"), TRAVEL_NODE, Money.of("25000")), rules,
                returned, Optional.of(Money.of("120000")), Map.of("RATE_CG_LONG_LISTED_EQUITY", Money.of("3125")), Map.of(), Map.of(), Map.of(), Map.of(), NO_ACCRUALS);
    }

    private static FilledCell cell(List<FilledCell> cells, ScheduleCell wanted) {
        return cells.stream().filter(filled -> filled.cell().equals(wanted.code())).findFirst().orElseThrow();
    }

    @Test
    void fill_whenEverySourceIsInTheRun_givesEachCellItsFigure() {
        // When — the new regime's deduction fired, ₹75,000
        List<FilledCell> cells = ScheduleMapper.fill(form("SYN-1", "B1.i", List.of(DEDUCTION, NET, GAIN)),
                figures(Map.of(NEW_DEDUCTION, Money.of("75000"))));

        // Then — ₹10,75,000 − ₹75,000 = ₹10,00,000
        assertThat(cell(cells, GROSS).amount()).contains(Money.of("1075000"));
        assertThat(cell(cells, DEDUCTION).amount()).contains(Money.of("75000"));
        assertThat(cell(cells, NET).amount()).contains(Money.of("1000000"));
        assertThat(cell(cells, GAIN).amount()).contains(Money.of("150000"));
        assertThat(cells).extracting(FilledCell::path).containsExactly("B1.i", "SYN_DEDUCTION", "SYN_NET", "SYN_GAIN");
    }

    @Test
    void fill_whenTwoFormsPlaceOneCellAtDifferentPaths_showTheSameFigure() {
        // When — T21-14: ITR-1 prints gross salary at B1(i), ITR-2 at Schedule S item 2
        ScheduleMapper.Figures run = figures(Map.of(NEW_DEDUCTION, Money.of("75000")));
        FilledCell first = cell(ScheduleMapper.fill(form("SYN-1", "B1.i", List.of()), run), GROSS);
        FilledCell second = cell(ScheduleMapper.fill(form("SYN-2", "S.2", List.of()), run), GROSS);

        // Then
        assertThat(first.path()).isEqualTo("B1.i");
        assertThat(second.path()).isEqualTo("S.2");
        assertThat(first.amount()).isEqualTo(second.amount());
        assertThat(first.label()).isEqualTo(second.label());
    }

    @Test
    void fill_whenNoRuleOfACellApplied_saysSoAndSoDoesEverySumReadingIt() {
        // When — T21-15: neither deduction row is in the trace
        List<FilledCell> cells = ScheduleMapper.fill(form("SYN-1", "B1.i", List.of(DEDUCTION, NET)), figures(Map.of()));

        // Then — unavailable with the reason, never blank or nil (C4a)
        assertThat(cell(cells, DEDUCTION).amount()).isEmpty();
        assertThat(cell(cells, DEDUCTION).unavailableBecause())
                .contains("None of [SYN-STANDARD-NEW, SYN-STANDARD-OLD] applied in this run");
        assertThat(cell(cells, NET).amount()).isEmpty();
        assertThat(cell(cells, NET).unavailableBecause()).hasValueSatisfying(reason -> assertThat(reason)
                .startsWith("Needs SYN_DEDUCTION, which is unavailable: None of"));
    }

    @Test
    void fill_whenACellIsEnteredByThePerson_givesItsGuidance() {
        // When
        List<FilledCell> cells = ScheduleMapper.fill(form("SYN-1", "B1.i", List.of(EMPLOYER)), figures(Map.of()));

        // Then
        assertThat(cell(cells, EMPLOYER).unavailableBecause())
                .contains("Enter the employer's TAN from Form 16; the computation does not hold it");
    }

    @Test
    void fill_whenANodeWasNotComputed_saysWhich() {
        // When
        List<FilledCell> cells = ScheduleMapper.fill(form("SYN-1", "B1.i", List.of()),
                new ScheduleMapper.Figures(Map.of(), Map.of(), figures(Map.of()).returned(), Optional.empty(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), NO_ACCRUALS));

        // Then
        assertThat(cell(cells, GROSS).unavailableBecause()).contains("The node salary.gross was not computed in this run");
    }
    @Test
    void fill_whenACellSumsNodesOrReadsTaxByBasis_addsAndReadsThem() {
        // Given — Q1a: gross salary as salary.gross plus the medical-travel perquisite; Q3a: slab and one special rate
        ScheduleCell withTravel = new ScheduleCell(new CellCode("SYN_GROSS_ALL"), "Gross salary",
                new FromNodes(List.of(GROSS_NODE, TRAVEL_NODE)), ScheduleCell.Sign.AS_COMPUTED);
        ScheduleCell slab = new ScheduleCell(new CellCode("SYN_SLAB"), "Tax at normal rates",
                new FromTaxBasis(FromTaxBasis.Basis.SLAB_RATES, Optional.empty()), ScheduleCell.Sign.AS_COMPUTED);
        ScheduleCell special = new ScheduleCell(new CellCode("SYN_SPECIAL"), "Tax at 12.5%",
                new FromTaxBasis(FromTaxBasis.Basis.SPECIAL_RATE, Optional.of("RATE_CG_LONG_LISTED_EQUITY")), ScheduleCell.Sign.AS_COMPUTED);

        // When
        List<FilledCell> cells = ScheduleMapper.fill(form("SYN-1", "B1.i", List.of(withTravel, slab, special)),
                figures(Map.of()));

        // Then — ₹10,75,000 + ₹25,000
        assertThat(cell(cells, withTravel).amount()).contains(Money.of("1100000"));
        assertThat(cell(cells, slab).amount()).contains(Money.of("120000"));
        assertThat(cell(cells, special).amount()).contains(Money.of("3125"));
    }

    @Test
    void fill_whenTheFormSplitsOrFloorsASign_showsEachSide() {
        // Given — Q4a, on a loss of ₹40,000 (gross ₹10,75,000 less a ₹11,15,000 deduction)
        ScheduleCell loss = new ScheduleCell(new CellCode("SYN_LOSS"), "Income", new SumOf(List.of(
                new SumOf.Term(GROSS.code(), false), new SumOf.Term(DEDUCTION.code(), true))), ScheduleCell.Sign.AS_COMPUTED);
        ScheduleCell nil = new ScheduleCell(new CellCode("SYN_NIL"), "Income (nil if loss)", loss.source(),
                ScheduleCell.Sign.NIL_IF_LOSS);
        ScheduleCell payable = new ScheduleCell(new CellCode("SYN_PAYABLE"), "Payable", loss.source(),
                ScheduleCell.Sign.POSITIVE_PART);
        ScheduleCell refund = new ScheduleCell(new CellCode("SYN_REFUND"), "Refund", loss.source(),
                ScheduleCell.Sign.NEGATIVE_PART);

        // When
        List<FilledCell> cells = ScheduleMapper.fill(form("SYN-1", "B1.i", List.of(DEDUCTION, loss, nil, payable, refund)),
                figures(Map.of(NEW_DEDUCTION, Money.of("1115000"))));

        // Then
        assertThat(cell(cells, loss).amount()).contains(Money.of("-40000"));
        assertThat(cell(cells, nil).amount()).contains(Money.ZERO);
        assertThat(cell(cells, payable).amount()).contains(Money.ZERO);
        assertThat(cell(cells, refund).amount()).contains(Money.of("40000"));
    }
    @Test
    void fill_whenAGroupRepeatsOverItems_readsEachItemsFiguresAtItsNumberedPath() {
        // Given — Schedule HP item 1 for two properties, the head at HP.3 beside it
        ScheduleCell annualValue = new ScheduleCell(new CellCode("SYN_HP_AV"), "Annual value",
                new FieldSource.FromItemFigure("annualValue"), ScheduleCell.Sign.AS_COMPUTED);
        ScheduleCell income = new ScheduleCell(new CellCode("SYN_HP_INCOME"), "Income of the property",
                new FieldSource.FromItemFigure("income"), ScheduleCell.Sign.AS_COMPUTED);
        ScheduleCell missing = new ScheduleCell(new CellCode("SYN_HP_MISSING"), "A figure no item has",
                new FieldSource.FromItemFigure("rentalYield"), ScheduleCell.Sign.AS_COMPUTED);
        ItrSchedule hp = new ItrSchedule(new ScheduleCode("HP"), "Synthetic HP",
                List.of(new ScheduleField("HP.3", GROSS)),
                List.of(new ScheduleGroup("HOUSE_PROPERTY", "HP.1", List.of(new ScheduleField("e", annualValue),
                        new ScheduleField("k", income), new ScheduleField("z", missing)))), SOURCE);
        ItrFormSpec form = new ItrFormSpec(new ItrFormCode("SYN-2"), "SYN-2", 1, Set.of(PersonType.INDIVIDUAL),
                Set.of(new ReturnKind("SYN_ORIGINAL")), List.of(), List.of(), Set.of(),
                List.of(new FurnishingOption(new FurnishingManner("SYN_ELECTRONIC"), List.of(), SOURCE)), List.of(hp), SOURCE);
        ScheduleMapper.Figures run = new ScheduleMapper.Figures(Map.of(GROSS_NODE, Money.of("1075000")), Map.of(),
                figures(Map.of()).returned(), Optional.empty(), Map.of(), Map.of(), Map.of("HOUSE_PROPERTY", List.of(
                        Map.of("annualValue", Money.of("522000"), "income", Money.of("365400")),
                        Map.of("annualValue", Money.ZERO, "income", Money.of("-86666.67")))), Map.of(), Map.of(), NO_ACCRUALS);

        // When
        List<FilledCell> cells = ScheduleMapper.fill(form, run);

        // Then — the schedule's own field first, then the group once per property
        assertThat(cells).extracting(FilledCell::path).containsExactly("HP.3", "HP.1[1].e", "HP.1[1].k", "HP.1[1].z",
                "HP.1[2].e", "HP.1[2].k", "HP.1[2].z");
        assertThat(cells.get(1).amount()).contains(Money.of("522000"));
        assertThat(cells.get(2).amount()).contains(Money.of("365400"));
        assertThat(cells.get(4).amount()).contains(Money.ZERO);
        assertThat(cells.get(5).amount()).contains(Money.of("-86666.67"));
        assertThat(cells.get(3).unavailableBecause()).contains("The item has no figure rentalYield");
    }

    @Test
    void fill_whenAnItemFigureIsReadOutsideAGroup_saysThereIsNoItem() {
        // Given
        ScheduleCell outside = new ScheduleCell(new CellCode("SYN_OUTSIDE"), "Annual value",
                new FieldSource.FromItemFigure("annualValue"), ScheduleCell.Sign.AS_COMPUTED);

        // When
        List<FilledCell> cells = ScheduleMapper.fill(form("SYN-1", "B1.i", List.of(outside)), figures(Map.of()));

        // Then
        assertThat(cell(cells, outside).unavailableBecause()).contains("The item figure annualValue is read outside a group");
    }

    @Test
    void fill_whenACellAddsOtherSourceKinds_addsTheirGross() {
        // Given — Schedule OS 1b(i) and (ii) together: savings 14,000, deposits 1,00,000; dividends left out
        ScheduleCell interest = new ScheduleCell(new CellCode("SYN_INTEREST"), "Interest", new FieldSource.FromOtherSourceKinds(
                List.of("SAVINGS_DEPOSIT_INTEREST", "DEPOSIT_INTEREST", "OTHER_INTEREST")), ScheduleCell.Sign.AS_COMPUTED);
        ScheduleMapper.Figures run = new ScheduleMapper.Figures(Map.of(), Map.of(), figures(Map.of()).returned(),
                Optional.empty(), Map.of(), Map.of("SAVINGS_DEPOSIT_INTEREST", Money.of("14000"), "DEPOSIT_INTEREST",
                        Money.of("100000"), "DIVIDEND", Money.of("5000")), Map.of(), Map.of(), Map.of(), NO_ACCRUALS);

        // When
        List<FilledCell> cells = ScheduleMapper.fill(form("SYN-1", "B1.i", List.of(interest)), run);

        // Then — a kind not received adds nothing
        assertThat(cell(cells, interest).amount()).contains(Money.of("114000"));
    }

    private static final CapitalGainsPart SHORT_LISTED = new CapitalGainsPart("SHORT_TERM_LISTED_EQUITY");

    private static final FieldSource.Period UP_TO_15_JUNE = new FieldSource.Period(MonthDay.of(4, 1), MonthDay.of(6, 15));

    private static final FieldSource.Period FROM_16_JUNE = new FieldSource.Period(MonthDay.of(6, 16), MonthDay.of(9, 15));

    private static final FieldSource.Period FROM_16_DECEMBER = new FieldSource.Period(MonthDay.of(12, 16), MonthDay.of(3, 15));

    private static ScheduleCell gainsIn(String code, FieldSource.Period period) {
        return new ScheduleCell(new CellCode(code), code, new FieldSource.FromCapitalGainsAccrual(List.of(SHORT_LISTED), period,
                "afterBroughtForward"), ScheduleCell.Sign.AS_COMPUTED);
    }

    /** TY 2025-26's figures with these short-term listed-equity gains, and {@code settled} as their BFLA figure. */
    private static ScheduleMapper.Figures accrued(List<ScheduleMapper.Accrued> gains, String settled,
            List<ScheduleMapper.Accrued> dividends) {
        return new ScheduleMapper.Figures(Map.of(), Map.of(), figures(Map.of()).returned(), Optional.empty(), Map.of(),
                Map.of(), Map.of(), Map.of(), Map.of(SHORT_LISTED.value(), Map.of("afterBroughtForward", Money.of(settled))),
                new ScheduleMapper.Accruals(LocalDate.of(2025, 4, 1), Map.of(
                        FieldSource.FromCapitalGainsBuckets.itemKindOf(SHORT_LISTED), gains, "DIVIDEND", dividends)));
    }

    private static ScheduleMapper.Accrued on(String rupees, LocalDate day) {
        return new ScheduleMapper.Accrued(Money.of(rupees), Optional.of(day));
    }

    @Test
    void fill_whenGainsAriseInTwoPeriods_givesEachPeriodItsOwn() {
        // Given — 50,000 sold on 15 June 2025 and 30,000 on 20 January 2026; nothing set off, so BFLA holds 80,000
        ScheduleCell june = gainsIn("SYN_CG_I", UP_TO_15_JUNE);
        ScheduleCell september = gainsIn("SYN_CG_II", FROM_16_JUNE);
        ScheduleCell march = gainsIn("SYN_CG_IV", FROM_16_DECEMBER);
        ScheduleMapper.Figures run = accrued(List.of(on("50000", LocalDate.of(2025, 6, 15)),
                on("30000", LocalDate.of(2026, 1, 20))), "80000", List.of());

        // When
        List<FilledCell> cells = ScheduleMapper.fill(form("SYN-1", "B1.i", List.of(june, september, march)), run);

        // Then — 15 June is the first period's last day; the January sale falls in 16/12 to 15/3, across the year's turn
        assertThat(cell(cells, june).amount()).contains(Money.of("50000"));
        assertThat(cell(cells, september).amount()).contains(Money.ZERO);
        assertThat(cell(cells, march).amount()).contains(Money.of("30000"));
    }

    @Test
    void fill_whenALossWasSetOffAgainstTheGains_saysTheRowCannotBeSplit() {
        // Given — the same gains, but BFLA holds 60,000: 20,000 of loss was set off, in no period the form names
        ScheduleCell june = gainsIn("SYN_CG_I", UP_TO_15_JUNE);
        ScheduleMapper.Figures run = accrued(List.of(on("50000", LocalDate.of(2025, 6, 15)),
                on("30000", LocalDate.of(2026, 1, 20))), "60000", List.of());

        // When
        List<FilledCell> cells = ScheduleMapper.fill(form("SYN-1", "B1.i", List.of(june)), run);

        // Then
        assertThat(cell(cells, june).amount()).isEmpty();
        assertThat(cell(cells, june).unavailableBecause()).hasValueSatisfying(reason -> assertThat(reason)
                .startsWith("A loss was set off against these gains"));
    }

    @Test
    void fill_whenAGainInTheRowIsALoss_saysTheRowCannotBeSplit() {
        // Given — a 20,000 loss sold on 1 February 2026 nets inside the part, so BFLA's 60,000 equals the bucket total
        ScheduleCell march = gainsIn("SYN_CG_IV", FROM_16_DECEMBER);
        ScheduleMapper.Figures run = accrued(List.of(on("50000", LocalDate.of(2025, 6, 15)),
                on("30000", LocalDate.of(2026, 1, 20)), on("-20000", LocalDate.of(2026, 2, 1))), "60000", List.of());

        // When
        List<FilledCell> cells = ScheduleMapper.fill(form("SYN-1", "B1.i", List.of(march)), run);

        // Then
        assertThat(cell(cells, march).unavailableBecause()).hasValueSatisfying(reason -> assertThat(reason)
                .startsWith("A loss was set off against these gains"));
    }

    @Test
    void fill_whenADividendHasNoDateOrIsDatedOutsideTheYear_saysSo() {
        // Given — dividends by the date received
        ScheduleCell dividends = new ScheduleCell(new CellCode("SYN_OS_II"), "Dividends", new FieldSource.FromOtherSourceAccrual(
                List.of("DIVIDEND"), FROM_16_JUNE), ScheduleCell.Sign.AS_COMPUTED);
        ScheduleMapper.Figures dated = accrued(List.of(), "0", List.of(on("10000", LocalDate.of(2025, 9, 5))));
        ScheduleMapper.Figures undated = accrued(List.of(), "0", List.of(new ScheduleMapper.Accrued(Money.of("10000"),
                Optional.empty())));
        ScheduleMapper.Figures outside = accrued(List.of(), "0", List.of(on("10000", LocalDate.of(2026, 4, 2))));

        // When
        FilledCell inYear = cell(ScheduleMapper.fill(form("SYN-1", "B1.i", List.of(dividends)), dated), dividends);
        FilledCell noDate = cell(ScheduleMapper.fill(form("SYN-1", "B1.i", List.of(dividends)), undated), dividends);
        FilledCell late = cell(ScheduleMapper.fill(form("SYN-1", "B1.i", List.of(dividends)), outside), dividends);

        // Then
        assertThat(inYear.amount()).contains(Money.of("10000"));
        assertThat(noDate.unavailableBecause()).hasValueSatisfying(reason -> assertThat(reason)
                .startsWith("An item this row holds was entered without the date it was received"));
        assertThat(late.unavailableBecause()).hasValueSatisfying(reason -> assertThat(reason)
                .startsWith("An item this row holds is dated outside the year"));
    }
}
