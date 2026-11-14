package com.thiru.wealthlens.taxengine.node;

import com.thiru.wealthlens.taxengine.pipeline.NodeContext;
import com.thiru.wealthlens.taxengine.rules.condition.Condition;
import com.thiru.wealthlens.taxengine.rules.condition.ConditionContext;
import com.thiru.wealthlens.taxengine.rules.condition.ItemFacts;
import com.thiru.wealthlens.taxengine.rules.id.ItemFactId;
import com.thiru.wealthlens.taxengine.rules.id.RuleSetId;
import com.thiru.wealthlens.taxengine.rules.pack.RuleRow;
import com.thiru.wealthlens.taxengine.rules.pack.TemporallyApportionedCap;
import com.thiru.wealthlens.taxengine.rules.temporal.RuleVersion;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Which optional facts an item may carry, as its rows read them (W-158a, D-357): a fact entered for an item no row of
 * the node's sets reads would be ignored, so it is refused — the rows say which kinds read it, not the input record.
 *
 * <p>A fact counts as read where a row <em>mentions</em> it and the row's other conditions on the item, those reading
 * none of the optional facts, hold. It is not whether flipping the fact changes the row reached, as
 * {@link EntryFacts} asks of arrears relief: the exclusion rows are conjunctions, so a credit card wholly for work
 * without kept records reaches none either way, yet the card's row reads the fact. The rows are asked whatever the
 * regime, since which facts the law reads is its structure, not the regime's.
 */
final class ReadFacts {

    private ReadFacts() {
    }

    /**
     * Refuses an optional fact the item carries that no row of {@code sets} reads for it.
     *
     * @param optional the facts the item may carry or not, in the order they are checked; one is entered when its code
     *     is {@code true}
     * @param what the item as a refusal names it
     */
    static void requireRead(NodeContext ctx, Set<RuleSetId> sets, ItemFacts item, List<ItemFactId> optional,
            String what) {
        List<RuleVersion> versions = sets.stream()
                .flatMap(set -> ctx.pack().ruleSet(set).members().stream())
                .map(ctx::rule)
                .map(row -> governing(ctx, row))
                .toList();
        ConditionContext about = ctx.conditions().about(item);
        for (ItemFactId fact : optional) {
            if (Boolean.parseBoolean(item.code(fact))
                    && versions.stream().noneMatch(version -> reads(version, fact, optional, about))) {
                throw new IllegalArgumentException(what + ": no row of " + sets.stream().map(RuleSetId::toString)
                        .sorted().collect(Collectors.joining(" or ")) + " reads " + fact + " for this item in TY "
                        + ctx.pack().taxYear().label() + "; it would be ignored, so it is refused");
            }
        }
    }

    private static RuleVersion governing(NodeContext ctx, RuleRow row) {
        return TemporallyApportionedCap.governingVersion(row, ctx.inputs().temporal());
    }

    /** Whether the version mentions the fact, and its item conditions reading no optional fact hold. */
    private static boolean reads(RuleVersion version, ItemFactId fact, List<ItemFactId> optional,
            ConditionContext about) {
        List<Condition> onItem = version.conditions().stream().filter(Condition::isAboutItem).toList();
        return onItem.stream().anyMatch(condition -> condition.itemFactsRead().contains(fact))
                && onItem.stream()
                        .filter(condition -> Collections.disjoint(condition.itemFactsRead(), optional))
                        .allMatch(condition -> condition.holds(about));
    }
}
