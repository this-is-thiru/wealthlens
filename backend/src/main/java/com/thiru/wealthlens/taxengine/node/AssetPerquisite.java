package com.thiru.wealthlens.taxengine.node;

import com.thiru.wealthlens.taxengine.input.EmployerAssetTransfer;
import com.thiru.wealthlens.taxengine.input.EmployerAssetUse;
import com.thiru.wealthlens.taxengine.input.TransferredAssetKind;
import com.thiru.wealthlens.taxengine.pipeline.NodeContext;
import com.thiru.wealthlens.taxengine.rules.cap.CapContext;
import com.thiru.wealthlens.taxengine.rules.cap.PercentOfBase;
import com.thiru.wealthlens.taxengine.rules.condition.ItemFacts;
import com.thiru.wealthlens.taxengine.rules.definition.ExcludedAssets;
import com.thiru.wealthlens.taxengine.rules.definition.RuleDefinitions;
import com.thiru.wealthlens.taxengine.rules.generated.RuleSetIds;
import com.thiru.wealthlens.taxengine.rules.id.BaseId;
import com.thiru.wealthlens.taxengine.rules.id.ItemFactId;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.id.RuleSetId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.money.Rate;
import com.thiru.wealthlens.taxengine.rules.pack.TemporallyApportionedCap;
import com.thiru.wealthlens.taxengine.rules.temporal.RuleVersion;
import com.thiru.wealthlens.taxengine.rules.temporal.TemporalContext;
import com.thiru.wealthlens.taxengine.trace.StepKind;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The use and the transfer of an employer's movable assets, for {@link PerquisiteNode} (S4 r.15(5) Table IV Sl. 7–8;
 * Rules 1962 r.3(7)(vii)–(viii); D-119).
 *
 * <p>Use: ten per cent a year of the cost for the months used, or the rent the employer paid, less the recovery; the
 * kinds the year excludes are nil. Transfer: the cost less wear and tear for the completed years — on the reducing
 * balance for computers and electronic items and for motor cars, as a straight line for the rest — less the price.
 */
final class AssetPerquisite {

    static final Set<RuleSetId> RULES = Set.of(RuleSetIds.RS_PERQ_ASSET_USE, RuleSetIds.RS_PERQ_ASSET_USE_EXCLUDED,
            RuleSetIds.RS_PERQ_ASSET_TRANSFER);

    private static final BaseId ASSET_COST = new BaseId("ASSET_COST");

    /** What a transferred asset's wear-and-tear row is chosen by (D-253). */
    private static final ItemFactId ASSET_KIND = new ItemFactId("asset.kind");

    private AssetPerquisite() {
    }

    /** One line per asset lent, then one per asset transferred, numbered from {@code firstSequence}. */
    static List<TraceLine> lines(NodeContext ctx, int firstSequence) {
        List<TraceLine> lines = new ArrayList<>();
        for (EmployerAssetUse use : ctx.inputs().entered().assetsLent()) {
            PerquisiteNode.requireEnteredEmployment(ctx, use.employment(), "asset lent");
            lines.add(used(ctx, use, firstSequence + lines.size()));
        }
        for (EmployerAssetTransfer transfer : ctx.inputs().entered().assetsTransferred()) {
            PerquisiteNode.requireEnteredEmployment(ctx, transfer.employment(), "asset transferred");
            lines.add(transferred(ctx, transfer, firstSequence + lines.size()));
        }
        return lines;
    }

    private static TraceLine used(NodeContext ctx, EmployerAssetUse use, int sequence) {
        TemporalContext t = ctx.inputs().temporal();
        if (excludedAssets(ctx, t).excludes(use.kind())) {
            return ctx.traceLine(sequence, ctx.ruleIdOf(RuleSetIds.RS_PERQ_ASSET_USE_EXCLUDED), StepKind.INCOME,
                            use.cost().or(use::rentPaid).orElseThrow(), Money.ZERO)
                    .withLimitedBy("not a perquisite: the use of a " + use.kind() + " is excluded this year");
        }
        Money charged = use.cost()
                .map(cost -> TemporallyApportionedCap.apply(ctx.ruleOf(RuleSetIds.RS_PERQ_ASSET_USE), t,
                        CapContext.of(ASSET_COST, cost.apportioned(use.months(), 12))).value())
                .orElseGet(() -> use.rentPaid().orElseThrow());
        Money value = charged.minus(use.recovered()).floorAtZero();
        return ctx.traceLine(sequence, ctx.ruleIdOf(RuleSetIds.RS_PERQ_ASSET_USE), StepKind.INCOME,
                        use.cost().or(use::rentPaid).orElseThrow(), value)
                .withLimitedBy((use.cost().isPresent() ? "ten per cent a year of the cost for " + use.months()
                        + " months"
                        : "the rent paid") + ", less the recovery " + use.recovered());
    }

    private static TraceLine transferred(NodeContext ctx, EmployerAssetTransfer transfer, int sequence) {
        RuleId ruleId = ctx.ruleIdOf(RuleSetIds.RS_PERQ_ASSET_TRANSFER, ItemFacts.of(ASSET_KIND, transfer.kind().name()));
        Rate rate = rateOf(ctx, ruleId);
        Money written = transfer.kind() == TransferredAssetKind.OTHER
                ? transfer.cost().minus(rate.ofShare(transfer.cost(), transfer.completedYears(), 1)).floorAtZero()
                : rate.leftAfter(transfer.cost(), transfer.completedYears());
        Money value = written.minus(transfer.pricePaid()).floorAtZero();
        return ctx.traceLine(sequence, ruleId, StepKind.INCOME, transfer.cost(), value)
                .withLimitedBy("written down to " + written + " after "
                + transfer.completedYears() + " completed years at " + rate
                + (transfer.kind() == TransferredAssetKind.OTHER ? " of the cost" : " on the reducing balance")
                + ", less the price " + transfer.pricePaid());
    }

    /** A wear-and-tear row's rate — the row states it as a percentage of the cost. */
    private static Rate rateOf(NodeContext ctx, RuleId ruleId) {
        RuleVersion version = TemporallyApportionedCap.governingVersion(ctx.rule(ruleId), ctx.inputs().temporal());
        if (version.cap().isPresent() && version.cap().get() instanceof PercentOfBase percent) {
            return percent.percent();
        }
        throw new IllegalStateException(ruleId + " must state its wear and tear as a percentage of the cost");
    }

    private static ExcludedAssets excludedAssets(NodeContext ctx, TemporalContext t) {
        RuleId ruleId = ctx.ruleIdOf(RuleSetIds.RS_PERQ_ASSET_USE_EXCLUDED);
        return RuleDefinitions.require(ctx.rule(ruleId).versionAt(t).definition(), ExcludedAssets.class, ruleId);
    }

}
