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
            new FromRules(List.of(NEW_DEDUCTION, OLD_DEDUCTION)), ScheduleCell.Sign.AS_COMPUTED);
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
                List.of(new ItrSchedule(new ScheduleCode("S"), "Synthetic salary", fields, SOURCE)), SOURCE);
    }

    private static ScheduleMapper.Figures figures(Map<RuleId, Money> rules) {
        ReturnFacts returned = ReturnFacts.of(Map.of(HeadCode.SALARIES, Money.of("1000000"), HeadCode.HOUSE_PROPERTY,
                Money.ZERO, HeadCode.OTHER_SOURCES, Money.ZERO, HeadCode.CAPITAL_GAINS, Money.of("150000")),
                Map.of(LONG, Money.of("150000")), Set.of(), Map.of(), Map.of());
        return new ScheduleMapper.Figures(Map.of(GROSS_NODE, Money.of("1075000"), TRAVEL_NODE, Money.of("25000")), rules,
                returned, Optional.of(Money.of("120000")), Map.of("RATE_CG_LONG_LISTED_EQUITY", Money.of("3125")));
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
                new ScheduleMapper.Figures(Map.of(), Map.of(), figures(Map.of()).returned(), Optional.empty(), Map.of()));

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
}
