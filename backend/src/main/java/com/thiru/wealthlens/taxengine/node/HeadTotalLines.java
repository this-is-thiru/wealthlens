package com.thiru.wealthlens.taxengine.node;

import com.thiru.wealthlens.taxengine.pipeline.ComponentSet;
import com.thiru.wealthlens.taxengine.pipeline.NodeContext;
import com.thiru.wealthlens.taxengine.rules.definition.HeadTotal;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.util.List;
import java.util.Map;

/**
 * The lines a {@link HeadTotal} counts: the head's entered lines whose component feeds the defining rule
 * (D-093).
 *
 * <p>A head is not all one thing. Professional tax sits under "Salaries" because s.19 deducts it there, yet it
 * is paid out of salary rather than being salary; summing the whole head would raise gross salary by the tax.
 * The registry's {@code feedsInto} is the declared link from a component to the statute that reads it, so the
 * rule, not the head alone, decides what is counted.
 *
 * @param lines the counted lines, in the order entered
 */
record HeadTotalLines(List<ComponentSet.Line> lines) {

    HeadTotalLines {
        lines = List.copyOf(lines);
    }

    static HeadTotalLines of(NodeContext ctx, ComponentSet components, HeadTotal total, RuleId rule) {
        return new HeadTotalLines(components.in(total.head()).stream()
                .filter(line -> ctx.pack().components().get(line.code()).feedsInto().contains(rule))
                .toList());
    }

    Money total() {
        return lines.stream().map(ComponentSet.Line::amount).reduce(Money.ZERO, Money::plus);
    }

    /** The counted lines under their head, for a trace line's {@code in}; empty where none were counted. */
    ComponentSet asComponentSet(HeadTotal total) {
        return new ComponentSet(lines.isEmpty() ? Map.of() : Map.of(total.head(), lines));
    }
}
