package com.thiru.wealthlens.taxengine.election;

import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.input.HouseOccupancy;
import com.thiru.wealthlens.taxengine.input.HouseProperty;
import com.thiru.wealthlens.taxengine.rules.definition.OwnResidenceHouses;
import com.thiru.wealthlens.taxengine.rules.definition.RuleDefinitions;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.generated.RuleIds;
import com.thiru.wealthlens.taxengine.rules.id.NodeId;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.pack.RulePack;
import com.thiru.wealthlens.taxengine.rules.pack.TemporallyApportionedCap;
import com.thiru.wealthlens.taxengine.rules.regime.RegimeSpec;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

/**
 * Which houses the assessee specifies as having a nil annual value ({@code DR 13} R-6; S2 s.21(6), (7)(a); 1961
 * s.23(2), (4); D-230).
 *
 * <p>Every house not let, from which the owner derives no other benefit, qualifies — entered {@code OWN_RESIDENCE}
 * (specified) or {@code NOT_LET} (left out) — and at most {@code HP-OWN-RESIDENCE}'s houses may be specified. Leaving one
 * out can cost less: it is valued at its letting value, less the standard deduction, but its interest escapes the
 * own-residence cap. The choices are every set of at most that many qualifying houses, the specification as entered
 * first, so a tie keeps it, then larger sets before smaller in entered order. A choice is the houses' positions in
 * {@code EnteredFacts.houseProperties}, from 0. Nothing is stated here: a plain run computes the houses as entered.
 */
public final class HouseElection implements Election<List<Integer>> {

    @Override
    public RuleId basis(List<Integer> choice, RulePack pack) {
        return RuleIds.HP_OWN_RESIDENCE;
    }

    @Override
    public NodeId node() {
        return NodeIds.HOUSEPROPERTY_INCOME;
    }

    @Override
    public List<List<Integer>> candidates(EngineInputs inputs, RulePack pack) {
        int limit = RuleDefinitions.require(TemporallyApportionedCap.governingVersion(
                pack.rule(RuleIds.HP_OWN_RESIDENCE), inputs.temporal()).definition(), OwnResidenceHouses.class,
                RuleIds.HP_OWN_RESIDENCE).houses();
        List<HouseProperty> houses = inputs.entered().houseProperties();
        List<Integer> qualifying = IntStream.range(0, houses.size())
                .filter(i -> houses.get(i).occupancy() != HouseOccupancy.LET).boxed().toList();
        List<Integer> entered = IntStream.range(0, houses.size())
                .filter(i -> houses.get(i).occupancy() == HouseOccupancy.OWN_RESIDENCE).boxed().toList();
        List<List<Integer>> sets = new ArrayList<>();
        if (entered.size() <= limit) {
            sets.add(entered);
        }
        for (int size = Math.min(limit, qualifying.size()); size >= 0; size--) {
            for (List<Integer> set : subsets(qualifying, size)) {
                if (!set.equals(entered)) {
                    sets.add(set);
                }
            }
        }
        return List.copyOf(sets);
    }

    @Override
    public Optional<List<Integer>> stated(EngineInputs inputs) {
        return Optional.empty();
    }

    @Override
    public EngineInputs choosing(List<Integer> choice, EngineInputs inputs) {
        List<HouseProperty> houses = inputs.entered().houseProperties();
        List<HouseProperty> relabelled = new ArrayList<>(houses.size());
        for (int i = 0; i < houses.size(); i++) {
            HouseProperty house = houses.get(i);
            relabelled.add(house.occupancy() == HouseOccupancy.LET ? house
                    : withOccupancy(house, choice.contains(i) ? HouseOccupancy.OWN_RESIDENCE : HouseOccupancy.NOT_LET));
        }
        return new EngineInputs(inputs.taxYear(), inputs.regime(), inputs.temporal(),
                inputs.entered().withHouseProperties(relabelled), inputs.assessee(), inputs.family(), inputs.ledgers(),
                inputs.elections(), inputs.capitalGains(), inputs.filing());
    }

    @Override
    public RegimeSpec regimeFor(List<Integer> choice, RegimeSpec computing, RulePack pack) {
        return computing;
    }

    @Override
    public String describe(List<Integer> choice, RulePack pack) {
        return "own residences: properties " + choice.stream().map(i -> i + 1).toList()
                + " (a loss carried forward is priced in this year only)";
    }

    /** Every set of {@code size} of {@code from}, in their order. */
    private static List<List<Integer>> subsets(List<Integer> from, int size) {
        List<List<Integer>> sets = new ArrayList<>();
        collect(from, size, 0, new ArrayList<>(), sets);
        return sets;
    }

    private static void collect(List<Integer> from, int size, int start, List<Integer> chosen, List<List<Integer>> sets) {
        if (chosen.size() == size) {
            sets.add(List.copyOf(chosen));
            return;
        }
        for (int i = start; i < from.size(); i++) {
            chosen.add(from.get(i));
            collect(from, size, i + 1, chosen, sets);
            chosen.removeLast();
        }
    }

    private static HouseProperty withOccupancy(HouseProperty house, HouseOccupancy occupancy) {
        return new HouseProperty(occupancy, house.expectedLettingValue(), house.rentReceivable(), house.unrealisedRent(),
                house.vacantPartOfYear(), house.localTaxesPaid(), house.ownershipShare(), house.arrearsReceived(),
                house.acquiredOrConstructedOn(), house.loans());
    }
}
