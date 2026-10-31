package com.thiru.wealthlens.taxengine.rules.validate;

import com.thiru.wealthlens.taxengine.rules.condition.AllOf;
import com.thiru.wealthlens.taxengine.rules.condition.AnyOf;
import com.thiru.wealthlens.taxengine.rules.condition.Condition;
import com.thiru.wealthlens.taxengine.rules.condition.ItemFactIs;
import com.thiru.wealthlens.taxengine.rules.condition.Not;
import com.thiru.wealthlens.taxengine.rules.definition.BucketPart;
import com.thiru.wealthlens.taxengine.rules.definition.CapitalGainsAbsorptionOrder;
import com.thiru.wealthlens.taxengine.rules.definition.CapitalGainsPart;
import com.thiru.wealthlens.taxengine.rules.definition.CapitalGainsParts;
import com.thiru.wealthlens.taxengine.rules.definition.SpecialRateShortfallOrder;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.pack.RulePack;
import com.thiru.wealthlens.taxengine.rules.temporal.RuleVersion;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * D-309 — every capital-gains part a row names is one the year's {@code CapitalGainsParts} declares, and the two orders
 * name the parts they must.
 *
 * <p>A part is a code, not a closed set, so a misspelt one would not fail to compile: a bucket row naming it would put
 * gains in a part nothing sets off or charges, and a special-rate row naming it would leave the real part at slab
 * rates. Both are a plausible wrong number, so the pack is refused instead — by the validation the build
 * runs over the shipped packs, as every check here is. Each reference is checked against every
 * declaration in force in the year, so a reference must hold under each.
 */
final class CapitalGainsPartReferences {

    private record Declaration(RuleId rule, Set<String> parts) {
    }

    private record Reference(RuleId rule, RuleVersion version) {
    }

    private CapitalGainsPartReferences() {
    }

    static void check(RulePack pack, List<String> problems) {
        List<Declaration> declarations = new ArrayList<>();
        List<Reference> references = new ArrayList<>();
        for (Map.Entry<RuleId, com.thiru.wealthlens.taxengine.rules.pack.RuleRow> entry : pack.rules().entrySet()) {
            for (RuleVersion version : entry.getValue().versions()) {
                if (!PackValidator.coversAnyOf(version, pack.taxYear().startsOn(), pack.taxYear().endsOn())) {
                    continue;
                }
                version.definition().filter(CapitalGainsParts.class::isInstance).map(CapitalGainsParts.class::cast)
                        .ifPresent(parts -> declarations.add(new Declaration(entry.getKey(), declared(parts))));
                references.add(new Reference(entry.getKey(), version));
            }
        }
        Set<String> charged = new LinkedHashSet<>();
        references.forEach(reference -> charged.addAll(conditionCodes(reference.version().conditions())));
        if (declarations.isEmpty()) {
            if (references.stream().anyMatch(CapitalGainsPartReferences::namesAPart)) {
                problems.add("rows of " + pack.taxYear().label() + " name capital-gains parts, but no row declares"
                        + " them (CapitalGainsParts, D-309)");
            }
            return;
        }
        for (Declaration declaration : declarations) {
            for (Reference reference : references) {
                checkReference(pack, declaration, reference, charged, problems);
            }
        }
    }

    private static void checkReference(RulePack pack, Declaration declaration, Reference reference,
            Set<String> charged, List<String> problems) {
        String where = "rule " + reference.rule() + " (version " + reference.version().validity() + ")";
        String against = declaration.rule() + " for " + pack.taxYear().label();
        for (String part : namedCodes(reference.version())) {
            if (!declaration.parts().contains(part)) {
                problems.add(where + " names capital-gains part " + part + ", which " + against + " does not declare");
            }
        }
        reference.version().definition().ifPresent(definition -> {
            if (definition instanceof CapitalGainsAbsorptionOrder order && !codes(order.parts()).equals(declaration.parts())) {
                problems.add(where + " orders the parts " + order.parts() + ", but " + against + " declares "
                        + declaration.parts() + "; the absorption order names every declared part once");
            }
            if (definition instanceof SpecialRateShortfallOrder order && !codes(order.parts()).equals(charged)) {
                problems.add(where + " orders the parts " + order.parts() + ", but the rows charge " + charged
                        + " at special rates (" + CapitalGainsPart.FACT + "); the shortfall order names each once");
            }
        });
    }

    /**
     * The part codes a version names: in its definition, or in its conditions — compared as codes, since a condition's
     * code is free text and may not even be shaped like a part.
     */
    private static List<String> namedCodes(RuleVersion version) {
        List<String> named = new ArrayList<>(conditionCodes(version.conditions()));
        version.definition().ifPresent(definition -> named.addAll(switch (definition) {
            case BucketPart bucket -> List.of(bucket.part().value());
            case CapitalGainsAbsorptionOrder order -> List.copyOf(codes(order.parts()));
            case SpecialRateShortfallOrder order -> List.copyOf(codes(order.parts()));
            default -> List.<String>of();
        }));
        return named;
    }

    private static boolean namesAPart(Reference reference) {
        return !namedCodes(reference.version()).isEmpty();
    }

    private static Set<String> declared(CapitalGainsParts parts) {
        return codes(parts.parts().stream().map(CapitalGainsParts.Declared::part).toList());
    }

    private static Set<String> codes(List<CapitalGainsPart> parts) {
        Set<String> codes = new LinkedHashSet<>();
        parts.forEach(part -> codes.add(part.value()));
        return codes;
    }

    /** The codes of {@code capitalgains.part} conditions, through {@code AllOf}, {@code AnyOf} and {@code Not}. */
    private static List<String> conditionCodes(List<Condition> conditions) {
        return conditions.stream().flatMap(CapitalGainsPartReferences::conditionCodes).toList();
    }

    private static Stream<String> conditionCodes(Condition condition) {
        return switch (condition) {
            case ItemFactIs is when is.fact().equals(CapitalGainsPart.FACT) -> Stream.of(is.code());
            case AllOf all -> all.all().stream().flatMap(CapitalGainsPartReferences::conditionCodes);
            case AnyOf any -> any.any().stream().flatMap(CapitalGainsPartReferences::conditionCodes);
            case Not not -> conditionCodes(not.condition());
            default -> Stream.empty();
        };
    }
}
