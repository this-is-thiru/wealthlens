package com.thiru.wealthlens.taxrules.pack;

import com.thiru.wealthlens.taxrules.id.RuleId;
import com.thiru.wealthlens.taxrules.registry.Provenance;
import com.thiru.wealthlens.taxrules.temporal.DateRange;
import com.thiru.wealthlens.taxrules.temporal.NoRuleVersionException;
import com.thiru.wealthlens.taxrules.temporal.RuleVersion;
import com.thiru.wealthlens.taxrules.temporal.TemporalAxis;
import com.thiru.wealthlens.taxrules.temporal.TemporalContext;
import com.thiru.wealthlens.taxrules.temporal.Validity;
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
                        .map(version -> windowOf(version.validity()))
                        .flatMap(Optional::stream)
                        .filter(window -> window.overlaps(year))
                        .count()
                > 1;
    }

    /**
     * The window a validity occupies, where it occupies one.
     *
     * <p>An exhaustive switch over a sealed family rather than a method on {@code Validity}, because
     * {@code Validity} is another item's type and this is the only caller. {@code 18-temporal-versioning.md}
     * V4 does put an {@code overlaps} method there, and if a second caller appears this belongs on the
     * variants instead — a fourth {@code Validity} would then break one line rather than several.
     *
     * <p>{@code PendingNotification} has no window on purpose: it is never in force, so it can never
     * be one of the two versions that make a rule change mid-year.
     */
    private static Optional<DateRange> windowOf(Validity validity) {
        return switch (validity) {
            case Validity.Between between -> Optional.of(new DateRange(between.from(), between.to()));
            case Validity.Retrospective retro ->
                    Optional.of(new DateRange(retro.deemedEffectiveFrom(), retro.to()));
            case Validity.PendingNotification ignored -> Optional.empty();
        };
    }
}
