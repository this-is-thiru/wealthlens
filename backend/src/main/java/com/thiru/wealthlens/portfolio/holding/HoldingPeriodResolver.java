package com.thiru.wealthlens.portfolio.holding;

import com.thiru.wealthlens.portfolio.dto.enums.CapitalGainsType;
import com.thiru.wealthlens.portfolio.entity.HoldingPeriodPolicyEntity;
import com.thiru.wealthlens.portfolio.repository.HoldingPeriodPolicyRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Decides whether a disposal is short-term, long-term, or not a capital gain.
 *
 * <p>Replaces {@code holdingPeriodDays > 365}, which was applied to every asset type in four
 * separate places — and the four did not agree with each other: two compared {@code plusYears(1)}
 * and two counted 365 days, which differ across a leap year.
 *
 * <p>Resolution has two steps and the order matters. A <b>deemed</b> rule — "short-term however long
 * it was held" — is consulted before any duration is measured, because it overrides the measurement
 * rather than competing with it. A specified fund bought in 2023 and sold in 2026 matches the
 * deeming rule by its acquisition date <em>and</em> the ordinary framework by its transfer date;
 * ranking those by recency picks the framework and calls a short-term disposal long-term. Among
 * candidates of equal standing, a policy naming a sub-class beats one matching any, and a later
 * window beats an earlier one — the specificity-then-recency ranking the charge schedule resolver
 * uses, for the same reason.
 *
 * <p>Nothing matching is an answer, not a failure. A mutual fund whose category is not on file could
 * be any of three treatments, so it returns {@code UNCLASSIFIED} with the reason attached rather than
 * guessing — the argument {@code ChargeResolution} already makes for a charge that could not be
 * priced.
 *
 * <p>Policies are read once and cached, because they change between releases rather than between
 * trades. {@link #evictAll()} is called by the seeder; a policy written straight to MongoDB is
 * invisible until something evicts, which is the same operational constraint rate cards carry.
 */
@Component
@RequiredArgsConstructor
public class HoldingPeriodResolver {

    private final HoldingPeriodPolicyRepository repository;
    private final AtomicReference<List<HoldingPeriodPolicy>> cache = new AtomicReference<>();

    public HoldingPeriodResolution classify(HoldingPeriodQuery query) {
        if (query.acquisitionDate() == null || query.transferDate() == null) {
            return HoldingPeriodResolution.unclassified("acquisition and transfer dates are both required");
        }

        Optional<HoldingPeriodPolicy> match = policies().stream()
                .filter(policy -> policy.appliesTo(query))
                .max(Comparator.comparingInt(HoldingPeriodResolver::overrides)
                        .thenComparingInt(HoldingPeriodPolicy::specificity)
                        .thenComparing(HoldingPeriodPolicy::effectiveFrom));

        if (match.isEmpty()) {
            return HoldingPeriodResolution.unclassified(
                    "no holding-period policy covers " + query.assetType()
                            + (query.subClass() == null
                            ? " with no sub-class on file — the instrument's category decides the rule and is unknown"
                            : " / " + query.subClass()));
        }

        HoldingPeriodPolicy policy = match.get();
        return new HoldingPeriodResolution(outcomeOf(policy, query), policy.policyCode(), policy.note());
    }

    /** Called when policies are seeded; a warm resolver would otherwise keep the old set. */
    public void evictAll() {
        cache.set(null);
    }

    private List<HoldingPeriodPolicy> policies() {
        List<HoldingPeriodPolicy> cached = cache.get();
        if (cached != null) {
            return cached;
        }
        List<HoldingPeriodPolicy> loaded = repository.findAll().stream()
                .map(HoldingPeriodResolver::toPolicy)
                .toList();
        cache.set(loaded);
        return loaded;
    }

    private static HoldingPeriodPolicy toPolicy(HoldingPeriodPolicyEntity entity) {
        return new HoldingPeriodPolicy(entity.getPolicyCode(), entity.getAssetType(), entity.getSubClass(),
                entity.getKeyedOn(), entity.getEffectiveFrom(), entity.getEffectiveTo(),
                entity.getOutcome(), entity.getMonths(), entity.getNote());
    }

    /**
     * A deemed rule outranks a duration rule, whatever their dates. They are not competing statements
     * of the same kind, and ranking them by recency produced exactly one wrong answer that a test
     * caught: a specified fund held five years classified as long-term.
     */
    private static int overrides(HoldingPeriodPolicy policy) {
        return policy.outcome() == HoldingPeriodOutcome.LONG_TERM_AFTER_MONTHS ? 0 : 1;
    }

    private static CapitalGainsType outcomeOf(HoldingPeriodPolicy policy, HoldingPeriodQuery query) {
        return switch (policy.outcome()) {
            case NOT_CAPITAL_GAINS -> CapitalGainsType.NOT_CAPITAL_GAINS;
            case ALWAYS_SHORT_TERM -> CapitalGainsType.SHORT_TERM;
            case LONG_TERM_AFTER_MONTHS -> heldBeyond(policy.months(), query)
                    ? CapitalGainsType.LONG_TERM
                    : CapitalGainsType.SHORT_TERM;
        };
    }

    /**
     * Strictly beyond, not "at least". A holding sold on its own twelve-month anniversary is still
     * short-term — the statute says "more than", and the difference is exactly the trades sitting on
     * the boundary.
     */
    private static boolean heldBeyond(Integer months, HoldingPeriodQuery query) {
        return query.acquisitionDate().plusMonths(months).isBefore(query.transferDate());
    }
}
