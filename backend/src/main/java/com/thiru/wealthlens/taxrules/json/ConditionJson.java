package com.thiru.wealthlens.taxrules.json;

import com.thiru.wealthlens.taxrules.condition.AgeAtLeast;
import com.thiru.wealthlens.taxrules.condition.AgeSubject;
import com.thiru.wealthlens.taxrules.condition.AllOf;
import com.thiru.wealthlens.taxrules.condition.AnyOf;
import com.thiru.wealthlens.taxrules.condition.ComponentPresent;
import com.thiru.wealthlens.taxrules.condition.Condition;
import com.thiru.wealthlens.taxrules.condition.DisabilitySeverity;
import com.thiru.wealthlens.taxrules.condition.EvidenceRecorded;
import com.thiru.wealthlens.taxrules.condition.EvidenceType;
import com.thiru.wealthlens.taxrules.condition.HasIncomeUnderHead;
import com.thiru.wealthlens.taxrules.condition.HasPersonType;
import com.thiru.wealthlens.taxrules.condition.HeadCode;
import com.thiru.wealthlens.taxrules.condition.IncomeAtLeast;
import com.thiru.wealthlens.taxrules.condition.IncomeAtMost;
import com.thiru.wealthlens.taxrules.condition.IsCertifiedDisabled;
import com.thiru.wealthlens.taxrules.condition.IsResident;
import com.thiru.wealthlens.taxrules.condition.Not;
import com.thiru.wealthlens.taxrules.condition.PaidOtherThanInCash;
import com.thiru.wealthlens.taxrules.condition.PersonType;
import com.thiru.wealthlens.taxrules.id.ComponentCode;
import com.thiru.wealthlens.taxrules.id.NodeId;
import com.thiru.wealthlens.taxrules.money.Money;
import com.thiru.wealthlens.taxrules.validate.PackValidationException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import tools.jackson.databind.JsonNode;

/**
 * All thirteen predicates of the condition algebra.
 *
 * <p>Each JSON field is named for the record component it fills, with one exception forced by the
 * format: {@link EvidenceRecorded}'s component is called {@code type}, which is also the
 * discriminator every tagged union here uses. So it is read from <b>{@code evidenceType}</b> —
 * naming both {@code type} would make the object mean two things at once.
 */
public final class ConditionJson {

    private static final String[] TAGS = {
        "IsResident",
        "AgeAtLeast",
        "HasPersonType",
        "IsCertifiedDisabled",
        "IncomeAtMost",
        "IncomeAtLeast",
        "HasIncomeUnderHead",
        "ComponentPresent",
        "EvidenceRecorded",
        "PaidOtherThanInCash",
        "AllOf",
        "AnyOf",
        "Not"
    };

    private ConditionJson() {
    }

    /** One condition, of any variant. */
    public static Condition condition(JsonNode node, String path) {
        String tag = PackJson.tag(node, path, TAGS);
        return PackJson.construct(path, () ->
        switch (tag) {
                    case "IsResident", "AgeAtLeast", "HasPersonType", "IsCertifiedDisabled" ->
                            personFact(tag, node, path);
                    case "IncomeAtMost", "IncomeAtLeast", "HasIncomeUnderHead", "ComponentPresent",
                            "EvidenceRecorded", "PaidOtherThanInCash" -> amountOrStructure(tag, node, path);
                    case "AllOf", "AnyOf", "Not" -> composition(tag, node, path);
                    default -> throw unhandled(tag, path);
                });
    }

    /** Facts about a person — the {@code W-021} predicates. */
    private static Condition personFact(String tag, JsonNode node, String path) {
        return switch (tag) {
            case "IsResident" -> new IsResident();
            case "AgeAtLeast" -> new AgeAtLeast(
                    PackJson.integer(node, "years", path),
                    PackJson.enumValue(node, "subject", path, AgeSubject.class));
            case "HasPersonType" -> new HasPersonType(personTypes(node, path));
            case "IsCertifiedDisabled" -> new IsCertifiedDisabled(
                    PackJson.enumValue(node, "minimum", path, DisabilitySeverity.class));
            default -> throw unhandled(tag, path);
        };
    }

    /** Facts about amounts and the shape of the computation — the {@code W-021b} predicates. */
    private static Condition amountOrStructure(String tag, JsonNode node, String path) {
        return switch (tag) {
            case "IncomeAtMost" -> new IncomeAtMost(
                    new NodeId(PackJson.text(node, "node", path)), money(node, "limit", path));
            case "IncomeAtLeast" -> new IncomeAtLeast(
                    new NodeId(PackJson.text(node, "node", path)), money(node, "threshold", path));
            case "HasIncomeUnderHead" -> new HasIncomeUnderHead(
                    PackJson.enumValue(node, "head", path, HeadCode.class));
            case "ComponentPresent" -> new ComponentPresent(
                    new ComponentCode(PackJson.text(node, "code", path)));
            case "EvidenceRecorded" -> new EvidenceRecorded(
                    PackJson.enumValue(node, "evidenceType", path, EvidenceType.class));
            case "PaidOtherThanInCash" -> new PaidOtherThanInCash();
            default -> throw unhandled(tag, path);
        };
    }

    /** Conditions built from other conditions — the {@code W-021c} composition. */
    private static Condition composition(String tag, JsonNode node, String path) {
        return switch (tag) {
            case "AllOf" -> new AllOf(limbs(node, "all", path));
            case "AnyOf" -> new AnyOf(limbs(node, "any", path));
            case "Not" -> new Not(
                    condition(PackJson.object(node, "condition", path), path + ".condition"));
            default -> throw unhandled(tag, path);
        };
    }

    private static PackValidationException unhandled(String tag, String path) {
        return new PackValidationException(path + " has unhandled type \"" + tag + "\"");
    }

    /** A list of conditions, absent meaning none. */
    public static List<Condition> conditions(JsonNode parent, String field, String path) {
        return PackJson.arrayOrEmpty(
                parent, field, path, element -> condition(element.node(), element.path()));
    }

    private static List<Condition> limbs(JsonNode node, String field, String path) {
        return PackJson.array(
                node, field, path, element -> condition(element.node(), element.path()));
    }

    private static Set<PersonType> personTypes(JsonNode node, String path) {
        Set<PersonType> types = new LinkedHashSet<>();
        List<String> raw = PackJson.array(node, "types", path, e -> e.node().asString());
        for (String name : raw) {
            boolean matched = false;
            for (PersonType candidate : PersonType.values()) {
                if (candidate.name().equals(name)) {
                    types.add(candidate);
                    matched = true;
                }
            }
            if (!matched) {
                throw new PackValidationException(
                        path + ".types contains \"" + name + "\", which is not a known person type."
                                + " Known: " + java.util.Arrays.toString(PersonType.values()));
            }
        }
        return types;
    }

    private static Money money(JsonNode parent, String field, String path) {
        String raw = PackJson.text(parent, field, path);
        try {
            return Money.of(raw);
        } catch (NumberFormatException e) {
            throw new PackValidationException(
                    path + "." + field + " must be a rupee amount as a string, but was \"" + raw + "\"");
        }
    }
}
