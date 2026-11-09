package com.thiru.wealthlens.taxengine.rules.timeline;

import com.thiru.wealthlens.taxengine.rules.component.ComponentDefinition;
import com.thiru.wealthlens.taxengine.rules.component.EmployerFundDefinition;
import com.thiru.wealthlens.taxengine.rules.declaration.DeclarationDefinition;
import com.thiru.wealthlens.taxengine.rules.id.ComponentCode;
import com.thiru.wealthlens.taxengine.rules.id.DeclarationCode;
import com.thiru.wealthlens.taxengine.rules.id.EmployerFundCode;
import com.thiru.wealthlens.taxengine.rules.id.ItrFormCode;
import com.thiru.wealthlens.taxengine.rules.id.NodeId;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.pack.RuleRow;
import com.thiru.wealthlens.taxengine.rules.regime.ConditionSetId;
import com.thiru.wealthlens.taxengine.rules.regime.DisallowanceSetId;
import com.thiru.wealthlens.taxengine.rules.regime.RegimeSpec;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The law authored once, across every year (D-034): the source each tax year's rule pack is compiled
 * from, so that a regime living ten years is one file rather than ten copies free to drift.
 *
 * @param rules every rule with all its versions, by id
 * @param regimes every regime, by code
 * @param ruleSets {@code law/rule-sets.json} verbatim, where the timeline has one: the rule sets every year's pack
 *     declares, each row naming its own (D-252). A shipped pack's rows are each in a set ({@code PackValidator})
 * @param ruleSources each rule's file verbatim — what a year's pack is compiled from, since the parsed
 *     row cannot be written back
 * @param regimeSources each regime's file verbatim, for the same reason
 * @param disallowanceSetSources each disallowance set's file verbatim, by id — a set holds a version per span of
 *     years, and a year's pack is compiled from the one in force (D-165)
 * @param conditionSetSources each eligibility condition set's file verbatim, by id, versioned as a disallowance set
 *     is (W-244)
 * @param components every pay component, by code (D-089) — each arrives with the first rule that reads it
 * @param componentSources each component's file verbatim, for the reason {@code ruleSources} is kept
 * @param funds every employer fund, by code (W-238e)
 * @param fundSources each fund's file verbatim, for the same reason
 * @param declarations every declaration the law has asked, by code (W-090a1)
 * @param declarationSources each declaration's file verbatim, for the same reason
 * @param itrFormSources each return form's file verbatim, by code, versioned as a condition set is (W-090b)
 * @param pipelineNodes the exec node catalogue, each node's file verbatim. Nothing reads its fields
 *     yet; the item that generates node ids from it gives it a type (D-053)
 * @param coverage the coverage register ({@code content/coverage.json}) verbatim, where the timeline
 *     has one. It describes the whole timeline rather than a year, so no year's pack is compiled from it
 *     (D-059)
 * @param sections the section catalogue ({@code content/sections/}), each entry's file verbatim by its
 *     key. Like the register it describes the whole timeline, so no year's pack is compiled from it
 *     (D-060)
 * @param acts each Act's section list ({@code content/acts/}), verbatim by the Act's code: what the
 *     coverage register must list exactly, for an Act that has one (D-066)
 */
public record Timeline(
        Map<RuleId, RuleRow> rules,
        Optional<String> ruleSets,
        Map<RegimeCode, RegimeSpec> regimes,
        Map<RuleId, String> ruleSources,
        Map<RegimeCode, String> regimeSources,
        Map<DisallowanceSetId, String> disallowanceSetSources,
        Map<ConditionSetId, String> conditionSetSources,
        Map<ComponentCode, ComponentDefinition> components,
        Map<ComponentCode, String> componentSources,
        Map<EmployerFundCode, EmployerFundDefinition> funds,
        Map<EmployerFundCode, String> fundSources,
        Map<DeclarationCode, DeclarationDefinition> declarations,
        Map<DeclarationCode, String> declarationSources,
        Map<ItrFormCode, String> itrFormSources,
        Map<NodeId, String> pipelineNodes,
        Optional<String> coverage,
        Map<String, String> sections,
        Map<String, String> acts) {

    public Timeline {
        rules = Map.copyOf(rules);
        ruleSets = Objects.requireNonNull(ruleSets, "ruleSets: Optional.empty() where the timeline has none");
        regimes = Map.copyOf(regimes);
        ruleSources = Map.copyOf(ruleSources);
        regimeSources = Map.copyOf(regimeSources);
        disallowanceSetSources = Map.copyOf(disallowanceSetSources);
        conditionSetSources = Map.copyOf(conditionSetSources);
        components = Map.copyOf(components);
        componentSources = Map.copyOf(componentSources);
        funds = Map.copyOf(funds);
        fundSources = Map.copyOf(fundSources);
        declarations = Map.copyOf(declarations);
        declarationSources = Map.copyOf(declarationSources);
        itrFormSources = Map.copyOf(itrFormSources);
        pipelineNodes = Map.copyOf(pipelineNodes);
        coverage = Objects.requireNonNull(coverage, "coverage: Optional.empty() where the timeline has none");
        sections = Map.copyOf(sections);
        acts = Map.copyOf(acts);
    }
}
