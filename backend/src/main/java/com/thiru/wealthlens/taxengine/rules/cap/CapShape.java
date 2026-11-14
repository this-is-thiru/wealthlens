package com.thiru.wealthlens.taxengine.rules.cap;

import com.thiru.wealthlens.taxengine.rules.id.BaseId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.money.Rate;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A statutory limit, expressed as one of a closed set of shapes rather than as a formula.
 *
 * <p>Every rupee limit in the rulebook — the standard deduction, the section 80C ceiling, the house
 * rent allowance exemption, the slab ladder itself — is one of six shapes over different numbers.
 * Holding them as shapes rather than as expressions is what lets a rule pack be data: a new
 * deduction is a row, and a genuinely new <em>kind</em> of limit is a new class in this package,
 * never an edit to an existing one.
 *
 * <p>The family is sealed so the compiler can prove that set is closed. A switch over a cap shape
 * that misses a case does not compile, which is the property that makes adding the sixth shape safe.
 *
 * <p><b>The clause is now complete.</b> All six shapes of {@code 05-domain-model.md} M1.3 exist, so
 * the family is closed as designed: a switch over a cap shape that misses a case does not compile.
 * A seventh shape is a new class and a new permit — never an edit to an existing shape.
 */
public sealed interface CapShape
        permits Fixed, PercentOfBase, SlabLadder, LeastOf, AggregateWithCeiling, PerUnitPerPeriod, AmountInExcess,
                PercentOfBaseByLocation, PercentOfBaseByPopulation {

    /** Applies the cap to the amounts this rule declares it reads. */
    CapResult apply(CapContext ctx);

    /** How this cap names itself in a trace line when nothing narrower bound the result. */
    String shapeName();

    /**
     * The bases whose amounts this cap reads from {@link CapContext#bases} — so a node can tell which entered amounts
     * a row reads, and refuse one it would ignore (W-155c2, D-341). Counts, locations and contributions are not
     * amounts and are not named. A composition reads what its parts read.
     */
    default Set<BaseId> amountsRead() {
        return switch (this) {
            case Fixed _, PerUnitPerPeriod _ -> Set.of();
            case PercentOfBase percent -> Set.of(percent.base());
            case SlabLadder ladder -> Set.of(ladder.base());
            case PercentOfBaseByLocation byLocation -> Set.of(byLocation.base());
            case PercentOfBaseByPopulation byPopulation -> Set.of(byPopulation.base());
            case AmountInExcess excess -> Stream.concat(Stream.of(excess.amount()),
                    excess.threshold().amountsRead().stream()).collect(Collectors.toUnmodifiableSet());
            case LeastOf least -> least.limbs().stream().flatMap(limb -> limb.shape().amountsRead().stream())
                    .collect(Collectors.toUnmodifiableSet());
            case AggregateWithCeiling aggregate -> aggregate.subCaps().values().stream()
                    .flatMap(subCap -> subCap.amountsRead().stream()).collect(Collectors.toUnmodifiableSet());
        };
    }

    /**
     * The ids whose counts this cap reads from {@link CapContext#counts} — so a node can tell which quantities a row
     * needs entered, such as children and months, without restating the rows that need them (W-156e, D-350). A
     * composition reads what its parts read.
     */
    /**
     * The amount whose rise raises this shape, where one does at {@code ctx} (W-326a, D-696): a percentage's base at a
     * rate above nil, an excess's amount where its threshold does not read it too. Empty for a shape no single amount
     * raises.
     */
    default Optional<BaseId> raisedBy(CapContext ctx) {
        return switch (this) {
            case PercentOfBase percent -> Optional.of(percent.base()).filter(base -> percent.percent().compareTo(Rate.ZERO) > 0);
            case PercentOfBaseByLocation byLocation -> Optional.of(byLocation.base())
                    .filter(base -> byLocation.percentAt(ctx).compareTo(Rate.ZERO) > 0);
            case AmountInExcess excess -> Optional.of(excess.amount())
                    .filter(amount -> !excess.threshold().amountsRead().contains(amount));
            case Fixed _, PerUnitPerPeriod _, SlabLadder _, PercentOfBaseByPopulation _, LeastOf _,
                    AggregateWithCeiling _ -> Optional.empty();
        };
    }

    /**
     * How much more of {@link #raisedBy} lifts this shape by {@code rise} at {@code ctx}, exact (W-326a, D-696). An
     * excess still below its threshold needs the shortfall first.
     *
     * @throws IllegalStateException for a shape no single amount raises
     */
    default Money moreToRaiseBy(Money rise, CapContext ctx) {
        return switch (this) {
            case PercentOfBase percent -> percent.percent().baseYielding(rise);
            case PercentOfBaseByLocation byLocation -> byLocation.percentAt(ctx).baseYielding(rise);
            case AmountInExcess excess -> rise.plus(excess.shortOfThreshold(ctx));
            case Fixed _, PerUnitPerPeriod _, SlabLadder _, PercentOfBaseByPopulation _, LeastOf _,
                    AggregateWithCeiling _ -> throw new IllegalStateException(
                            "No single amount raises a " + shapeName() + ", so none can be asked for more of");
        };
    }

    default Set<BaseId> countsRead() {
        return switch (this) {
            case Fixed _, PercentOfBase _, SlabLadder _, PercentOfBaseByLocation _ -> Set.of();
            case PerUnitPerPeriod perUnit -> Stream.concat(perUnit.unit().stream(), Stream.of(perUnit.period()))
                    .collect(Collectors.toUnmodifiableSet());
            case PercentOfBaseByPopulation byPopulation -> Set.of(byPopulation.population());
            case AmountInExcess excess -> excess.threshold().countsRead();
            case LeastOf least -> least.limbs().stream().flatMap(limb -> limb.shape().countsRead().stream())
                    .collect(Collectors.toUnmodifiableSet());
            case AggregateWithCeiling aggregate -> aggregate.subCaps().values().stream()
                    .flatMap(subCap -> subCap.countsRead().stream()).collect(Collectors.toUnmodifiableSet());
        };
    }
}
