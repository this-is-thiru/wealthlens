package com.thiru.wealthlens.taxengine.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.id.NodeId;
import com.thiru.wealthlens.taxengine.rules.id.RuleSetId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The order comes from the edges, and the same edges always give the same order.
 */
class TopologicalSortTest {

    private static final NodeId COMPONENTS = new NodeId("components");
    private static final NodeId GROSS = new NodeId("salary.gross");
    private static final NodeId EXEMPT = new NodeId("salary.exemptions");
    private static final NodeId SALARY = new NodeId("salary.income");
    private static final NodeId HP = new NodeId("houseproperty.income");
    private static final NodeId GTI = new NodeId("gti");

    private record StubNode(NodeId produces, Set<NodeId> requires, Set<RuleSetId> ruleSets)
            implements ComputationNode {

        @Override
        public NodeResult evaluate(NodeContext ctx) {
            return NodeResult.untraced(MoneyValue.of(Money.ZERO));
        }
    }

    private static ComputationNode node(NodeId produces, NodeId... requires) {
        return new StubNode(produces, Set.of(requires), Set.of());
    }

    /** The salary head plus one other, as the real pipeline is shaped. */
    private static List<ComputationNode> salaryGraph() {
        return List.of(
                node(COMPONENTS),
                node(GROSS, COMPONENTS),
                node(EXEMPT, GROSS),
                node(SALARY, GROSS, EXEMPT),
                node(HP, COMPONENTS),
                node(GTI, SALARY, HP));
    }

    private static List<NodeId> orderOf(List<ComputationNode> nodes) {
        return TopologicalSort.of(nodes).stream().map(ComputationNode::produces).toList();
    }

    @Nested
    @DisplayName("order respects every edge")
    class Edges {

        @Test
        void everyNodeFollowsEverythingItRequires() {
            List<ComputationNode> ordered = TopologicalSort.of(salaryGraph());
            List<NodeId> positions = ordered.stream().map(ComputationNode::produces).toList();

            for (ComputationNode node : ordered) {
                for (NodeId required : node.requires()) {
                    assertThat(positions.indexOf(required))
                            .as("%s must come before %s", required, node.produces())
                            .isLessThan(positions.indexOf(node.produces()));
                }
            }
        }

        @Test
        void aSourceNodeComesFirstAndTheSinkLast() {
            List<NodeId> order = orderOf(salaryGraph());

            assertThat(order).startsWith(COMPONENTS).endsWith(GTI).hasSize(6);
        }

        @Test
        void aSingleNodeGraphIsItsOwnOrder() {
            assertThat(orderOf(List.of(node(COMPONENTS)))).containsExactly(COMPONENTS);
        }

        @Test
        void anEmptyPipelineOrdersToNothing() {
            assertThat(TopologicalSort.of(List.of())).isEmpty();
        }

        @Test
        void independentBranchesAreBothIncluded() {
            // salary and house property are independent subgraphs -- B3's parallelism note
            assertThat(orderOf(salaryGraph())).contains(SALARY, HP);
        }
    }

    @Nested
    @DisplayName("the order is canonical, because the trace is the result")
    class Determinism {

        @Test
        void theSameGraphGivesTheSameOrderWhateverTheInputOrder() {
            // TraceLine.sequence comes from this order and the trace IS the result. An order that
            // varied with iteration order would make identical inputs produce differing traces --
            // golden tests failing for no reason, and scenario diffs reporting changes nobody made.
            List<NodeId> expected = orderOf(salaryGraph());

            for (int attempt = 0; attempt < 20; attempt++) {
                List<ComputationNode> shuffled = new ArrayList<>(salaryGraph());
                Collections.shuffle(shuffled);

                assertThat(orderOf(shuffled)).isEqualTo(expected);
            }
        }

        @Test
        void tiesBreakOnNodeId() {
            // Three independent nodes: nothing orders them but their ids.
            NodeId a = new NodeId("aaa");
            NodeId m = new NodeId("mmm");
            NodeId z = new NodeId("zzz");

            assertThat(orderOf(List.of(node(z), node(a), node(m)))).containsExactly(a, m, z);
        }

        @Test
        void tiesAmongNodesFreedTogetherAlsoBreakOnNodeId() {
            NodeId root = new NodeId("root");
            NodeId b = new NodeId("branch.b");
            NodeId a = new NodeId("branch.a");

            assertThat(orderOf(List.of(node(root), node(b, root), node(a, root))))
                    .containsExactly(root, a, b);
        }
    }

    @Nested
    @DisplayName("a cycle fails with the cycle named")
    class Cycles {

        @Test
        void aTwoNodeCycleNamesBothNodes() {
            assertThatThrownBy(() -> TopologicalSort.of(List.of(
                            node(GROSS, EXEMPT), node(EXEMPT, GROSS))))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("dependency cycle")
                    .hasMessageContaining("salary.exemptions")
                    .hasMessageContaining("salary.gross");
        }

        @Test
        void theMessageDrawsThePathAndReturnsToItsStart() {
            // "Sorting failed" sends someone to read thirty node classes. a -> b -> c -> a is the
            // defect itself.
            NodeId a = new NodeId("aaa");
            NodeId b = new NodeId("bbb");
            NodeId c = new NodeId("ccc");

            assertThatThrownBy(() -> TopologicalSort.of(List.of(
                            node(a, c), node(b, a), node(c, b))))
                    .hasMessageContaining("aaa -> bbb -> ccc -> aaa");
        }

        @Test
        void aCycleReachableFromAnAcyclicPrefixIsStillNamed() {
            // The sortable part drains first; the cycle is what is left.
            assertThatThrownBy(() -> TopologicalSort.of(List.of(
                            node(COMPONENTS),
                            node(GROSS, COMPONENTS, SALARY),
                            node(SALARY, GROSS))))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("dependency cycle")
                    .hasMessageContaining("1 of 3 nodes could be ordered");
        }

        @Test
        void aSelfEdgeIsACycleOfOne() {
            assertThatThrownBy(() -> TopologicalSort.of(List.of(node(GROSS, GROSS))))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("dependency cycle");
        }
    }

    @Nested
    @DisplayName("a missing requires producer fails")
    class MissingProducer {

        @Test
        void aSlotNoNodeProducesIsNamedAlongsideWhatIsAvailable() {
            assertThatThrownBy(() -> TopologicalSort.of(List.of(node(SALARY, GROSS))))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("requires salary.gross")
                    .hasMessageContaining("which no node here produces")
                    .hasMessageContaining("salary.income");
        }

        @Test
        void theMessageOffersBothExplanations() {
            // Either the pipeline omitted a node, or the requiring node names a dead slot. The
            // reader cannot tell which from the graph, so the message says both.
            assertThatThrownBy(() -> TopologicalSort.of(List.of(node(GTI, SALARY))))
                    .hasMessageContaining("missing a node")
                    .hasMessageContaining("no longer exists");
        }

        @Test
        void aNodeProducedOutsideThisPipelineDoesNotCount() {
            // The full registry may produce it; this pipeline does not run that node, so the
            // edge cannot be satisfied and running anyway would read an uncomputed slot.
            assertThatThrownBy(() -> TopologicalSort.of(List.of(
                            node(COMPONENTS), node(GTI, SALARY, COMPONENTS))))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("requires salary.income");
        }
    }

    @Nested
    @DisplayName("rejections that are cheaper here than at run time")
    class Rejections {

        @Test
        void twoNodesProducingOneSlotIsAmbiguous() {
            assertThatThrownBy(() -> TopologicalSort.of(List.of(
                            node(GROSS, COMPONENTS), node(GROSS, EXEMPT))))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Two nodes produce");
        }

        @Test
        void aNullCollectionAndANullNodeAreBothRejected() {
            assertThatThrownBy(() -> TopologicalSort.of(null))
                    .isInstanceOf(IllegalArgumentException.class);

            List<ComputationNode> withNull = new ArrayList<>();
            withNull.add(null);
            assertThatThrownBy(() -> TopologicalSort.of(withNull))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
