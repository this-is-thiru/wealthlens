package com.thiru.wealthlens.taxengine.rules.validate;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.taxengine.rules.condition.PersonType;
import com.thiru.wealthlens.taxengine.rules.definition.CapitalGainsPart;
import com.thiru.wealthlens.taxengine.rules.filing.FieldSource;
import com.thiru.wealthlens.taxengine.rules.filing.FurnishingOption;
import com.thiru.wealthlens.taxengine.rules.filing.ItrFormSpec;
import com.thiru.wealthlens.taxengine.rules.filing.ItrSchedule;
import com.thiru.wealthlens.taxengine.rules.filing.ScheduleCell;
import com.thiru.wealthlens.taxengine.rules.filing.ScheduleField;
import com.thiru.wealthlens.taxengine.rules.id.CellCode;
import com.thiru.wealthlens.taxengine.rules.id.FurnishingManner;
import com.thiru.wealthlens.taxengine.rules.id.ItrFormCode;
import com.thiru.wealthlens.taxengine.rules.id.NodeId;
import com.thiru.wealthlens.taxengine.rules.id.ReturnKind;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.id.ScheduleCode;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.pack.RulePack;
import com.thiru.wealthlens.taxengine.rules.registry.Provenance;
import com.thiru.wealthlens.taxengine.rules.registry.VerificationLevel;
import com.thiru.wealthlens.taxengine.rules.timeline.ShippedRulePacks;
import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** I21 (W-092, D-654): a schedule cell reads only what the year's pack holds, and sums only cells its form places. */
class ItrScheduleReferencesTest {

    private static final TaxYearId TY_2025_26 = TaxYearId.of(2025);
    private static final RulePack PACK = ShippedRulePacks.compile("rulepacks", Set.of(TY_2025_26)).load(TY_2025_26);
    private static final Provenance SOURCE = new Provenance(URI.create("https://example.invalid/w-092"),
            "Synthetic, tests only", LocalDate.of(2026, 11, 13), VerificationLevel.UNVERIFIED, "Nothing: synthetic");

    private static ScheduleCell cell(String code, FieldSource source) {
        return new ScheduleCell(new CellCode(code), code, source, ScheduleCell.Sign.AS_COMPUTED);
    }

    private static List<String> problems(ScheduleCell... cells) {
        List<ScheduleField> fields = new ArrayList<>();
        for (ScheduleCell cell : cells) {
            fields.add(new ScheduleField(cell.code().value(), cell));
        }
        ItrFormSpec form = new ItrFormSpec(new ItrFormCode("SYN-1"), "Synthetic", 1, Set.of(PersonType.INDIVIDUAL),
                Set.of(new ReturnKind("SYN_ORIGINAL")), List.of(), List.of(), Set.of(),
                List.of(new FurnishingOption(new FurnishingManner("SYN_ELECTRONIC"), List.of(), SOURCE)),
                List.of(new ItrSchedule(new ScheduleCode("S"), "Synthetic", fields, SOURCE)), SOURCE);
        RulePack pack = new RulePack(PACK.taxYear(), PACK.layers(), PACK.instruments(), PACK.rules(), PACK.ruleSets(),
                PACK.components(), PACK.regimes(), PACK.disallowanceSets(), PACK.conditionSets(), PACK.funds(),
                PACK.declarations(), Map.of(form.code(), form));
        List<String> problems = new ArrayList<>();
        ItrScheduleReferences.check(pack, problems);
        return problems;
    }

    @Test
    void check_whenEveryCellReadsWhatTheYearHolds_findsNothing() {
        // When — a node, a rule of the year, a declared part, a sum of placed cells, an entry by hand
        List<String> problems = problems(
                cell("SYN_GROSS", new FieldSource.FromNodes(List.of(new NodeId("salary.gross")))),
                cell("SYN_DEDUCTION", new FieldSource.FromRules(List.of(new RuleId("SAL-STANDARD-DEDUCTION-NEW-TAX-REGIME")))),
                cell("SYN_GAIN", new FieldSource.FromCapitalGainsPart(new CapitalGainsPart("LONG_TERM_LISTED_EQUITY"))),
                cell("SYN_NET", new FieldSource.SumOf(List.of(new FieldSource.SumOf.Term(new CellCode("SYN_GROSS"), false)))),
                cell("SYN_TAN", new FieldSource.ManualEntry("Enter it")));

        // Then
        assertThat(problems).isEmpty();
    }

    @Test
    void check_whenACellReadsARuleOrPartTheYearLacksOrSumsAnUnplacedCell_namesEach() {
        // When
        List<String> problems = problems(
                cell("SYN_RULE", new FieldSource.FromRules(List.of(new RuleId("SYN-NOT-A-RULE")))),
                cell("SYN_PART", new FieldSource.FromCapitalGainsPart(new CapitalGainsPart("SYN_NOT_A_PART"))),
                cell("SYN_SUM", new FieldSource.SumOf(List.of(new FieldSource.SumOf.Term(new CellCode("SYN_ABSENT"), true)))));

        // Then
        assertThat(problems).containsExactly(
                "form SYN-1 S SYN_RULE (SYN_RULE) reads rule SYN-NOT-A-RULE, which is not a rule of 2025-26 (I21)",
                "form SYN-1 S SYN_PART (SYN_PART) reads capital-gains part SYN_NOT_A_PART, which the year's"
                        + " CapitalGainsParts does not declare (I21)",
                "form SYN-1 S SYN_SUM (SYN_SUM) sums cell SYN_ABSENT, which the form does not place (I21)");
    }
}
