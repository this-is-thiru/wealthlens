package com.thiru.wealthlens.taxengine.rules.condition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.definition.CapitalGainsPart;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The six conditions on a computed return's facts (W-090c1b1, W-090c1b2): r.164(2)(c)'s loss under a head, (d)'s "only
 * long-term capital gains under section 198 not exceeding ₹ 125000" and (3)(e)'s s.93 deduction — each a fact, with
 * the head, part, limit and rows as data — and (2)(b) and (d)'s loss brought forward or to be carried forward.
 * Figures chosen by hand.
 */
class ReturnFactConditionsTest {

    private static final CapitalGainsPart LONG_LISTED = new CapitalGainsPart("LONG_TERM_LISTED_EQUITY");
    private static final CapitalGainsPart SHORT_SLAB = new CapitalGainsPart("SHORT_TERM_SLAB");
    private static final RuleId COMMISSION = new RuleId("OS-BANKER-COMMISSION");
    private static final RuleId FAMILY_PENSION = new RuleId("OS-FAMILY-PENSION");

    private static ConditionContext returning(Map<HeadCode, Money> heads, Map<CapitalGainsPart, Money> gains,
            Set<RuleId> deductions) {
        return new ConditionContext(ResidentialStatus.RESIDENT, PersonType.INDIVIDUAL, Map.of(), Map.of())
                .reporting(ReturnFacts.of(heads, gains, deductions, Map.of(), Map.of()));
    }

    private static ConditionContext losses(Map<HeadCode, Money> broughtForward, Map<HeadCode, Money> toCarryForward) {
        return new ConditionContext(ResidentialStatus.RESIDENT, PersonType.INDIVIDUAL, Map.of(), Map.of())
                .reporting(ReturnFacts.of(Map.of(), Map.of(), Set.of(), broughtForward, toCarryForward));
    }

    private static ConditionContext gains(Map<CapitalGainsPart, Money> gains) {
        return returning(Map.of(), gains, Set.of());
    }

    @Test
    void hasLossUnderHead_whenTheHeadIsBelowNil_holds() {
        ConditionContext loss = returning(Map.of(HeadCode.HOUSE_PROPERTY, Money.of("-20000")), Map.of(), Set.of());
        assertThat(new HasLossUnderHead(HeadCode.HOUSE_PROPERTY).holds(loss)).isTrue();
    }

    @Test
    void hasLossUnderHead_whenTheHeadIsNil_fails() {
        ConditionContext nil = returning(Map.of(HeadCode.HOUSE_PROPERTY, Money.ZERO), Map.of(), Set.of());
        assertThat(new HasLossUnderHead(HeadCode.HOUSE_PROPERTY).holds(nil)).isFalse();
    }

    @Test
    void hasLossUnderHead_whenTheHeadWasNotComputed_throws() {
        // Given — business income is not computed: unknown, never "no loss"
        ConditionContext salaried = returning(Map.of(HeadCode.SALARIES, Money.of("900000")), Map.of(), Set.of());

        // When / Then
        assertThatThrownBy(() -> new HasLossUnderHead(HeadCode.BUSINESS_OR_PROFESSION).holds(salaried))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BUSINESS_OR_PROFESSION was not computed");
    }

    @Test
    void hasLossUnderHead_whenTheHeadIsCapitalGains_refuses() {
        // Given — the head's figure is never below nil; its loss of the year is a loss to carry forward (W-090c1b2)
        // When / Then
        assertThatThrownBy(() -> new HasLossUnderHead(HeadCode.CAPITAL_GAINS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("HasLossCarriedForward");
    }

    @Test
    void hasLossBroughtForward_whenOneRupeeWasBroughtForward_holds() {
        // Given
        ConditionContext one = losses(Map.of(HeadCode.HOUSE_PROPERTY, Money.of("1")), Map.of());

        // When / Then
        assertThat(new HasLossBroughtForward(HeadCode.HOUSE_PROPERTY).holds(one)).isTrue();
    }

    @Test
    void hasLossBroughtForward_whenNilWasBroughtForward_fails() {
        // Given
        ConditionContext nil = losses(Map.of(HeadCode.HOUSE_PROPERTY, Money.ZERO), Map.of());

        // When / Then
        assertThat(new HasLossBroughtForward(HeadCode.HOUSE_PROPERTY).holds(nil)).isFalse();
    }

    @Test
    void hasLossCarriedForward_whenOneRupeeIsLeftToCarry_holds() {
        // Given
        ConditionContext one = losses(Map.of(), Map.of(HeadCode.CAPITAL_GAINS, Money.of("1")));

        // When / Then
        assertThat(new HasLossCarriedForward(HeadCode.CAPITAL_GAINS).holds(one)).isTrue();
    }

    @Test
    void hasLossCarriedForward_whenNilIsLeftToCarry_fails() {
        // Given
        ConditionContext nil = losses(Map.of(), Map.of(HeadCode.CAPITAL_GAINS, Money.ZERO));

        // When / Then
        assertThat(new HasLossCarriedForward(HeadCode.CAPITAL_GAINS).holds(nil)).isFalse();
    }

    @Test
    void lossConditions_whenTheHeadWasNotComputed_throw() {
        // Given — business income is not computed: unknown, never "no loss"
        ConditionContext salaried = losses(Map.of(HeadCode.SALARIES, Money.ZERO), Map.of(HeadCode.SALARIES, Money.ZERO));

        // When / Then
        assertThatThrownBy(() -> new HasLossBroughtForward(HeadCode.BUSINESS_OR_PROFESSION).holds(salaried))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BUSINESS_OR_PROFESSION was not computed");
        assertThatThrownBy(() -> new HasLossCarriedForward(HeadCode.BUSINESS_OR_PROFESSION).holds(salaried))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BUSINESS_OR_PROFESSION was not computed");
    }

    @Test
    void capitalGainsOnlyInParts_whenTheGainIsInTheNamedPartAlone_holds() {
        assertThat(new CapitalGainsOnlyInParts(Set.of(LONG_LISTED))
                .holds(gains(Map.of(LONG_LISTED, Money.of("125000"))))).isTrue();
    }

    @Test
    void capitalGainsOnlyInParts_whenAnotherPartHasOneRupee_fails() {
        assertThat(new CapitalGainsOnlyInParts(Set.of(LONG_LISTED))
                .holds(gains(Map.of(LONG_LISTED, Money.of("125000"), SHORT_SLAB, Money.of("1"))))).isFalse();
    }

    @Test
    void capitalGainsOnlyInParts_whenThereAreNoGains_holds() {
        // Given — "only" excludes other gains; another part at nil is no gain
        assertThat(new CapitalGainsOnlyInParts(Set.of(LONG_LISTED)).holds(gains(Map.of(SHORT_SLAB, Money.ZERO))))
                .isTrue();
    }

    @Test
    void capitalGainsPartAtMost_whenTheGainEqualsTheLimit_holds() {
        assertThat(new CapitalGainsPartAtMost(LONG_LISTED, Money.of("125000"))
                .holds(gains(Map.of(LONG_LISTED, Money.of("125000"))))).isTrue();
    }

    @Test
    void capitalGainsPartAtMost_whenTheGainIsOneRupeeAbove_fails() {
        assertThat(new CapitalGainsPartAtMost(LONG_LISTED, Money.of("125000"))
                .holds(gains(Map.of(LONG_LISTED, Money.of("125001"))))).isFalse();
    }

    @Test
    void capitalGainsPartAtMost_whenThePartHasNoGain_holds() {
        assertThat(new CapitalGainsPartAtMost(LONG_LISTED, Money.of("125000")).holds(gains(Map.of()))).isTrue();
    }

    @Test
    void deductionAllowedUnder_whenANamedRowAllowedOne_holds() {
        ConditionContext commission = returning(Map.of(), Map.of(), Set.of(COMMISSION));
        assertThat(new DeductionAllowedUnder(Set.of(COMMISSION)).holds(commission)).isTrue();
    }

    @Test
    void deductionAllowedUnder_whenOnlyAnUnnamedRowAllowedOne_fails() {
        // Given — the family pension's deduction (s.93(1)(d)) is the one r.164(3)(e) leaves out
        ConditionContext pension = returning(Map.of(), Map.of(), Set.of(FAMILY_PENSION));
        assertThat(new DeductionAllowedUnder(Set.of(COMMISSION)).holds(pension)).isFalse();
    }

    @Test
    void anyReturnFact_whenNoReturnWasSupplied_throws() {
        ConditionContext rule = new ConditionContext(ResidentialStatus.RESIDENT, PersonType.INDIVIDUAL, Map.of(), Map.of());
        assertThatThrownBy(() -> new DeductionAllowedUnder(Set.of(COMMISSION)).holds(rule))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No computed return's facts were supplied");
    }

    @Test
    void constructors_whenNothingIsNamed_refuse() {
        assertThatThrownBy(() -> new CapitalGainsOnlyInParts(Set.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DeductionAllowedUnder(Set.of())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void returnFacts_whenAnyCollectionIsNull_refuses() {
        // Given — each of the five collections in turn; "absent" is an empty collection, never null (CLAUDE.md §2)
        Map<HeadCode, Money> heads = Map.of();
        Map<CapitalGainsPart, Money> parts = Map.of();
        Set<RuleId> rows = Set.of();
        String message = "ReturnFacts collections may not be null; pass empty ones";

        // When / Then
        assertThatThrownBy(() -> ReturnFacts.of(null, parts, rows, heads, heads)).hasMessage(message);
        assertThatThrownBy(() -> ReturnFacts.of(heads, null, rows, heads, heads)).hasMessage(message);
        assertThatThrownBy(() -> ReturnFacts.of(heads, parts, null, heads, heads)).hasMessage(message);
        assertThatThrownBy(() -> ReturnFacts.of(heads, parts, rows, null, heads)).hasMessage(message);
        assertThatThrownBy(() -> ReturnFacts.of(heads, parts, rows, heads, null)).hasMessage(message);
    }

    @Test
    void constructors_whenWhatTheyReadIsNull_refuse() {
        assertThatThrownBy(() -> new HasLossUnderHead(null)).hasMessage("HasLossUnderHead must name a head");
        assertThatThrownBy(() -> new HasLossBroughtForward(null)).hasMessage("HasLossBroughtForward must name a head");
        assertThatThrownBy(() -> new HasLossCarriedForward(null)).hasMessage("HasLossCarriedForward must name a head");
        assertThatThrownBy(() -> new CapitalGainsOnlyInParts(null))
                .hasMessage("CapitalGainsOnlyInParts must name at least one part");
        assertThatThrownBy(() -> new DeductionAllowedUnder(null)).hasMessage("DeductionAllowedUnder must name at least one row");
        assertThatThrownBy(() -> new CapitalGainsPartAtMost(null, Money.of("125000")))
                .hasMessage("CapitalGainsPartAtMost must name a part and a limit");
        assertThatThrownBy(() -> new CapitalGainsPartAtMost(LONG_LISTED, null))
                .hasMessage("CapitalGainsPartAtMost must name a part and a limit");
    }
}
