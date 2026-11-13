package com.thiru.wealthlens.taxengine.rules.filing;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.condition.ReturnFacts;
import com.thiru.wealthlens.taxengine.rules.filing.FieldSource.FromCapitalGainsParts;
import com.thiru.wealthlens.taxengine.rules.filing.FieldSource.FromNodes;
import com.thiru.wealthlens.taxengine.rules.filing.FieldSource.FromRules;
import com.thiru.wealthlens.taxengine.rules.filing.FieldSource.FromTaxBasis;
import com.thiru.wealthlens.taxengine.rules.filing.FieldSource.ManualEntry;
import com.thiru.wealthlens.taxengine.rules.filing.FieldSource.SumOf;
import com.thiru.wealthlens.taxengine.rules.id.CellCode;
import com.thiru.wealthlens.taxengine.rules.id.ItrFormCode;
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
 * The schedule model refuses what could not be shown or filled (W-092, W-093; D-654, D-657): a cell without its parts, a
 * source naming nothing, a filled cell with both a figure and a reason or neither.
 */
class ScheduleModelTest {

    private static final Provenance SOURCE = new Provenance(URI.create("https://example.invalid/w-092"),
            "Synthetic, tests only", LocalDate.of(2026, 11, 13), VerificationLevel.UNVERIFIED, "Nothing: synthetic");
    private static final CellCode CODE = new CellCode("SYN_CELL");
    private static final ScheduleCell CELL = new ScheduleCell(CODE, "Synthetic", new ManualEntry("Enter it"),
            ScheduleCell.Sign.AS_COMPUTED);
    private static final ScheduleCode SCHEDULE = new ScheduleCode("S");

    @Test
    void sources_whenTheyNameNothing_areRefused() {
        assertThatThrownBy(() -> new FromNodes(null)).hasMessage("FromNodes names at least one node");
        assertThatThrownBy(() -> new FromNodes(List.of())).hasMessage("FromNodes names at least one node");
        assertThatThrownBy(() -> new FromRules(null, Optional.empty())).hasMessage("FromRules names at least one rule, and its kind or none");
        assertThatThrownBy(() -> new FromRules(List.of(), Optional.empty())).hasMessage("FromRules names at least one rule, and its kind or none");
        assertThatThrownBy(() -> new FromRules(List.of(new com.thiru.wealthlens.taxengine.rules.id.RuleId("OS-INTEREST")), null))
                .hasMessage("FromRules names at least one rule, and its kind or none");
        assertThatThrownBy(() -> new FromCapitalGainsParts(null)).hasMessage("FromCapitalGainsParts names at least one part");
        assertThatThrownBy(() -> new FromCapitalGainsParts(List.of()))
                .hasMessage("FromCapitalGainsParts names at least one part");
        assertThatThrownBy(() -> new SumOf(null)).hasMessage("SumOf names at least one cell");
        assertThatThrownBy(() -> new SumOf(List.of())).hasMessage("SumOf names at least one cell");
        assertThatThrownBy(() -> new SumOf.Term(null, false)).hasMessage("A SumOf term names a cell");
        assertThatThrownBy(() -> new ManualEntry(null)).hasMessage("A ManualEntry tells the person what to enter and why");
        assertThatThrownBy(() -> new ManualEntry(" ")).hasMessage("A ManualEntry tells the person what to enter and why");
    }

    @Test
    void fromTaxBasis_whenTheSpecialRateDoesNotMatchTheBasis_isRefused() {
        String refusal = "FromTaxBasis names a special rate exactly when its basis is SPECIAL_RATE";
        assertThatThrownBy(() -> new FromTaxBasis(null, Optional.empty())).hasMessage(refusal);
        assertThatThrownBy(() -> new FromTaxBasis(FromTaxBasis.Basis.SLAB_RATES, null)).hasMessage(refusal);
        assertThatThrownBy(() -> new FromTaxBasis(FromTaxBasis.Basis.SPECIAL_RATE, Optional.empty())).hasMessage(refusal);
        assertThatThrownBy(() -> new FromTaxBasis(FromTaxBasis.Basis.ALL_RATES, Optional.of("RATE_X"))).hasMessage(refusal);
    }

    @Test
    void cellsFieldsAndSchedules_whenAPartIsMissing_areRefused() {
        assertThatThrownBy(() -> new ScheduleCell(null, "L", new ManualEntry("x"), ScheduleCell.Sign.AS_COMPUTED))
                .hasMessage("A schedule cell names its code, its source and its sign");
        assertThatThrownBy(() -> new ScheduleCell(CODE, "L", null, ScheduleCell.Sign.AS_COMPUTED))
                .hasMessage("A schedule cell names its code, its source and its sign");
        assertThatThrownBy(() -> new ScheduleCell(CODE, "L", new ManualEntry("x"), null))
                .hasMessage("A schedule cell names its code, its source and its sign");
        assertThatThrownBy(() -> new ScheduleCell(CODE, null, new ManualEntry("x"), ScheduleCell.Sign.AS_COMPUTED))
                .hasMessage("SYN_CELL must have a label; it reaches the person");
        assertThatThrownBy(() -> new ScheduleCell(CODE, " ", new ManualEntry("x"), ScheduleCell.Sign.AS_COMPUTED))
                .hasMessage("SYN_CELL must have a label; it reaches the person");
        assertThatThrownBy(() -> new ScheduleField(null, CELL)).hasMessage("A schedule field names its path and its cell");
        assertThatThrownBy(() -> new ScheduleField(" ", CELL)).hasMessage("A schedule field names its path and its cell");
        assertThatThrownBy(() -> new ScheduleField("S.1", null)).hasMessage("A schedule field names its path and its cell");
        List<ScheduleField> fields = List.of(new ScheduleField("S.1", CELL));
        assertThatThrownBy(() -> new ItrSchedule(null, "S", fields, List.of(), SOURCE))
                .hasMessage("A schedule names its code and where it was read");
        assertThatThrownBy(() -> new ItrSchedule(SCHEDULE, "S", fields, List.of(), null))
                .hasMessage("A schedule names its code and where it was read");
        assertThatThrownBy(() -> new ItrSchedule(SCHEDULE, null, fields, List.of(), SOURCE)).hasMessage("S must have a display name");
        assertThatThrownBy(() -> new ItrSchedule(SCHEDULE, " ", fields, List.of(), SOURCE)).hasMessage("S must have a display name");
        assertThatThrownBy(() -> new ItrSchedule(SCHEDULE, "S", null, List.of(), SOURCE))
                .hasMessage("S has no fields; a schedule shows at least one figure");
        assertThatThrownBy(() -> new ItrSchedule(SCHEDULE, "S", List.of(), List.of(), SOURCE))
                .hasMessage("S has no fields; a schedule shows at least one figure");
    }

    @Test
    void filledCell_whenItHasBothAFigureAndAReasonOrNeither_isRefused() {
        Optional<Money> figure = Optional.of(Money.of("1"));
        Optional<String> reason = Optional.of("why");
        assertThatThrownBy(() -> new FilledCell(SCHEDULE, "S.1", CODE, "L", figure, reason))
                .hasMessage("S.1 must carry either a figure or the reason it has none, not both");
        assertThatThrownBy(() -> new FilledCell(SCHEDULE, "S.1", CODE, "L", Optional.empty(), Optional.empty()))
                .hasMessage("S.1 must carry either a figure or the reason it has none, not neither");
        String missing = "A filled cell names its schedule, path, cell and label, and says either its figure or why it has"
                + " none";
        assertThatThrownBy(() -> new FilledCell(null, "S.1", CODE, "L", figure, Optional.empty())).hasMessage(missing);
        assertThatThrownBy(() -> new FilledCell(SCHEDULE, null, CODE, "L", figure, Optional.empty())).hasMessage(missing);
        assertThatThrownBy(() -> new FilledCell(SCHEDULE, "S.1", null, "L", figure, Optional.empty())).hasMessage(missing);
        assertThatThrownBy(() -> new FilledCell(SCHEDULE, "S.1", CODE, null, figure, Optional.empty())).hasMessage(missing);
        assertThatThrownBy(() -> new FilledCell(SCHEDULE, "S.1", CODE, "L", null, Optional.empty())).hasMessage(missing);
        assertThatThrownBy(() -> new FilledCell(SCHEDULE, "S.1", CODE, "L", figure, null)).hasMessage(missing);
    }

    @Test
    void figuresVerdictsAndSelections_whenAPartIsMissing_areRefused() {
        assertThatThrownBy(() -> new ScheduleMapper.Figures(Map.of(), Map.of(), null, Optional.empty(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of()))
                .hasMessage("Figures needs the return's facts and says whether slab tax was computed");
        assertThatThrownBy(() -> new ScheduleMapper.Figures(Map.of(), Map.of(), ReturnFacts.NONE, null, Map.of(), Map.of(), Map.of(), Map.of(), Map.of()))
                .hasMessage("Figures needs the return's facts and says whether slab tax was computed");
        ItrFormCode form = new ItrFormCode("SYN-1");
        String verdict = "A verdict names its form, its standing and whether a form excluded it";
        assertThatThrownBy(() -> new FormVerdict(null, FormVerdict.Standing.ELIGIBLE, List.of(), List.of(), Optional.empty(),
                Set.of())).hasMessage(verdict);
        assertThatThrownBy(() -> new FormVerdict(form, null, List.of(), List.of(), Optional.empty(), Set.of()))
                .hasMessage(verdict);
        assertThatThrownBy(() -> new FormVerdict(form, FormVerdict.Standing.ELIGIBLE, List.of(), List.of(), null, Set.of()))
                .hasMessage(verdict);
        assertThatThrownBy(() -> new ItrSelection(List.of(), null, List.of()))
                .hasMessage("An ItrSelection says whether a form was chosen; empty for none");
    }
    @Test
    void groupsAndItemSources_whenAPartIsMissing_areRefused() {
        List<ScheduleField> fields = List.of(new ScheduleField("a", CELL));
        assertThatThrownBy(() -> new ScheduleGroup(null, "HP.1", fields))
                .hasMessage("A schedule group names the kind of item it repeats over and its path");
        assertThatThrownBy(() -> new ScheduleGroup("HOUSE_PROPERTY", " ", fields))
                .hasMessage("A schedule group names the kind of item it repeats over and its path");
        assertThatThrownBy(() -> new ScheduleGroup("HOUSE_PROPERTY", "HP.1", List.of())).hasMessage("The group HP.1 repeats no cell");
        assertThatThrownBy(() -> new FieldSource.FromItemFigure(" ")).hasMessage("FromItemFigure names the item's figure");
        assertThatThrownBy(() -> new FieldSource.FromOtherSourceKinds(List.of()))
                .hasMessage("FromOtherSourceKinds names at least one kind");
        assertThatThrownBy(() -> new ItrSchedule(SCHEDULE, "S", List.of(), null, SOURCE))
                .hasMessage("S has no fields; a schedule shows at least one figure");
    }
}
