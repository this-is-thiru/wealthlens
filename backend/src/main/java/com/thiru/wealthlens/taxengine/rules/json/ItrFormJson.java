package com.thiru.wealthlens.taxengine.rules.json;

import com.thiru.wealthlens.taxengine.rules.definition.CapitalGainsPart;
import com.thiru.wealthlens.taxengine.rules.filing.FieldSource;
import com.thiru.wealthlens.taxengine.rules.filing.FurnishingOption;
import com.thiru.wealthlens.taxengine.rules.filing.ItrFormBar;
import com.thiru.wealthlens.taxengine.rules.filing.ItrFormSpec;
import com.thiru.wealthlens.taxengine.rules.filing.ItrSchedule;
import com.thiru.wealthlens.taxengine.rules.filing.ScheduleCell;
import com.thiru.wealthlens.taxengine.rules.filing.ScheduleField;
import com.thiru.wealthlens.taxengine.rules.filing.ScheduleGroup;
import com.thiru.wealthlens.taxengine.rules.id.CellCode;
import com.thiru.wealthlens.taxengine.rules.id.FurnishingManner;
import com.thiru.wealthlens.taxengine.rules.id.ItrFormCode;
import com.thiru.wealthlens.taxengine.rules.id.NodeId;
import com.thiru.wealthlens.taxengine.rules.id.ReturnKind;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.id.ScheduleCode;
import com.thiru.wealthlens.taxengine.rules.validate.PackValidationException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import tools.jackson.databind.JsonNode;

/**
 * The return forms: {@code forms/itr-forms.json} in a year's pack, and each version of a
 * {@code forms/itr-forms/<CODE>.json} file on the timeline (W-090b). A form file's identity is under {@code id}, as
 * every versioned set's is.
 */
public final class ItrFormJson {

    private ItrFormJson() {
    }

    /** The year's forms, by code, their schedules' cells resolved from the file's {@code cells} catalogue (W-092). */
    public static Map<ItrFormCode, ItrFormSpec> forms(JsonNode root, String path) {
        Map<CellCode, ScheduleCell> cells = cells(PackJson.arrayOrEmpty(root, "cells", path, e -> cell(e.node(), e.path())),
                path);
        Map<ItrFormCode, ItrFormSpec> byCode = new LinkedHashMap<>();
        for (ItrFormSpec form : PackJson.arrayOrEmpty(root, "forms", path, e -> form(e.node(), e.path(), cells))) {
            if (byCode.put(form.code(), form) != null) {
                throw new PackValidationException(path + " defines form " + form.code() + " twice");
            }
        }
        return byCode;
    }

    /** The cells, by code; a code defined twice is refused. */
    public static Map<CellCode, ScheduleCell> cells(java.util.List<ScheduleCell> cells, String path) {
        Map<CellCode, ScheduleCell> byCode = new LinkedHashMap<>();
        for (ScheduleCell cell : cells) {
            if (byCode.put(cell.code(), cell) != null) {
                throw new PackValidationException(path + " defines cell " + cell.code() + " twice");
            }
        }
        return byCode;
    }

    /** One cell: its code, label and source (W-092 C2a, C3a). */
    public static ScheduleCell cell(JsonNode node, String path) {
        CellCode code = PackJson.construct(path, () -> new CellCode(PackJson.text(node, "code", path)));
        String where = path + " (" + code + ")";
        return PackJson.construct(where, () -> new ScheduleCell(code, PackJson.text(node, "label", where),
                source(PackJson.object(node, "source", where), where + ".source"),
                PackJson.optionalText(node, "sign", where).map(ScheduleCell.Sign::valueOf)
                        .orElse(ScheduleCell.Sign.AS_COMPUTED)));
    }

    private static FieldSource source(JsonNode node, String path) {
        return switch (PackJson.tag(node, path, "FromNodes", "FromTaxBasis", "FromRules", "FromCapitalGainsParts", "SumOf",
                "FromItemFigure", "FromOtherSourceKinds", "FromCapitalGainsBuckets", "FromSetOff", "ManualEntry")) {
            case "FromCapitalGainsBuckets" -> PackJson.construct(path, () -> new FieldSource.FromCapitalGainsBuckets(
                    PackJson.array(node, "parts", path, e -> new CapitalGainsPart(e.node().asString())),
                    PackJson.text(node, "figure", path)));
            case "FromSetOff" -> PackJson.construct(path, () -> new FieldSource.FromSetOff(
                    PackJson.array(node, "rows", path, e -> e.node().asString()), PackJson.text(node, "column", path)));
            case "FromItemFigure" -> PackJson.construct(path,
                    () -> new FieldSource.FromItemFigure(PackJson.text(node, "figure", path)));
            case "FromOtherSourceKinds" -> PackJson.construct(path, () -> new FieldSource.FromOtherSourceKinds(
                    PackJson.array(node, "kinds", path, e -> e.node().asString())));
            case "FromNodes" -> PackJson.construct(path, () -> new FieldSource.FromNodes(PackJson.array(node, "nodes", path,
                    e -> PackJson.construct(e.path(), () -> new NodeId(e.node().asString())))));
            case "FromTaxBasis" -> PackJson.construct(path,
                    () -> new FieldSource.FromTaxBasis(FieldSource.FromTaxBasis.Basis.valueOf(PackJson.text(node, "basis", path)),
                            PackJson.optionalText(node, "specialRate", path)));
            case "FromRules" -> PackJson.construct(path, () -> new FieldSource.FromRules(PackJson.array(node, "rules", path,
                    e -> PackJson.construct(e.path(), () -> new RuleId(e.node().asString()))),
                    PackJson.optionalText(node, "kind", path)));
            case "FromCapitalGainsParts" -> PackJson.construct(path, () -> new FieldSource.FromCapitalGainsParts(
                    PackJson.array(node, "parts", path, e -> new CapitalGainsPart(e.node().asString()))));
            case "SumOf" -> PackJson.construct(path, () -> new FieldSource.SumOf(PackJson.array(node, "cells", path,
                    e -> term(e.node().asString(), e.path()))));
            default -> PackJson.construct(path, () -> new FieldSource.ManualEntry(PackJson.text(node, "guidance", path)));
        };
    }

    /** A sum's term: a cell's code, taken away where it starts with a minus sign. */
    private static FieldSource.SumOf.Term term(String text, String path) {
        boolean subtract = text.startsWith("-");
        return PackJson.construct(path, () -> new FieldSource.SumOf.Term(new CellCode(subtract ? text.substring(1) : text),
                subtract));
    }

    /**
     * One form, as a year's pack holds it — and as the timeline reads each version of one — its schedules' cells resolved
     * from {@code cells}.
     */
    public static ItrFormSpec form(JsonNode node, String path, Map<CellCode, ScheduleCell> cells) {
        ItrFormCode code = PackJson.construct(path, () -> new ItrFormCode(PackJson.text(node, "id", path)));
        String where = path + " (" + code + ")";
        return PackJson.construct(where, () -> new ItrFormSpec(
                code,
                PackJson.text(node, "displayName", where),
                PackJson.integer(node, "cascadeOrder", where),
                RegimeJson.personTypes(node, where),
                new LinkedHashSet<>(PackJson.array(node, "returnKinds", where,
                        e -> PackJson.construct(e.path(), () -> new ReturnKind(e.node().asString())))),
                ConditionJson.conditions(node, "positiveConditions", where),
                PackJson.arrayOrEmpty(node, "bars", where, e -> bar(e.node(), e.path())),
                new LinkedHashSet<>(PackJson.arrayOrEmpty(node, "excludes", where,
                        e -> PackJson.construct(e.path(), () -> new ItrFormCode(e.node().asString())))),
                PackJson.array(node, "furnishing", where, e -> furnishing(e.node(), e.path())),
                PackJson.arrayOrEmpty(node, "schedules", where, e -> schedule(e.node(), e.path(), cells)),
                ValueJson.provenance(PackJson.object(node, "provenance", where), where + ".provenance")));
    }

    private static ItrSchedule schedule(JsonNode node, String path, Map<CellCode, ScheduleCell> cells) {
        ScheduleCode code = PackJson.construct(path, () -> new ScheduleCode(PackJson.text(node, "code", path)));
        String where = path + " (" + code + ")";
        return PackJson.construct(where, () -> new ItrSchedule(code, PackJson.text(node, "displayName", where),
                PackJson.arrayOrEmpty(node, "fields", where, e -> field(e.node(), e.path(), cells)),
                PackJson.arrayOrEmpty(node, "groups", where, e -> group(e.node(), e.path(), cells)),
                ValueJson.provenance(PackJson.object(node, "provenance", where), where + ".provenance")));
    }

    private static ScheduleGroup group(JsonNode node, String path, Map<CellCode, ScheduleCell> cells) {
        return PackJson.construct(path, () -> new ScheduleGroup(PackJson.text(node, "item", path),
                PackJson.text(node, "path", path), PackJson.array(node, "fields", path, e -> field(e.node(), e.path(), cells))));
    }

    private static ScheduleField field(JsonNode node, String path, Map<CellCode, ScheduleCell> cells) {
        CellCode code = PackJson.construct(path, () -> new CellCode(PackJson.text(node, "cell", path)));
        ScheduleCell cell = cells.get(code);
        if (cell == null) {
            throw new PackValidationException(path + " places cell " + code + ", which no cell file defines");
        }
        return PackJson.construct(path, () -> new ScheduleField(PackJson.text(node, "path", path), cell));
    }

    private static ItrFormBar bar(JsonNode node, String path) {
        return PackJson.construct(path, () -> new ItrFormBar(
                PackJson.text(node, "reason", path),
                ConditionJson.condition(PackJson.object(node, "condition", path), path + ".condition"),
                ValueJson.provenance(PackJson.object(node, "provenance", path), path + ".provenance")));
    }

    private static FurnishingOption furnishing(JsonNode node, String path) {
        return PackJson.construct(path, () -> new FurnishingOption(
                new FurnishingManner(PackJson.text(node, "manner", path)),
                ConditionJson.conditions(node, "conditions", path),
                ValueJson.provenance(PackJson.object(node, "provenance", path), path + ".provenance")));
    }
}
