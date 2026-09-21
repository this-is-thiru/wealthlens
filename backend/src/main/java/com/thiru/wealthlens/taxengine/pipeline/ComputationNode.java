package com.thiru.wealthlens.taxengine.pipeline;

import com.thiru.wealthlens.taxrules.id.NodeId;
import com.thiru.wealthlens.taxrules.id.RuleId;
import java.util.Set;

/**
 * One step of the computation, as a node in a graph rather than a line in a list.
 *
 * <h2>Why a graph</h2>
 *
 * <p>{@code 15-review-findings.md} B2 and B3. Values flow between nodes <b>by {@link NodeId}, never
 * by field</b>, and the order of evaluation is <i>derived</i> from {@link #requires()} rather than
 * declared anywhere. Three things follow that a hand-ordered list cannot give:
 *
 * <ul>
 *   <li>a taxpayer with no employment simply yields zero at {@code salary.gross}, and everything
 *       downstream is unaffected because it depends on the <b>node</b>, not on an employment
 *       existing — the list version needed eleven salary steps that each tolerated emptiness (D-10);
 *   <li>independent heads are independent subgraphs, so they can be evaluated in parallel — of no
 *       interest at 50 ms and of considerable interest at 24,000 optimiser evaluations;
 *   <li>the graph <i>is</i> the explanation, so attributing a difference between two scenarios is a
 *       graph diff rather than a diff of line positions ({@code A1.2}).
 * </ul>
 *
 * <p>A node is <b>pure</b>: it reads only what {@link #requires()} named, writes only
 * {@link #produces()}, and <b>throws</b> on any condition it cannot evaluate ({@code D-NOSILENT}).
 * It does not catch, and it never substitutes a zero for a failure — a rule that could not be
 * evaluated and was quietly replaced with zero is how a deduction vanishes and someone files on the
 * result.
 */
public interface ComputationNode {

    /** The single slot this node writes. */
    NodeId produces();

    /**
     * The slots this node reads — the edges of the graph.
     *
     * <p>Declaring them is not documentation: {@link NodeContext#get} refuses anything not named
     * here, so an undeclared dependency is a test failure rather than a bug that shows up as a
     * wrong order under a different pipeline.
     */
    Set<NodeId> requires();

    /**
     * The rules this node evaluates.
     *
     * <p>For provenance, and for invariant <b>I3</b>: a pack rule named by no node is a dead rule,
     * which is {@code O-19}'s failure — a deduction present in the pack, absent from every answer,
     * and silent about it. {@link NodeRegistry#rulesNotEvaluatedByAnyNode} is that check.
     */
    Set<RuleId> rules();

    /**
     * Compute it.
     *
     * @param ctx scoped to this node — see {@link NodeContext}
     */
    NodeResult evaluate(NodeContext ctx);
}
