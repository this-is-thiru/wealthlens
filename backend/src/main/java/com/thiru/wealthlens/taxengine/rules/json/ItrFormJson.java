package com.thiru.wealthlens.taxengine.rules.json;

import com.thiru.wealthlens.taxengine.rules.filing.FurnishingOption;
import com.thiru.wealthlens.taxengine.rules.filing.ItrFormBar;
import com.thiru.wealthlens.taxengine.rules.filing.ItrFormSpec;
import com.thiru.wealthlens.taxengine.rules.id.FurnishingManner;
import com.thiru.wealthlens.taxengine.rules.id.ItrFormCode;
import com.thiru.wealthlens.taxengine.rules.id.ReturnKind;
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

    /** The year's forms, by code. */
    public static Map<ItrFormCode, ItrFormSpec> forms(JsonNode root, String path) {
        Map<ItrFormCode, ItrFormSpec> byCode = new LinkedHashMap<>();
        for (ItrFormSpec form : PackJson.arrayOrEmpty(root, "forms", path, e -> form(e.node(), e.path()))) {
            if (byCode.put(form.code(), form) != null) {
                throw new PackValidationException(path + " defines form " + form.code() + " twice");
            }
        }
        return byCode;
    }

    /** One form, as a year's pack holds it — and as the timeline reads each version of one. */
    public static ItrFormSpec form(JsonNode node, String path) {
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
                ValueJson.provenance(PackJson.object(node, "provenance", where), where + ".provenance")));
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
