package com.thiru.wealthlens.taxengine.node;

import com.thiru.wealthlens.taxengine.input.LifePolicyReceipt;
import com.thiru.wealthlens.taxengine.pipeline.NodeContext;
import com.thiru.wealthlens.taxengine.rules.definition.PolicyPremiumLimits;
import com.thiru.wealthlens.taxengine.rules.definition.PremiumShareBands;
import com.thiru.wealthlens.taxengine.rules.definition.RuleDefinition;
import com.thiru.wealthlens.taxengine.rules.definition.RuleDefinitions;
import com.thiru.wealthlens.taxengine.rules.generated.RuleIds;
import com.thiru.wealthlens.taxengine.rules.id.BaseId;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.TemporallyApportionedCap;
import com.thiru.wealthlens.taxengine.rules.temporal.RuleVersion;
import com.thiru.wealthlens.taxengine.trace.StepKind;
import java.util.Optional;

/**
 * A life policy's sums under other sources (S2 Schedule II Sl. 2, s.92(2)(l), S4 r.59; 1961 s.10(10D), s.56(2)(xiii),
 * Rules 1962 r.11UACA; D-205): excluded where received on a death, where the policy predates every band, or where its
 * highest annual premium is within both its band's share of the sum assured and, for a capped policy, the premium
 * limit; otherwise the sum beyond the premiums not deducted or already counted, never below nil.
 *
 * <p>Apart from {@link OtherSourcesNode} only so that class stays readable; it writes to the node's trace and reads
 * the rules the node declares.
 */
final class LifePolicySums {

    private static final BaseId LIFE_POLICY = new BaseId("LIFE_POLICY");

    private LifePolicySums() {
    }

    /** What one receipt adds to the head: nil where excluded, else the r.59 / r.11UACA sum. */
    static Money income(NodeContext ctx, OtherSourcesNode.Lines lines, int position, LifePolicyReceipt receipt) {
        String where = "life policy " + position;
        Optional<String> excluded = excludedBecause(ctx, where, receipt);
        if (excluded.isPresent()) {
            lines.add(RuleIds.OS_LIFE_POLICY_RATIO, StepKind.EXEMPTION, receipt.received(), receipt.received(),
                    where + ": excluded, " + excluded.get());
            return Money.ZERO;
        }
        if (receipt.unitLinked()) {
            throw new IllegalArgumentException(where + ": a unit linked policy's sums that are not excluded are capital"
                    + " gains (S2 s.67(5); Income-tax Act, 1961 s.45(1B)), which are W-088's");
        }
        Money exceeding = receipt.received().minus(receipt.premiumsNotDeductedOrUsed());
        Money chargeable = exceeding.isLessThan(Money.ZERO) ? Money.ZERO : exceeding;
        Money income = lines.applied(RuleIds.OS_LIFE_POLICY, LIFE_POLICY, chargeable);
        lines.add(RuleIds.OS_LIFE_POLICY, StepKind.INCOME, receipt.received(), income, where + ": not excluded; the sum"
                + " exceeding " + receipt.premiumsNotDeductedOrUsed() + " of premium not deducted or already counted");
        return income;
    }

    /**
     * Why the sums are excluded, if they are. A capped policy held with others of its class is refused: which of them
     * the limit spares is the Board's guidelines' question, {@code W-079g2b}'s.
     */
    private static Optional<String> excludedBecause(NodeContext ctx, String where, LifePolicyReceipt receipt) {
        if (receipt.onDeath()) {
            return Optional.of("received on a death");
        }
        Optional<PremiumShareBands.Band> band = definition(ctx, RuleIds.OS_LIFE_POLICY_RATIO, PremiumShareBands.class)
                .bandHolding(receipt.issuedOn(), receipt.coversDisabilityOrSpecifiedDisease());
        if (band.isEmpty()) {
            return Optional.of("issued on " + receipt.issuedOn() + ", before any condition applies");
        }
        Optional<Money> limit = definition(ctx, RuleIds.OS_LIFE_POLICY_PREMIUM, PolicyPremiumLimits.class)
                .limitFor(receipt.issuedOn(), receipt.unitLinked(), receipt.issuedByIfscOffice());
        if (limit.isPresent() && receipt.heldWithOthersOfItsClass()) {
            throw new IllegalArgumentException(where + ": a capped policy held with others of its class is excluded"
                    + " only if chosen within the aggregate limit under the Board's guidelines, which is W-079g2b's");
        }
        Money shareLimit = band.get().share().of(receipt.actualCapitalSumAssured());
        boolean withinShare = !receipt.highestAnnualPremium().isGreaterThan(shareLimit);
        boolean withinLimit = limit.map(cap -> !receipt.highestAnnualPremium().isGreaterThan(cap)).orElse(true);
        return withinShare && withinLimit
                ? Optional.of("premium " + receipt.highestAnnualPremium() + " within " + shareLimit
                        + limit.map(cap -> " and " + cap).orElse(""))
                : Optional.empty();
    }

    private static <T extends RuleDefinition> T definition(NodeContext ctx, RuleId row, Class<T> type) {
        RuleVersion version = TemporallyApportionedCap.governingVersion(ctx.rule(row), ctx.inputs().temporal());
        return RuleDefinitions.require(version.definition(), type, row);
    }
}
