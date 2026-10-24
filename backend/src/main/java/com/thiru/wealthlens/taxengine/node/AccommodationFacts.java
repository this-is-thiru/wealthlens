package com.thiru.wealthlens.taxengine.node;

import com.thiru.wealthlens.taxengine.input.EmployerAccommodation;
import com.thiru.wealthlens.taxengine.pipeline.NodeContext;
import com.thiru.wealthlens.taxengine.rules.cap.CapShape;
import com.thiru.wealthlens.taxengine.rules.condition.ItemFacts;
import com.thiru.wealthlens.taxengine.rules.generated.RuleSetIds;
import com.thiru.wealthlens.taxengine.rules.id.BaseId;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.pack.RuleRow;
import com.thiru.wealthlens.taxengine.rules.pack.TemporallyApportionedCap;
import com.thiru.wealthlens.taxengine.rules.temporal.RuleVersion;
import java.util.Set;

/**
 * Which facts an accommodation carries beyond its provider, period and rent, as its Table I rows read them (W-158b,
 * D-358): a fact the provider's row reads is required, and one no row reads is refused, since it would be ignored.
 *
 * <p>The population and the employer's cost are what the provider's {@code RS-PERQ-ACCOM} cap counts and reads; the
 * first year's amount is read where a {@code RS-PERQ-ACCOM-CONTINUED} row reaches accommodation that is continued;
 * furniture where a {@code RS-PERQ-ACCOM-FURNITURE} row reaches it. Which provider reads which is the rows' to say,
 * so no provider is named here — the {@code EntryFacts} pattern (W-155c2) for accommodation.
 */
final class AccommodationFacts {

    private AccommodationFacts() {
    }

    /**
     * Refuses an accommodation lacking a fact its row reads, or carrying one no row reads.
     *
     * @param position the accommodation's place among those entered, from one, as a refusal names it
     * @param facts the accommodation's facts as the rows' conditions read them
     */
    static void requireWhatTheRowsRead(NodeContext ctx, EmployerAccommodation accommodation, int position,
            ItemFacts facts) {
        String what = "Accommodation " + position + " (" + accommodation.provider() + ")";
        RuleRow row = ctx.rule(ctx.ruleIdOf(RuleSetIds.RS_PERQ_ACCOM, facts));
        RuleVersion version = TemporallyApportionedCap.governingVersion(row, ctx.inputs().temporal());
        Set<BaseId> amounts = version.cap().map(CapShape::amountsRead).orElse(Set.of());
        Set<BaseId> counts = version.cap().map(CapShape::countsRead).orElse(Set.of());
        require(what, row.id(), counts.contains(PerquisiteNode.POPULATION), accommodation.cityPopulation().isPresent(),
                "city's population");
        require(what, row.id(), amounts.contains(PerquisiteNode.EMPLOYER_COST), accommodation.employerCost().isPresent(),
                "employer's cost");
        boolean capped = ctx.optionalRuleOf(RuleSetIds.RS_PERQ_ACCOM_CONTINUED, facts).isPresent()
                && PerquisiteNode.isContinued(accommodation, ctx.inputs().taxYear());
        if (accommodation.firstYearValue().isPresent() && !capped) {
            throw new IllegalArgumentException(what + ": no row of " + RuleSetIds.RS_PERQ_ACCOM_CONTINUED
                    + " caps it in TY " + ctx.inputs().taxYear().label() + " as accommodation first provided in "
                    + accommodation.firstProvidedIn() + "; the first year's amount would be ignored, so it is refused");
        }
        if (accommodation.furniture().isPresent()
                && ctx.optionalRuleOf(RuleSetIds.RS_PERQ_ACCOM_FURNITURE, facts).isEmpty()) {
            throw new IllegalArgumentException(what + ": no row of " + RuleSetIds.RS_PERQ_ACCOM_FURNITURE
                    + " values its furniture in TY " + ctx.inputs().taxYear().label()
                    + "; it would be ignored, so it is refused");
        }
    }

    private static void require(String what, RuleId row, boolean read, boolean given, String fact) {
        if (read && !given) {
            throw new IllegalArgumentException(what + " is measured on the " + fact + " (" + row + "); enter it");
        }
        if (!read && given) {
            throw new IllegalArgumentException(what + " is not measured on the " + fact + " (" + row
                    + "); it would be ignored, so it is refused");
        }
    }
}
