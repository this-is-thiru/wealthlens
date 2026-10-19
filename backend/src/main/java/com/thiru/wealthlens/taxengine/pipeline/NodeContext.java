package com.thiru.wealthlens.taxengine.pipeline;

import com.thiru.wealthlens.taxengine.input.Assessee;
import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.input.Individual;
import com.thiru.wealthlens.taxengine.rules.condition.AgeSubject;
import com.thiru.wealthlens.taxengine.rules.condition.ConditionContext;
import com.thiru.wealthlens.taxengine.rules.condition.DisabilitySeverity;
import com.thiru.wealthlens.taxengine.rules.id.NodeId;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.id.RuleSetId;
import com.thiru.wealthlens.taxengine.rules.pack.DisallowanceSet;
import com.thiru.wealthlens.taxengine.rules.pack.RulePack;
import com.thiru.wealthlens.taxengine.rules.pack.RuleRow;
import com.thiru.wealthlens.taxengine.rules.pack.RuleSelection;
import com.thiru.wealthlens.taxengine.rules.pack.RuleSet;
import com.thiru.wealthlens.taxengine.rules.pack.SelectionGround;
import com.thiru.wealthlens.taxengine.rules.regime.DisallowanceSetId;
import com.thiru.wealthlens.taxengine.rules.regime.RegimeSpec;
import com.thiru.wealthlens.taxengine.rules.temporal.RuleVersion;
import java.time.Period;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * What one node is allowed to read.
 *
 * <h2>Scoped to the node, not to the run</h2>
 *
 * <p>{@code 15-review-findings.md} B2 makes a strong claim for this type: {@code get(NodeId)}
 * <i>"throws if the node was not declared in {@code requires()}"</i>, and <i>"that single check
 * makes an undeclared dependency a test failure rather than an ordering bug, which is what a
 * hand-kept list can never give you."</i>
 *
 * <p>B3 then sketches the runner building <b>one</b> context for the whole run and passing it to
 * every node. A shared context cannot know which node is asking, so it cannot make that check at
 * all — the property B2 sells is unimplementable as B3 draws it. So a context is <b>created per
 * node</b>, carrying that node's declarations, over a value store shared across the run:
 * {@link #forRun} once, {@link #forNode} per node.
 *
 * <p>The same discipline applies to rules. {@link #rule} refuses a {@link RuleId} the node did not
 * declare in {@link ComputationNode#rules()}, so the mapping invariant <b>I3</b> depends on cannot
 * quietly go stale: a node that evaluates a rule it never declared would otherwise make that rule
 * look unreachable while it is being applied.
 *
 * <p>Not a record, because the value store is mutated as the run proceeds and a record would
 * promise an immutability this cannot keep.
 */
public final class NodeContext {

    private final RulePack pack;
    private final RegimeSpec regime;
    private final EngineInputs inputs;

    /** Shared across every context of one run; written by the runner, read by the nodes. */
    private final Map<NodeId, NodeValue> computed;

    private final Set<NodeId> declaredRequires;
    private final Set<RuleSetId> declaredRuleSets;

    /** How this run recomputes a slot at another value; one that cannot says so when asked. */
    private final Recomputation recomputation;

    private NodeContext(
            RulePack pack,
            RegimeSpec regime,
            EngineInputs inputs,
            Map<NodeId, NodeValue> computed,
            Set<NodeId> declaredRequires,
            Set<RuleSetId> declaredRuleSets,
            Recomputation recomputation) {
        this.pack = pack;
        this.regime = regime;
        this.inputs = inputs;
        this.computed = computed;
        this.declaredRequires = declaredRequires;
        this.declaredRuleSets = declaredRuleSets;
        this.recomputation = recomputation;
    }

    /**
     * The run-level context. Declares nothing, so it can read no node — only the runner holds it,
     * to {@link #put} results and to derive each node's view.
     */
    public static NodeContext forRun(RulePack pack, RegimeSpec regime, EngineInputs inputs) {
        return forRun(pack, regime, inputs, (target, pinned, value) -> {
            throw new IllegalStateException("This context cannot re-enter the computation to recompute " + target
                    + "; only a pipeline run, or a NodeHarness given .recomputation(...), can");
        });
    }

    /** The same, able to recompute a slot at another value — the runner's, and a node test's (D-086). */
    public static NodeContext forRun(RulePack pack, RegimeSpec regime, EngineInputs inputs, Recomputation recomputation) {
        if (pack == null || regime == null || inputs == null || recomputation == null) {
            throw new IllegalArgumentException(
                    "A NodeContext needs a pack, a regime, the inputs and a recomputation; the engine fetches none"
                            + " of them for itself");
        }
        return new NodeContext(pack, regime, inputs, new LinkedHashMap<>(), Set.of(), Set.of(), recomputation);
    }

    /** This node's view: the same values, restricted to what it declared. */
    public NodeContext forNode(ComputationNode node) {
        if (node == null) {
            throw new IllegalArgumentException("Cannot scope a NodeContext to a null node");
        }
        return new NodeContext(
                pack, regime, inputs, computed, Set.copyOf(node.requires()),
                Set.copyOf(node.ruleSets()), recomputation);
    }

    /** Record a node's output. The runner's call, after {@code evaluate}. */
    public void put(NodeId node, NodeValue value) {
        if (node == null || value == null) {
            throw new IllegalArgumentException("A computed node and its value are both required");
        }
        if (computed.containsKey(node)) {
            throw new IllegalStateException(
                    "Node " + node + " has already been computed in this run; a node writes its"
                            + " slot once, or the graph is not a graph");
        }
        computed.put(node, value);
    }

    /**
     * A value this node declared it requires.
     *
     * @throws IllegalStateException if the node did not declare it, or if it has not been computed
     *     yet — the second means the topological order is wrong, and both are bugs rather than
     *     conditions to tolerate
     */
    public NodeValue get(NodeId node) {
        requireDeclared(node);
        NodeValue value = computed.get(node);
        if (value == null) {
            throw new IllegalStateException(
                    "Node " + node + " was declared in requires() but has not been computed yet;"
                            + " the evaluation order does not respect this edge");
        }
        return value;
    }

    /** The same, cast to the shape this node expects. */
    public <T extends NodeValue> T get(NodeId node, Class<T> as) {
        NodeValue value = get(node);
        if (!as.isInstance(value)) {
            throw new IllegalStateException(
                    "Node " + node + " produced a " + value.getClass().getSimpleName()
                            + " but this node expected a " + as.getSimpleName());
        }
        return as.cast(value);
    }

    /**
     * A slot this node declared, as it would be had another slot it declared held {@code value} — U for
     * surcharge marginal relief (D-086). The run is not changed: the recomputation is discarded after it
     * answers.
     *
     * @throws IllegalStateException if the node did not declare both slots, or if this context cannot
     *     re-enter
     */
    public NodeValue recomputed(NodeId target, NodeId pinned, NodeValue value) {
        requireDeclared(target);
        requireDeclared(pinned);
        if (value == null) {
            throw new IllegalArgumentException("Recomputing " + target + " needs a value for " + pinned);
        }
        return recomputation.recompute(target, pinned, value);
    }

    private void requireDeclared(NodeId node) {
        if (!declaredRequires.contains(node)) {
            throw new IllegalStateException(
                    "This node did not declare " + node + " in requires(), so it may not read it."
                            + " It declared " + declaredRequires + ". Add it to requires() -- the"
                            + " evaluation order is derived from that set, so reading outside it"
                            + " would work only by luck of ordering");
        }
    }

    /**
     * The row of an {@link RuleSelection#EXACTLY_ONE} set: the set's only row, or the one that applies here (D-252).
     *
     * @throws IllegalStateException if the node did not declare the set, the set takes another selection, or none or
     *     several of its rows apply
     */
    public RuleRow ruleOf(RuleSetId id) {
        List<RuleRow> chosen = chosen(id, RuleSelection.EXACTLY_ONE);
        if (chosen.isEmpty()) {
            throw new IllegalStateException("Pack " + pack.taxYear().label() + " holds no row of rule set " + id
                    + ", which takes exactly one");
        }
        return chosen.getFirst();
    }

    /** The id of {@link #ruleOf}'s row — what a trace line, a definition or a salary base is keyed on. */
    public RuleId ruleIdOf(RuleSetId id) {
        return ruleOf(id).id();
    }

    /** The row of an {@link RuleSelection#AT_MOST_ONE} set, where the year holds one (D-252). */
    public Optional<RuleRow> optionalRuleOf(RuleSetId id) {
        return chosen(id, RuleSelection.AT_MOST_ONE).stream().findFirst();
    }

    /**
     * Every row of an {@link RuleSelection#ALL_THAT_APPLY} set the year holds, in the set's order (D-252). The node
     * applies each the regime allows, and traces the rest as it always has.
     */
    public List<RuleRow> rulesOf(RuleSetId id) {
        return rowsOf(declared(id, RuleSelection.ALL_THAT_APPLY));
    }

    /**
     * A row of a set this node declared — for a node that reads a row it already chose, or names its id in a trace.
     *
     * @throws IllegalStateException if no set the node declared holds the row
     */
    public RuleRow rule(RuleId id) {
        boolean declared = declaredRuleSets.stream()
                .map(set -> pack.ruleSets().get(set))
                .anyMatch(set -> set != null && set.members().contains(id));
        if (!declared) {
            throw new IllegalStateException(
                    "This node declared no rule set holding " + id + ", so it may not evaluate it."
                            + " It declared " + declaredRuleSets + ". Reachability (invariant I3) is"
                            + " computed from those sets, so an undeclared rule would look dead while"
                            + " being applied");
        }
        return pack.rule(id);
    }

    private RuleSet declared(RuleSetId id, RuleSelection expected) {
        if (!declaredRuleSets.contains(id)) {
            throw new IllegalStateException("This node did not declare rule set " + id + " in ruleSets(), so it may"
                    + " not evaluate it. It declared " + declaredRuleSets);
        }
        RuleSet set = pack.ruleSet(id);
        if (set.selection() != expected) {
            throw new IllegalStateException("Rule set " + id + " takes " + set.selection() + ", but the node reads it"
                    + " as " + expected + "; the pack and the node disagree on how many rows it may take");
        }
        return set;
    }

    private List<RuleRow> rowsOf(RuleSet set) {
        return set.members().stream().map(pack::rule).toList();
    }

    /** The set's only row, or the one of several that applies on the set's ground; never several. */
    private List<RuleRow> chosen(RuleSetId id, RuleSelection expected) {
        RuleSet set = declared(id, expected);
        List<RuleRow> rows = rowsOf(set);
        if (rows.size() <= 1) {
            return rows;
        }
        List<RuleRow> applying = rows.stream().filter(row -> appliesHere(row, set.ground())).toList();
        if (applying.size() != 1) {
            throw new IllegalStateException("Rule set " + id + " has " + applying.size() + " of its rows "
                    + rows.stream().map(RuleRow::id).toList() + " applying in regime " + regime.code() + " for "
                    + pack.taxYear().label() + "; exactly one must");
        }
        return applying;
    }

    private boolean appliesHere(RuleRow row, SelectionGround ground) {
        RuleVersion version = row.versionAt(inputs.temporal());
        if (!inRegime(row.id(), version).applies()) {
            return false;
        }
        if (ground == SelectionGround.REGIME) {
            return true;
        }
        ConditionContext facts = conditions();
        return version.conditions().stream().allMatch(condition -> condition.holds(facts));
    }

    /**
     * What a rule's conditions are tested against, drawn from the inputs (D-072).
     *
     * <p>Residence collapses to the boolean the conditions read here, at the point of use, as
     * {@code Assessee.ResidentialStatus} says it should: a person "not ordinarily resident" is a
     * resident, because the Act scopes such a person inside "a person, who is a resident" (S2
     * s.5(1)(c)). An individual's age is the completed years on the last day of the tax year —
     * the highest age reached in it — which is what "at any time during the tax year" asks
     * (S2 s.2(100)). An {@code Huf} has no age, so a condition that needs one fails loudly.
     */
    public ConditionContext conditions() {
        Assessee assessee = inputs.assessee();
        Map<AgeSubject, Integer> ages = new EnumMap<>(AgeSubject.class);
        Map<AgeSubject, DisabilitySeverity> disabilities = new EnumMap<>(AgeSubject.class);
        if (assessee instanceof Individual individual) {
            ages.put(AgeSubject.SELF,
                    Period.between(individual.dateOfBirth(), inputs.temporal().taxYearEnd()).getYears());
            individual.disability().ifPresent(severity -> disabilities.put(AgeSubject.SELF, severity));
        }
        boolean resident = assessee.residence() != Assessee.ResidentialStatus.NON_RESIDENT;
        return new ConditionContext(resident, assessee.type(), ages, disabilities);
    }

    /**
     * Whether a rule applies in this regime, and why not where it does not (D-165): first the version's own
     * {@code regimes}, then every disallowance set the regime references. The one place a node asks — a node reading
     * {@code regimes().appliesIn} itself would apply a rule the regime forbids ({@code ArchitectureTest}).
     *
     * @param rule the rule the version belongs to, which is what a set lists
     * @param version the version in force for the node's purpose
     */
    public RegimeVerdict inRegime(RuleId rule, RuleVersion version) {
        if (!version.regimes().appliesIn(regime.code())) {
            return new RegimeVerdict.ExcludedByRow(regime.code());
        }
        for (DisallowanceSetId id : regime.disallowances()) {
            DisallowanceSet set = pack.disallowanceSets().get(id);
            if (set == null) {
                throw new IllegalStateException("Regime " + regime.code() + " references disallowance set " + id
                        + ", which pack " + pack.taxYear().label() + " does not define (invariant I6)");
            }
            if (set.disallows(rule)) {
                return new RegimeVerdict.Disallowed(regime.code(), set);
            }
        }
        return new RegimeVerdict.Applies();
    }

    public RulePack pack() {
        return pack;
    }

    public RegimeSpec regime() {
        return regime;
    }

    public EngineInputs inputs() {
        return inputs;
    }

    /** Whether a node has been computed — for the runner, not for a node's arithmetic. */
    public boolean hasValueFor(NodeId node) {
        return computed.containsKey(node);
    }
}
