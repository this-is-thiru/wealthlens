package com.thiru.wealthlens.taxengine.election;

import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.pipeline.NodeContext;
import com.thiru.wealthlens.taxengine.rules.condition.Condition;
import com.thiru.wealthlens.taxengine.rules.condition.ConditionContext;
import com.thiru.wealthlens.taxengine.rules.condition.PersonType;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.pack.ConditionSet;
import com.thiru.wealthlens.taxengine.rules.pack.RulePack;
import com.thiru.wealthlens.taxengine.rules.regime.BindingSpec;
import com.thiru.wealthlens.taxengine.rules.regime.ConditionSetId;
import com.thiru.wealthlens.taxengine.rules.regime.ElectionState;
import com.thiru.wealthlens.taxengine.rules.regime.RegimeSpec;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * The regimes the year's pack offers an assessee ({@code DR 17} L3's {@code eligibleRegimes}; D-222).
 *
 * <p>A regime is offered where it applies to the person type, is in force in the year, is open to new entrants — or
 * is closed to them and the assessee is already in it (a grandfathered regime) — and every eligibility set is met.
 * The default regime comes first, so a tie in price goes to it: staying needs no action ({@code REG-DEFAULT-001}).
 *
 * <p>A regime whose binding applies to this assessee and binds them (s.202(4)(a); 1961 s.115BAC(6)(i)) is refused:
 * what it offers then turns on the election history, which is W-082e. Its binding applies only with income under the
 * business head, which nothing yet computes, so a computable person always has the free annual choice
 * ({@code REG-OPTOUT-YEARLY-001}).
 */
public final class RegimeOffers {

    private RegimeOffers() {}

    /**
     * Every regime in the pack, offered or barred, the default first.
     *
     * @throws IllegalArgumentException if a regime's binding applies to this assessee, or a regime names an
     *     eligibility set the pack does not hold
     */
    public static List<RegimeOffer> of(RulePack pack, EngineInputs inputs) {
        if (pack == null || inputs == null) {
            throw new IllegalArgumentException("Regime offers need the pack and the inputs");
        }
        return pack.regimes().values().stream()
                .sorted(Comparator.comparing((RegimeSpec regime) -> !regime.isDefault())
                        .thenComparing(regime -> regime.code().value()))
                .map(regime -> offer(regime, pack, inputs))
                .toList();
    }

    private static RegimeOffer offer(RegimeSpec regime, RulePack pack, EngineInputs inputs) {
        PersonType personType = inputs.assessee().type();
        TaxYearId year = inputs.taxYear();
        if (!regime.appliesTo().contains(personType)) {
            return new RegimeOffer.Barred(regime.code(), regime.code() + " applies to " + regime.appliesTo()
                    + ", not to " + personType);
        }
        if (!regime.lifecycle().applicable(year)) {
            return new RegimeOffer.Barred(regime.code(), regime.code() + " is not in force in " + year.label());
        }
        ConditionContext ctx = NodeContext.forRun(pack, regime, inputs).conditions();
        BindingSpec binding = regime.election().binding();
        if (binding.appliesTo(ctx) && binds(binding)) {
            throw new IllegalArgumentException(regime.code() + "'s election binds this assessee (" + regime.election()
                    .provenance().sourceDescription() + "); what it offers then turns on the election history — W-082e");
        }
        if (!regime.lifecycle().openToNewEntrants(year)
                && inputs.elections().stateOf(regime.code()) != ElectionState.IN_EFFECT) {
            return new RegimeOffer.Barred(regime.code(), regime.code() + " is closed to new entrants in "
                    + year.label() + ", and the assessee had not elected it");
        }
        for (ConditionSetId id : regime.eligibility()) {
            ConditionSet set = Optional.ofNullable(pack.conditionSets().get(id)).orElseThrow(() ->
                    new IllegalArgumentException(regime.code() + " names the eligibility set " + id
                            + ", which the pack does not hold"));
            Optional<Condition> unmet = set.firstUnmet(ctx);
            if (unmet.isPresent()) {
                return new RegimeOffer.Barred(regime.code(), regime.code() + " requires " + set.displayName()
                        + " (" + set.basis() + "): " + unmet.get().describe() + " does not hold");
            }
        }
        return new RegimeOffer.Available(regime);
    }

    /** Whether a binding commits the assessee to anything — a later year, a limit on withdrawing, the first return. */
    private static boolean binds(BindingSpec binding) {
        return binding.bindsSubsequentYears() || !binding.isUnlimitedWithdrawals() || binding.firstReturnOnly();
    }
}
