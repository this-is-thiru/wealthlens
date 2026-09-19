package com.thiru.wealthlens.taxrules.json;

import com.thiru.wealthlens.taxrules.condition.PersonType;
import com.thiru.wealthlens.taxrules.id.NodeId;
import com.thiru.wealthlens.taxrules.id.RegimeCode;
import com.thiru.wealthlens.taxrules.id.RuleId;
import com.thiru.wealthlens.taxrules.regime.AfterWithdrawal;
import com.thiru.wealthlens.taxrules.regime.BehaviourId;
import com.thiru.wealthlens.taxrules.regime.BindingSpec;
import com.thiru.wealthlens.taxrules.regime.ConditionSetId;
import com.thiru.wealthlens.taxrules.regime.ContinuationPolicy;
import com.thiru.wealthlens.taxrules.regime.DeadlineSpec;
import com.thiru.wealthlens.taxrules.regime.DisallowanceSetId;
import com.thiru.wealthlens.taxrules.regime.ElectionKind;
import com.thiru.wealthlens.taxrules.regime.ElectionProtocol;
import com.thiru.wealthlens.taxrules.regime.ElectionRoute;
import com.thiru.wealthlens.taxrules.regime.ElectionVehicle;
import com.thiru.wealthlens.taxrules.regime.FormRef;
import com.thiru.wealthlens.taxrules.regime.InvalidationPolicy;
import com.thiru.wealthlens.taxrules.regime.PipelineSpec;
import com.thiru.wealthlens.taxrules.regime.RegimeLifecycle;
import com.thiru.wealthlens.taxrules.regime.RegimeSpec;
import com.thiru.wealthlens.taxrules.validate.PackValidationException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import tools.jackson.databind.JsonNode;

/**
 * Regimes, with their election protocol and their two lifecycle windows.
 *
 * <p>Reads {@code law/regimes.json}. Every tagged union here is one the design revised after the
 * getting-started pack was written — the {@code lockIn} enum became a {@link BindingSpec}, and a
 * bare deadline string became a {@link DeadlineSpec} — so the tags listed on failure are the
 * current ones, and a pack copied from the old example fails with the options spelled out.
 */
public final class RegimeJson {

    private RegimeJson() {
    }

    /** Every regime in one file, keyed by code. */
    public static Map<RegimeCode, RegimeSpec> regimes(JsonNode root, String path) {
        Map<RegimeCode, RegimeSpec> byCode = new LinkedHashMap<>();
        for (RegimeSpec regime : PackJson.array(root, "regimes", path, e -> regime(e.node(), e.path()))) {
            if (byCode.put(regime.code(), regime) != null) {
                throw new PackValidationException(
                        path + " defines regime " + regime.code() + " twice");
            }
        }
        return byCode;
    }

    /** One regime. */
    public static RegimeSpec regime(JsonNode node, String path) {
        RegimeCode code = new RegimeCode(PackJson.text(node, "code", path));
        String where = path + " (" + code + ")";

        return new RegimeSpec(
                code,
                PackJson.text(node, "displayName", where),
                ValueJson.provision(
                        PackJson.object(node, "provision1961", where), where + ".provision1961"),
                ValueJson.provision(
                        PackJson.object(node, "provision2025", where), where + ".provision2025"),
                personTypes(node, where),
                new RuleId(PackJson.text(node, "rateTable", where)),
                ids(node, "disallowances", where, DisallowanceSetId::new),
                ids(node, "eligibility", where, ConditionSetId::new),
                election(PackJson.object(node, "election", where), where + ".election"),
                lifecycle(PackJson.object(node, "lifecycle", where), where + ".lifecycle"),
                PackJson.bool(node, "isDefault", where),
                pipeline(PackJson.object(node, "pipeline", where), where + ".pipeline"),
                PackJson.optionalText(node, "behaviour", where).map(BehaviourId::new),
                ValueJson.provenance(
                        PackJson.object(node, "provenance", where), where + ".provenance"));
    }

    private static Set<PersonType> personTypes(JsonNode node, String path) {
        Set<PersonType> types = new LinkedHashSet<>();
        for (String name : PackJson.array(node, "appliesTo", path, e -> e.node().asString())) {
            boolean matched = false;
            for (PersonType candidate : PersonType.values()) {
                if (candidate.name().equals(name)) {
                    types.add(candidate);
                    matched = true;
                }
            }
            if (!matched) {
                throw new PackValidationException(
                        path + ".appliesTo contains \"" + name + "\", which is not a known person"
                                + " type. Known: " + java.util.Arrays.toString(PersonType.values()));
            }
        }
        return types;
    }

    private static <T> Set<T> ids(
            JsonNode node, String field, String path, java.util.function.Function<String, T> factory) {
        return new LinkedHashSet<>(
                PackJson.arrayOrEmpty(node, field, path, e -> factory.apply(e.node().asString())));
    }

    private static PipelineSpec pipeline(JsonNode node, String path) {
        return new PipelineSpec(
                PackJson.array(node, "nodeRefs", path, e -> new NodeId(e.node().asString())));
    }

    // ------------------------------------------------------------- election

    static ElectionProtocol election(JsonNode node, String path) {
        return new ElectionProtocol(
                PackJson.enumValue(node, "kind", path, ElectionKind.class),
                PackJson.enumValue(node, "vehicle", path, ElectionVehicle.class),
                PackJson.optionalObject(node, "form", path)
                        .map(form -> new FormRef(
                                PackJson.text(form, "number", path + ".form"),
                                PackJson.text(form, "displayName", path + ".form"))),
                deadline(PackJson.object(node, "deadline", path), path + ".deadline"),
                binding(PackJson.object(node, "binding", path), path + ".binding"),
                ValueJson.provenance(
                        PackJson.object(node, "provenance", path), path + ".provenance"));
    }

    static DeadlineSpec deadline(JsonNode node, String path) {
        String tag = PackJson.tag(node, path, "DueDateUnder", "WithTheReturn", "NoDeadline");
        return switch (tag) {
            case "DueDateUnder" -> new DeadlineSpec.DueDateUnder(
                    PackJson.text(node, "section", path));
            case "WithTheReturn" -> new DeadlineSpec.WithTheReturn();
            case "NoDeadline" -> new DeadlineSpec.NoDeadline();
            default -> throw unhandled(tag, path);
        };
    }

    static BindingSpec binding(JsonNode node, String path) {
        return new BindingSpec(
                PackJson.bool(node, "bindsSubsequentYears", path),
                PackJson.integer(node, "maxWithdrawals", path),
                afterWithdrawal(
                        PackJson.object(node, "afterWithdrawal", path), path + ".afterWithdrawal"),
                PackJson.bool(node, "firstReturnOnly", path),
                ConditionJson.conditions(node, "bindingAppliesWhen", path));
    }

    static AfterWithdrawal afterWithdrawal(JsonNode node, String path) {
        String tag = PackJson.tag(node, path, "MayReElect", "PermanentlyIneligible", "IneligibleUnless");
        return switch (tag) {
            case "MayReElect" -> new AfterWithdrawal.MayReElect();
            case "PermanentlyIneligible" -> new AfterWithdrawal.PermanentlyIneligible();
            case "IneligibleUnless" -> new AfterWithdrawal.IneligibleUnless(
                    ConditionJson.condition(
                            PackJson.object(node, "revival", path), path + ".revival"),
                    new ElectionRoute(PackJson.text(node, "thenUse", path)));
            default -> throw unhandled(tag, path);
        };
    }

    // ------------------------------------------------------------ lifecycle

    static RegimeLifecycle lifecycle(JsonNode node, String path) {
        return new RegimeLifecycle(
                ValueJson.dateRange(
                        PackJson.object(node, "entryWindow", path), path + ".entryWindow"),
                ValueJson.dateRange(
                        PackJson.object(node, "inForceWindow", path), path + ".inForceWindow"),
                continuation(PackJson.object(node, "onSunset", path), path + ".onSunset"),
                invalidation(
                        PackJson.object(node, "onConditionFailure", path),
                        path + ".onConditionFailure"),
                ValueJson.provenance(
                        PackJson.object(node, "provenance", path), path + ".provenance"));
    }

    static ContinuationPolicy continuation(JsonNode node, String path) {
        String tag = PackJson.tag(node, path, "FallToDefault", "MigrateTo", "ContinueIndefinitely");
        return switch (tag) {
            case "FallToDefault" -> new ContinuationPolicy.FallToDefault(
                    new RuleId(PackJson.text(node, "basis", path)));
            case "MigrateTo" -> new ContinuationPolicy.MigrateTo(
                    new RegimeCode(PackJson.text(node, "successor", path)),
                    new RuleId(PackJson.text(node, "basis", path)));
            case "ContinueIndefinitely" -> new ContinuationPolicy.ContinueIndefinitely();
            default -> throw unhandled(tag, path);
        };
    }

    static InvalidationPolicy invalidation(JsonNode node, String path) {
        String tag = PackJson.tag(
                node, path, "RetroactiveFromFailureYear", "ProspectiveOnly", "NoInvalidation");
        return switch (tag) {
            case "RetroactiveFromFailureYear" -> new InvalidationPolicy.RetroactiveFromFailureYear(
                    new RuleId(PackJson.text(node, "basis", path)));
            case "ProspectiveOnly" -> new InvalidationPolicy.ProspectiveOnly(
                    new RuleId(PackJson.text(node, "basis", path)));
            case "NoInvalidation" -> new InvalidationPolicy.NoInvalidation();
            default -> throw unhandled(tag, path);
        };
    }

    private static PackValidationException unhandled(String tag, String path) {
        return new PackValidationException(path + " has unhandled type \"" + tag + "\"");
    }
}
