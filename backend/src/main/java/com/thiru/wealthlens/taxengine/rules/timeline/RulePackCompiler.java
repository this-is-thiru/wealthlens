package com.thiru.wealthlens.taxengine.rules.timeline;

import com.thiru.wealthlens.taxengine.rules.id.NodeId;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.pack.RulePackRef;
import com.thiru.wealthlens.taxengine.rules.pack.RuleRow;
import com.thiru.wealthlens.taxengine.rules.registry.PublicationStatus;
import com.thiru.wealthlens.taxengine.rules.temporal.RuleVersion;
import com.thiru.wealthlens.taxengine.rules.temporal.Validity;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Function;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Compiles one tax year's rule pack from the {@link Timeline} (D-034, D-054).
 *
 * <p>A year holds every {@code PUBLISHED} version whose validity overlaps it, plus every
 * {@code PUBLISHED} pending-notification version — those have no dates, and invariant I16 needs their
 * interim rule in the pack. A rule left with no version is omitted; a reference to it then fails I2,
 * as it would today. A regime is included when it is in force during the year.
 *
 * <p>The output is canonical — keys sorted, no whitespace, identities sorted, note fields
 * ({@code _}-prefixed) dropped — so a checksum moves when the law moves and not when a note does.
 */
public final class RulePackCompiler {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private RulePackCompiler() {
    }

    public static CompiledPack compile(Timeline timeline, TaxYearId taxYear) {
        Map<RuleId, String> ruleChecksums = new HashMap<>();
        ArrayNode rules = MAPPER.createArrayNode();
        for (RuleId id : sorted(timeline.rules().keySet(), RuleId::value)) {
            List<Integer> kept = versionsIn(timeline.rules().get(id), taxYear);
            if (kept.isEmpty()) {
                continue;
            }
            JsonNode rule = canonical(withVersions(MAPPER.readTree(timeline.ruleSources().get(id)), kept));
            rules.add(rule);
            ruleChecksums.put(id, RulePackRef.checksumOf(MAPPER.writeValueAsString(rule)));
        }
        ArrayNode regimes = MAPPER.createArrayNode();
        for (RegimeCode code : sorted(timeline.regimes().keySet(), RegimeCode::value)) {
            if (timeline.regimes().get(code).lifecycle().inForceWindow().overlapsTaxYear(taxYear)) {
                regimes.add(MAPPER.readTree(timeline.regimeSources().get(code)));
            }
        }
        ArrayNode nodes = MAPPER.createArrayNode();
        sorted(timeline.pipelineNodes().keySet(), NodeId::value)
                .forEach(id -> nodes.add(MAPPER.readTree(timeline.pipelineNodes().get(id))));

        Map<String, String> files = new HashMap<>();
        files.put("law/rates.json", render(object().set("rules", rules)));
        files.put("law/regimes.json", render(object()
                .<ObjectNode>set("regimes", regimes)
                .<ObjectNode>set("disallowanceSets", MAPPER.createArrayNode())
                .set("conditionSets", MAPPER.createArrayNode())));
        files.put("exec/pipelines.json", render(object()
                .<ObjectNode>set("nodes", nodes)
                .set("axes", object().set("bindings", MAPPER.createArrayNode()))));
        files.put("exec/components.json", render(object().set("components", MAPPER.createArrayNode())));
        files.put("forms/itr-forms.json", render(object().set("forms", MAPPER.createArrayNode())));
        files.put("content/explanations.json", render(object().set("explanations", MAPPER.createArrayNode())));
        return new CompiledPack(taxYear, files, ruleChecksums);
    }

    /** The positions, in the rule's own order, of the versions this year's pack holds. */
    private static List<Integer> versionsIn(RuleRow row, TaxYearId taxYear) {
        List<Integer> kept = new ArrayList<>();
        for (int i = 0; i < row.versions().size(); i++) {
            RuleVersion version = row.versions().get(i);
            boolean published = version.publicationStatus() == PublicationStatus.PUBLISHED;
            boolean pending = version.validity() instanceof Validity.PendingNotification;
            boolean inYear = version.validity().window().map(w -> w.overlapsTaxYear(taxYear)).orElse(false);
            if (published && (pending || inYear)) {
                kept.add(i);
            }
        }
        return kept;
    }

    private static JsonNode withVersions(JsonNode rule, List<Integer> kept) {
        ArrayNode versions = MAPPER.createArrayNode();
        kept.forEach(i -> versions.add(rule.path("versions").get(i)));
        return ((ObjectNode) rule).set("versions", versions);
    }

    /** Identifiers are not {@code Comparable}; a pack orders them by their text. */
    private static <T> List<T> sorted(Collection<T> ids, Function<T, String> text) {
        return ids.stream().sorted(Comparator.comparing(text)).toList();
    }

    /** Keys sorted, note fields dropped, arrays in their own order. */
    static JsonNode canonical(JsonNode node) {
        if (node.isObject()) {
            ObjectNode sorted = object();
            new TreeSet<>(node.propertyNames()).stream()
                    .filter(name -> !name.startsWith("_"))
                    .forEach(name -> sorted.set(name, canonical(node.get(name))));
            return sorted;
        }
        if (node.isArray()) {
            ArrayNode copy = MAPPER.createArrayNode();
            node.forEach(element -> copy.add(canonical(element)));
            return copy;
        }
        return node;
    }

    static String render(JsonNode node) {
        return MAPPER.writeValueAsString(canonical(node));
    }

    private static ObjectNode object() {
        return MAPPER.createObjectNode();
    }
}
