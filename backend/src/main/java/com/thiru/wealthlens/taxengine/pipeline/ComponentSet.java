package com.thiru.wealthlens.taxengine.pipeline;

import com.thiru.wealthlens.taxengine.rules.condition.HeadCode;
import com.thiru.wealthlens.taxengine.rules.id.ComponentCode;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The entered pay lines, partitioned into heads by the year's registry ({@code DR 06} G1.1 step 1, D-090).
 *
 * <p>Grouped, never summed: gross salary sums its head ({@code W-061}), while HRA and the standard deduction
 * need each employer's lines, so collapsing them here would lose what later steps read. The partition is the
 * whole of {@code D-CLOSEDSUM}: every line is under the head its registry entry declares, and a code with no
 * entry never reaches this value.
 *
 * @param byHead each head's lines, in the order entered
 */
public record ComponentSet(Map<HeadCode, List<Line>> byHead) implements NodeValue {

    /**
     * One entered pay line.
     *
     * @param employment its employment's position among the entered employments, from 0
     * @param code the component
     * @param amount the annual amount entered
     */
    public record Line(int employment, ComponentCode code, Money amount) {

        public Line {
            if (employment < 0 || code == null || amount == null) {
                throw new IllegalArgumentException("A line needs its employment's position, a code and an amount");
            }
        }
    }

    public ComponentSet {
        if (byHead == null) {
            throw new IllegalArgumentException("A component set's heads may not be null; pass an empty map");
        }
        Map<HeadCode, List<Line>> copied = new EnumMap<>(HeadCode.class);
        byHead.forEach((head, lines) -> copied.put(head, List.copyOf(lines)));
        byHead = Map.copyOf(copied);
    }

    /** One head's lines, in the order entered; empty where nothing was entered under it. */
    public List<Line> in(HeadCode head) {
        return byHead.getOrDefault(head, List.of());
    }

    /** Every line's amount added up — the trace's summary column only; it mixes heads, so nothing sums it. */
    @Override
    public Money asMoney() {
        return byHead.values().stream().flatMap(List::stream).map(Line::amount).reduce(Money.ZERO, Money::plus);
    }
}
