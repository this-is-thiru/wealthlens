package com.thiru.wealthlens.taxengine.rules.timeline;

import com.thiru.wealthlens.taxengine.rules.component.SalaryBaseInclusion;
import com.thiru.wealthlens.taxengine.rules.id.ComponentCode;
import com.thiru.wealthlens.taxengine.rules.id.DeclarationCode;
import com.thiru.wealthlens.taxengine.rules.id.EmployerFundCode;
import com.thiru.wealthlens.taxengine.rules.id.ItrFormCode;
import com.thiru.wealthlens.taxengine.rules.id.NodeId;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.json.ValueJson;
import com.thiru.wealthlens.taxengine.rules.pack.RulePackRef;
import com.thiru.wealthlens.taxengine.rules.pack.RuleRow;
import com.thiru.wealthlens.taxengine.rules.regime.ConditionSetId;
import com.thiru.wealthlens.taxengine.rules.regime.DisallowanceSetId;
import com.thiru.wealthlens.taxengine.rules.registry.PublicationStatus;
import com.thiru.wealthlens.taxengine.rules.temporal.RuleVersion;
import com.thiru.wealthlens.taxengine.rules.temporal.Validity;
import com.thiru.wealthlens.taxengine.rules.validate.PackValidationException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Stream;
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
 * <p>A component is projected into the year: its {@code feedsInto} and {@code salaryBaseFor} keep only the rules
 * the year holds, since a pay line outlives a rule the law states for one year only — an entertainment allowance is
 * salary in both years, deductible in one (D-105). That would hide a misspelt rule, so a reference to a rule the
 * timeline states in no year is refused here, before any year is compiled. A component's or fund's {@code liquidity}
 * keeps only the periods that overlap the year (W-238e1).
 *
 * <p>The output is canonical — keys sorted, no whitespace, identities sorted, note fields
 * ({@code _}-prefixed) dropped — so a checksum moves when the law moves and not when a note does.
 */
public final class RulePackCompiler {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private RulePackCompiler() {
    }

    public static CompiledPack compile(Timeline timeline, TaxYearId taxYear) {
        requireComponentReferencesOnTheTimeline(timeline);
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
        ArrayNode regimes = regimes(timeline, taxYear);
        ArrayNode disallowanceSets = disallowanceSets(timeline, taxYear);
        ArrayNode conditionSets = conditionSets(timeline, taxYear);
        ArrayNode components = components(timeline, taxYear, ruleChecksums.keySet());
        ArrayNode funds = funds(timeline, taxYear, ruleChecksums.keySet());
        ArrayNode declarations = declarations(timeline, taxYear);
        ArrayNode nodes = nodes(timeline);

        Map<String, String> files = new HashMap<>();
        ObjectNode rates = object().set("rules", rules);
        timeline.ruleSets().ifPresent(sets -> rates.set("ruleSets", MAPPER.readTree(sets).path("ruleSets")));
        files.put("law/rates.json", render(rates));
        files.put("law/regimes.json", render(object()
                .<ObjectNode>set("regimes", regimes)
                .<ObjectNode>set("disallowanceSets", disallowanceSets)
                .set("conditionSets", conditionSets)));
        files.put("exec/pipelines.json", render(object()
                .<ObjectNode>set("nodes", nodes)
                .set("axes", object().set("bindings", MAPPER.createArrayNode()))));
        files.put("exec/components.json", render(object().set("components", components)));
        files.put("exec/funds.json", render(object().set("funds", funds)));
        files.put("exec/declarations.json", render(object().set("declarations", declarations)));
        files.put("forms/itr-forms.json", render(object().set("forms", itrForms(timeline, taxYear))));
        files.put("content/explanations.json", render(object().set("explanations", MAPPER.createArrayNode())));
        return new CompiledPack(taxYear, files, ruleChecksums);
    }

    /** The regimes in force at some time in the year, each with the election version for the year. */
    private static ArrayNode regimes(Timeline timeline, TaxYearId taxYear) {
        ArrayNode regimes = MAPPER.createArrayNode();
        for (RegimeCode code : sorted(timeline.regimes().keySet(), RegimeCode::value)) {
            if (timeline.regimes().get(code).lifecycle().inForceWindow().overlapsTaxYear(taxYear)) {
                regimes.add(RegimeElections.forTaxYear(MAPPER.readTree(timeline.regimeSources().get(code)), taxYear,
                        "law/regimes/" + code.value() + ".json"));
            }
        }
        return regimes;
    }

    /** Each disallowance set's version for the year, where it has one. */
    private static ArrayNode disallowanceSets(Timeline timeline, TaxYearId taxYear) {
        ArrayNode disallowanceSets = MAPPER.createArrayNode();
        for (DisallowanceSetId id : sorted(timeline.disallowanceSetSources().keySet(), DisallowanceSetId::value)) {
            SetVersions.forTaxYear(MAPPER.readTree(timeline.disallowanceSetSources().get(id)), taxYear,
                    "law/disallowances/" + id.value() + ".json").ifPresent(disallowanceSets::add);
        }
        return disallowanceSets;
    }

    /** The return forms in force in the year, each as its year's version (W-090b, D-521 C3a). */
    private static ArrayNode itrForms(Timeline timeline, TaxYearId taxYear) {
        ArrayNode forms = MAPPER.createArrayNode();
        for (ItrFormCode code : sorted(timeline.itrFormSources().keySet(), ItrFormCode::value)) {
            SetVersions.forTaxYear(MAPPER.readTree(timeline.itrFormSources().get(code)), taxYear,
                    "forms/itr-forms/" + code.value() + ".json").ifPresent(forms::add);
        }
        return forms;
    }

    /** The eligibility condition sets in force in the year, each as its year's version (W-244). */
    private static ArrayNode conditionSets(Timeline timeline, TaxYearId taxYear) {
        ArrayNode conditionSets = MAPPER.createArrayNode();
        for (ConditionSetId id : sorted(timeline.conditionSetSources().keySet(), ConditionSetId::value)) {
            SetVersions.forTaxYear(MAPPER.readTree(timeline.conditionSetSources().get(id)), taxYear,
                    "law/condition-sets/" + id.value() + ".json").ifPresent(conditionSets::add);
        }
        return conditionSets;
    }

    /** The components live in the year, each projected onto the rules the year's pack holds. */
    private static ArrayNode components(Timeline timeline, TaxYearId taxYear, Set<RuleId> rules) {
        ArrayNode components = MAPPER.createArrayNode();
        for (ComponentCode code : sorted(timeline.components().keySet(), ComponentCode::value)) {
            if (timeline.components().get(code).lifecycle().isLiveIn(taxYear)) {
                components.add(canonical(liquidityIn(projected(MAPPER.readTree(timeline.componentSources().get(code)),
                        rules), taxYear)));
            }
        }
        return components;
    }

    /** The employer funds live in the year, each projected onto the rules the year's pack holds (W-238e). */
    private static ArrayNode funds(Timeline timeline, TaxYearId taxYear, Set<RuleId> rules) {
        ArrayNode funds = MAPPER.createArrayNode();
        for (EmployerFundCode code : sorted(timeline.funds().keySet(), EmployerFundCode::value)) {
            if (timeline.funds().get(code).validity().coversTaxYear(taxYear)) {
                ObjectNode fund = (ObjectNode) MAPPER.readTree(timeline.fundSources().get(code));
                funds.add(canonical(liquidityIn(fund.set("feedsInto", held(fund.path("feedsInto"), rules)), taxYear)));
            }
        }
        return funds;
    }

    /**
     * The declarations the law asks at any time in the year (W-090a1): a question added or dropped mid-year is still
     * one the year's return can ask, so a row is kept where its validity overlaps the year, not only where it covers it.
     */
    private static ArrayNode declarations(Timeline timeline, TaxYearId taxYear) {
        ArrayNode declarations = MAPPER.createArrayNode();
        for (DeclarationCode code : sorted(timeline.declarations().keySet(), DeclarationCode::value)) {
            if (timeline.declarations().get(code).validity().overlapsTaxYear(taxYear)) {
                declarations.add(canonical(MAPPER.readTree(timeline.declarationSources().get(code))));
            }
        }
        return declarations;
    }

    private static ArrayNode nodes(Timeline timeline) {
        ArrayNode nodes = MAPPER.createArrayNode();
        sorted(timeline.pipelineNodes().keySet(), NodeId::value)
                .forEach(id -> nodes.add(MAPPER.readTree(timeline.pipelineNodes().get(id))));
        return nodes;
    }

    private static void requireComponentReferencesOnTheTimeline(Timeline timeline) {
        Set<String> problems = new TreeSet<>();
        timeline.components().values().forEach(component -> {
            Stream.concat(component.feedsInto().stream(),
                            component.salaryBaseFor().stream().map(SalaryBaseInclusion::rule))
                    .filter(rule -> !timeline.rules().containsKey(rule))
                    .forEach(rule -> problems.add("component " + component.code() + " names " + rule
                            + ", which the timeline states in no year"));
        });
        timeline.funds().values().forEach(fund -> fund.feedsInto().stream()
                .filter(rule -> !timeline.rules().containsKey(rule))
                .forEach(rule -> problems.add("employer fund " + fund.code() + " names " + rule
                        + ", which the timeline states in no year")));
        if (!problems.isEmpty()) {
            throw new PackValidationException(String.join("; ", problems));
        }
    }

    /** The component as the year sees it: references to rules the year does not hold are dropped. */
    private static JsonNode projected(JsonNode component, Set<RuleId> held) {
        ObjectNode copy = (ObjectNode) component.deepCopy();
        ArrayNode feeds = held(component.path("feedsInto"), held);
        ArrayNode bases = MAPPER.createArrayNode();
        component.path("salaryBaseFor").forEach(inclusion -> {
            if (held.contains(new RuleId(inclusion.path("rule").asString()))) {
                bases.add(inclusion);
            }
        });
        copy.set("feedsInto", feeds);
        copy.set("salaryBaseFor", bases);
        return copy;
    }

    /**
     * The row as the year sees its liquidity (W-238e1): only the terms and the unstated periods that overlap the year,
     * since a term the regulator replaced mid-year belongs to both years it touches and to no other.
     */
    private static JsonNode liquidityIn(JsonNode row, TaxYearId taxYear) {
        if (!row.path("liquidity").isObject()) {
            return row;
        }
        ObjectNode copy = (ObjectNode) row.deepCopy();
        ObjectNode liquidity = (ObjectNode) copy.get("liquidity");
        for (String periods : List.of("terms", "unstated")) {
            ArrayNode kept = MAPPER.createArrayNode();
            liquidity.path(periods).forEach(period -> {
                if (ValueJson.dateRange(period.path("validity"), "liquidity." + periods).overlapsTaxYear(taxYear)) {
                    kept.add(period);
                }
            });
            liquidity.set(periods, kept);
        }
        return copy;
    }

    /** The rule ids in {@code rules} the year's pack holds, in their own order. */
    private static ArrayNode held(JsonNode rules, Set<RuleId> held) {
        ArrayNode kept = MAPPER.createArrayNode();
        rules.forEach(rule -> {
            if (held.contains(new RuleId(rule.asString()))) {
                kept.add(rule);
            }
        });
        return kept;
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
