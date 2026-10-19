package com.thiru.wealthlens.taxengine.node;

import com.thiru.wealthlens.taxengine.input.ClaimedPolicy;
import com.thiru.wealthlens.taxengine.input.LifePolicyReceipt;
import com.thiru.wealthlens.taxengine.pipeline.NodeContext;
import com.thiru.wealthlens.taxengine.rules.definition.PolicyPremiumLimits;
import com.thiru.wealthlens.taxengine.rules.definition.PremiumShareBands;
import com.thiru.wealthlens.taxengine.rules.definition.RuleDefinition;
import com.thiru.wealthlens.taxengine.rules.definition.RuleDefinitions;
import com.thiru.wealthlens.taxengine.rules.generated.RuleSetIds;
import com.thiru.wealthlens.taxengine.rules.id.BaseId;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.TemporallyApportionedCap;
import com.thiru.wealthlens.taxengine.rules.temporal.RuleVersion;
import com.thiru.wealthlens.taxengine.trace.StepKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A life policy's sums under other sources (S2 Schedule II Sl. 2, s.92(2)(l), S4 r.59; 1961 s.10(10D), s.56(2)(xiii),
 * Rules 1962 r.11UACA; D-205, D-207).
 *
 * <p>Each receipt is first tested on its own: excluded where received on a death, where the policy predates every
 * band, or where its premium is within its band's share of the sum assured and — for a capped policy — within the
 * premium limit. A capped policy that passes is excluded only if claimed, and only if its premiums with the class's
 * other claims and its old policies stay within the limit in every premium year (CBDT Circulars No. 2/2022 para 4,
 * No. 15/2023 para 4, continued by S2 s.536(2)(j)): a single claim that breaches is not excluded; several that breach
 * are refused, the choice of which to claim being the person's. What is not excluded is the sum beyond the premiums
 * not deducted or already counted, never below nil.
 *
 * <p>Apart from {@link OtherSourcesNode} only so that class stays readable; it writes to the node's trace and reads
 * the rules the node declares.
 */
final class LifePolicySums {

    private static final BaseId LIFE_POLICY = new BaseId("LIFE_POLICY");

    /**
     * A receipt's own test: excluded with a reason, charged with a reason, or capped and passing — then its fate is the
     * aggregate's.
     */
    private record Verdict(LifePolicyReceipt receipt, boolean excluded, String why, Optional<Money> capLimit) {

        boolean awaitsAggregate() {
            return capLimit.isPresent();
        }
    }

    private LifePolicySums() {
    }

    /** What the year's receipts add to the head. */
    static Money income(NodeContext ctx, OtherSourcesNode.Lines lines, List<LifePolicyReceipt> receipts,
            List<ClaimedPolicy> claimed) {
        PremiumShareBands bands = definition(ctx, ctx.ruleIdOf(RuleSetIds.RS_OS_LIFE_POLICY_RATIO), PremiumShareBands.class);
        PolicyPremiumLimits limits = definition(ctx, ctx.ruleIdOf(RuleSetIds.RS_OS_LIFE_POLICY_PREMIUM), PolicyPremiumLimits.class);
        requireCapped(claimed, limits);
        List<Verdict> verdicts = new ArrayList<>();
        for (LifePolicyReceipt receipt : receipts) {
            verdicts.add(ownTest(receipt, bands, limits));
        }
        for (boolean unitLinked : new boolean[] {true, false}) {
            settleClass(verdicts, claimed, unitLinked);
        }
        Money head = Money.ZERO;
        for (int position = 0; position < verdicts.size(); position++) {
            head = head.plus(lineFor(lines, "life policy " + position, verdicts.get(position)));
        }
        return head;
    }

    private static Verdict ownTest(LifePolicyReceipt receipt, PremiumShareBands bands, PolicyPremiumLimits limits) {
        if (receipt.onDeath()) {
            return new Verdict(receipt, true, "received on a death", Optional.empty());
        }
        Optional<PremiumShareBands.Band> band = bands.bandHolding(receipt.issuedOn(),
                receipt.coversDisabilityOrSpecifiedDisease());
        if (band.isEmpty()) {
            return new Verdict(receipt, true, "issued on " + receipt.issuedOn() + ", before any condition applies",
                    Optional.empty());
        }
        Money shareLimit = band.get().share().of(receipt.actualCapitalSumAssured());
        if (receipt.highestAnnualPremium().isGreaterThan(shareLimit)) {
            return new Verdict(receipt, false, "premium " + receipt.highestAnnualPremium() + " above " + shareLimit,
                    Optional.empty());
        }
        Optional<Money> limit = limits.limitFor(receipt.issuedOn(), receipt.unitLinked(), receipt.issuedByIfscOffice());
        if (limit.isEmpty()) {
            return new Verdict(receipt, true, "premium " + receipt.highestAnnualPremium() + " within " + shareLimit,
                    Optional.empty());
        }
        if (receipt.highestAnnualPremium().isGreaterThan(limit.get())) {
            return new Verdict(receipt, false, "premium " + receipt.highestAnnualPremium() + " above the limit of "
                    + limit.get(), Optional.empty());
        }
        if (!receipt.claimsExemption()) {
            return new Verdict(receipt, false, "a capped policy not claimed exempt", Optional.empty());
        }
        return new Verdict(receipt, true, "premium within " + shareLimit + " and, with the claimed and old policies of"
                + " its class, within " + limit.get(), limit);
    }

    /**
     * The aggregate for one class: the old policies and this year's claims that passed alone, year by year over their
     * premium years. A single claim that breaches is not excluded (Circulars para 4.2(ii) / 4.3(ii)); several are
     * refused, which of them to claim being the person's choice.
     */
    private static void settleClass(List<Verdict> verdicts, List<ClaimedPolicy> claimed, boolean unitLinked) {
        List<Integer> claims = new ArrayList<>();
        for (int i = 0; i < verdicts.size(); i++) {
            if (verdicts.get(i).awaitsAggregate() && verdicts.get(i).receipt().unitLinked() == unitLinked) {
                claims.add(i);
            }
        }
        if (claims.isEmpty()) {
            return;
        }
        Money limit = verdicts.get(claims.getFirst()).capLimit().orElseThrow();
        List<ClaimedPolicy> old = claimed.stream().filter(policy -> policy.unitLinked() == unitLinked).toList();
        Optional<String> breach = firstBreach(verdicts, claims, old, limit);
        if (breach.isEmpty()) {
            return;
        }
        if (claims.size() > 1) {
            throw new IllegalArgumentException("Life policies " + claims + " are all claimed exempt, but with the old"
                    + " policies their premiums " + breach.get() + "; only a set within the limit may be claimed"
                    + " (CBDT Circulars No. 2/2022, 15/2023, para 4) — choose which to claim");
        }
        Verdict alone = verdicts.get(claims.getFirst());
        verdicts.set(claims.getFirst(), new Verdict(alone.receipt(), false, "with the old policies of its class the"
                + " premiums " + breach.get(), Optional.empty()));
    }

    private static Optional<String> firstBreach(List<Verdict> verdicts, List<Integer> claims, List<ClaimedPolicy> old,
            Money limit) {
        int from = Integer.MAX_VALUE;
        int to = Integer.MIN_VALUE;
        for (int i : claims) {
            from = Math.min(from, verdicts.get(i).receipt().premiumFrom().startYear());
            to = Math.max(to, verdicts.get(i).receipt().premiumTo().startYear());
        }
        for (int year = from; year <= to; year++) {
            Money total = Money.ZERO;
            for (int i : claims) {
                LifePolicyReceipt receipt = verdicts.get(i).receipt();
                total = receipt.payableIn(year) ? total.plus(receipt.highestAnnualPremium()) : total;
            }
            for (ClaimedPolicy policy : old) {
                total = policy.payableIn(year) ? total.plus(policy.annualPremium()) : total;
            }
            if (total.isGreaterThan(limit)) {
                return Optional.of("reach " + total + " in the tax year starting " + year + ", above " + limit);
            }
        }
        return Optional.empty();
    }

    private static Money lineFor(OtherSourcesNode.Lines lines, String where, Verdict verdict) {
        LifePolicyReceipt receipt = verdict.receipt();
        if (verdict.excluded()) {
            lines.add(lines.row(RuleSetIds.RS_OS_LIFE_POLICY_RATIO), StepKind.EXEMPTION, receipt.received(), receipt.received(),
                    where + ": excluded, " + verdict.why());
            return Money.ZERO;
        }
        if (receipt.unitLinked()) {
            throw new IllegalArgumentException(where + ": a unit linked policy's sums that are not excluded (" + verdict.why()
                    + ") are capital gains (S2 s.67(5); Income-tax Act, 1961 s.45(1B)), which are W-088's");
        }
        Money exceeding = receipt.received().minus(receipt.premiumsNotDeductedOrUsed());
        Money chargeable = exceeding.isLessThan(Money.ZERO) ? Money.ZERO : exceeding;
        Money income = lines.applied(lines.row(RuleSetIds.RS_OS_LIFE_POLICY), LIFE_POLICY, chargeable);
        lines.add(lines.row(RuleSetIds.RS_OS_LIFE_POLICY), StepKind.INCOME, receipt.received(), income, where + ": not excluded, "
                + verdict.why() + "; the sum exceeding " + receipt.premiumsNotDeductedOrUsed()
                + " of premium not deducted or already counted");
        return income;
    }

    /** An old policy counts only if it is of a capped class; any other entered as one would distort the aggregate. */
    private static void requireCapped(List<ClaimedPolicy> claimed, PolicyPremiumLimits limits) {
        for (ClaimedPolicy policy : claimed) {
            if (limits.limitFor(policy.issuedOn(), policy.unitLinked(), false).isEmpty()) {
                throw new IllegalArgumentException("A policy issued on " + policy.issuedOn() + " is not of a capped"
                        + " class, so its earlier claim does not count toward the aggregate limit; do not enter it");
            }
        }
    }

    private static <T extends RuleDefinition> T definition(NodeContext ctx, RuleId row, Class<T> type) {
        RuleVersion version = TemporallyApportionedCap.governingVersion(ctx.rule(row), ctx.inputs().temporal());
        return RuleDefinitions.require(version.definition(), type, row);
    }
}
