package com.thiru.wealthlens.taxengine.rules.pack;

import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.registry.Provenance;
import com.thiru.wealthlens.taxengine.rules.temporal.DateRange;
import com.thiru.wealthlens.taxengine.rules.temporal.NoRuleVersionException;
import com.thiru.wealthlens.taxengine.rules.temporal.RuleVersion;
import com.thiru.wealthlens.taxengine.rules.temporal.TemporalAxis;
import com.thiru.wealthlens.taxengine.rules.temporal.TemporalContext;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * One rule: a stable identity, the clock it is read against, and its versions in order.
 *
 * <p>The id is the contract. It appears in stored computations, in trace lines, in golden tests and
 * in the claim catalogue, so it must survive every amendment to what the rule says. That is why a
 * mid-year change is a second {@link RuleVersion} here rather than a second rule — and why
 * {@code EffectiveBetween} was withdrawn from the condition algebra, where it would have forced the
 * opposite.
 *
 * @param id stable across every version
 * @param axis which clock this rule is read against — {@code D-TEMPORALKEY}
 * @param versions ordered, non-overlapping and gap-free within the pack's year
 * @param midYearCombination how to apply this rule in a year it changed inside — declared, never
 *     assumed. Irrelevant in a year it did not change
 * @param midYearCombinationRule the rule supplying the combination, required when
 *     {@code midYearCombination} is {@link MidYearCombination#STATUTORY} and meaningless otherwise
 * @param headProvenance where the rule as a whole was sourced
 */
public record RuleRow(
        RuleId id,
        TemporalAxis axis,
        List<RuleVersion> versions,
        MidYearCombination midYearCombination,
        RuleId midYearCombinationRule,
        Provenance headProvenance) {

    /** A rule with no mid-year change authored, which is the ordinary case. */
    public RuleRow(RuleId id, TemporalAxis axis, List<RuleVersion> versions, Provenance headProvenance) {
        this(id, axis, versions, MidYearCombination.UNDETERMINED, null, headProvenance);
    }

    public RuleRow {
        if (id == null) {
            throw new IllegalArgumentException("A rule row must have an id");
        }
        if (axis == null) {
            throw new IllegalArgumentException("Rule " + id + " must declare a TemporalAxis");
        }
        if (versions == null || versions.isEmpty()) {
            throw new IllegalArgumentException("Rule " + id + " has no versions");
        }
        versions = List.copyOf(versions);

        if (midYearCombination == null) {
            throw new IllegalArgumentException(
                    "Rule " + id + " must state a MidYearCombination; UNDETERMINED is the honest"
                            + " default, and null is not");
        }
        if (midYearCombination == MidYearCombination.STATUTORY && midYearCombinationRule == null) {
            throw new IllegalArgumentException(
                    "Rule " + id + " combines mid-year changes STATUTORY but names no rule to do it");
        }
        if (midYearCombination != MidYearCombination.STATUTORY && midYearCombinationRule != null) {
            throw new IllegalArgumentException(
                    "Rule " + id + " names combination rule " + midYearCombinationRule
                            + " but its combination is " + midYearCombination
                            + ", which would never consult it");
        }
    }

    /**
     * The version in force on this computation's coordinate for this rule's axis.
     *
     * @throws NoRuleVersionException where no version covers that date — the law said nothing then,
     *     and there is no defensible amount to return
     */
    public RuleVersion versionAt(TemporalContext t) {
        LocalDate at = t.on(axis);
        return versions.stream()
                .filter(version -> version.validity().contains(at))
                .findFirst()
                .orElseThrow(() -> new NoRuleVersionException(id, axis, at));
    }

    /**
     * Whether this rule changed inside the tax year, which is what drives apportionment.
     *
     * <p>Not hypothetical: the Income-tax Rules 2026 were amended four times within the year they
     * govern, and one amending instrument commenced partly on 1 April and partly on the date of its
     * own publication. A rule that changes mid-year cannot be applied as a single annual figure.
     */
    public boolean changesWithin(TemporalContext t) {
        DateRange year = DateRange.between(t.taxYearStart(), t.taxYearEnd());
        return versions.stream()
                        .map(version -> version.validity().window())
                        .flatMap(Optional::stream)
                        .filter(window -> window.overlaps(year))
                        .count()
                > 1;
    }
}
