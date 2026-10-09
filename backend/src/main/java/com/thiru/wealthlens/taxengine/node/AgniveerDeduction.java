package com.thiru.wealthlens.taxengine.node;

import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeContext;
import com.thiru.wealthlens.taxengine.pipeline.RegimeVerdict;
import com.thiru.wealthlens.taxengine.rules.cap.CapContext;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.generated.RuleIds;
import com.thiru.wealthlens.taxengine.rules.id.BaseId;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.TemporallyApportionedCap;
import com.thiru.wealthlens.taxengine.rules.temporal.RuleVersion;
import com.thiru.wealthlens.taxengine.trace.StepKind;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * s.125 — the Agniveer Corpus Fund — for {@link ChapterEightNode} (S2; D-140): the Agniveer's own subscription in the
 * old regime, the Central Government's contribution in every regime, each the whole amount. The Government's
 * contribution is salary too (s.16(l)), so in gross salary and here it nets to nil.
 */
final class AgniveerDeduction {

    static final Set<RuleId> RULES = Set.of(RuleIds.DED_AGNIVEER_OWN, RuleIds.DED_AGNIVEER_GOVERNMENT);

    private static final BaseId AMOUNT_PAID = new BaseId("AMOUNT_PAID");

    private AgniveerDeduction() {
    }

    /** The lines, numbered from {@code firstSequence}; none where no Agniveer account is entered. */
    static List<TraceLine> lines(NodeContext ctx, int firstSequence) {
        List<TraceLine> lines = new ArrayList<>();
        ctx.inputs().entered().agniveerCorpusFund().ifPresent(fund -> {
            add(ctx, RuleIds.DED_AGNIVEER_OWN, fund.ownSubscription(), firstSequence, lines);
            add(ctx, RuleIds.DED_AGNIVEER_GOVERNMENT, fund.governmentContribution(), firstSequence, lines);
        });
        return lines;
    }

    private static void add(NodeContext ctx, RuleId row, Money paid, int firstSequence, List<TraceLine> lines) {
        if (!paid.isGreaterThan(Money.ZERO)) {
            return;
        }
        RuleVersion version = TemporallyApportionedCap.governingVersion(ctx.rule(row), ctx.inputs().temporal());
        RegimeVerdict verdict = ctx.inRegime(row, version);
        boolean applies = verdict.applies();
        Money allowed = applies
                ? TemporallyApportionedCap.apply(ctx.rule(row), ctx.inputs().temporal(), CapContext.of(AMOUNT_PAID, paid))
                        .value()
                : Money.ZERO;
        lines.add(TraceLine.of(firstSequence + lines.size(), NodeIds.CHAPTERVIII, row, StepKind.DEDUCTION,
                        MoneyValue.of(paid), MoneyValue.of(allowed), version.provenance())
                .withLimitedBy(applies ? "the whole amount" : verdict.whyNot()));
    }

    /** Whether an Agniveer account is entered at all. */
    static boolean entered(NodeContext ctx) {
        return ctx.inputs().entered().agniveerCorpusFund().isPresent();
    }
}
