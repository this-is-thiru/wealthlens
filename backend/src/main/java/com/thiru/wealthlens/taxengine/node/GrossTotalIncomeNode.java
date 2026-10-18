package com.thiru.wealthlens.taxengine.node;

import com.thiru.wealthlens.taxengine.pipeline.ComputationNode;
import com.thiru.wealthlens.taxengine.pipeline.HeadsAfterSetOff;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeContext;
import com.thiru.wealthlens.taxengine.pipeline.NodeResult;
import com.thiru.wealthlens.taxengine.rules.definition.CapitalGainsPart;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.id.NodeId;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.util.Set;

/**
 * Gross total income: the total income before any Chapter VIII deduction (S2 s.122(10); D-132).
 *
 * <p>Salaries, house property and other sources are the heads the engine computes; it is their income after a head's
 * loss is set off against the others ({@code setoff}, D-217), capital gains with them (D-243); the business head joins
 * through set-off when it is built. Untraced: set-off carries the lines.
 *
 * <p>Until the special rates are applied (W-088b), a capital gain charged at one would be taxed through the slab ladder
 * here, so a non-nil special-rate part is refused rather than passed on.
 */
public final class GrossTotalIncomeNode implements ComputationNode {

    @Override
    public NodeId produces() {
        return NodeIds.GTI;
    }

    @Override
    public Set<NodeId> requires() {
        return Set.of(NodeIds.SETOFF);
    }

    @Override
    public Set<RuleId> rules() {
        return Set.of();
    }

    @Override
    public NodeResult evaluate(NodeContext ctx) {
        HeadsAfterSetOff heads = ctx.get(NodeIds.SETOFF, HeadsAfterSetOff.class);
        heads.capitalGains().forEach((part, amount) -> {
            if (part != CapitalGainsPart.SHORT_TERM_SLAB && amount.isGreaterThan(Money.ZERO)) {
                throw new IllegalStateException("Capital gains of " + amount + " are charged at a special rate (" + part
                        + "), which the engine does not yet apply (W-088b); they are not taxed at slab rates instead");
            }
        });
        return NodeResult.untraced(MoneyValue.of(heads.total()));
    }
}
