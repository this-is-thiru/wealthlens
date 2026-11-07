package com.thiru.wealthlens.taxengine.rules.pack;

import com.thiru.wealthlens.taxengine.rules.cap.CapContext;
import com.thiru.wealthlens.taxengine.rules.cap.CapResult;
import com.thiru.wealthlens.taxengine.rules.cap.CapShape;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.temporal.DateRange;
import com.thiru.wealthlens.taxengine.rules.temporal.RuleVersion;
import com.thiru.wealthlens.taxengine.rules.temporal.TemporalContext;
import com.thiru.wealthlens.taxengine.rules.temporal.proration.ProrationBasis;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Applies a rule in a year it changed inside, the way the rule declares ({@code DR 18} V5, D-098).
 *
 * <p>Without this, every rule is read at year end and a mid-year change is applied as though the earlier
 * version never existed. A rule that did not change is applied as before. One that did is split into segments,
 * one per version in force for part of the year, and combined by its {@link MidYearCombination}:
 *
 * <ul>
 *   <li>{@code PRORATE_PER_SEGMENT} — each version's cap over the year's figures, apportioned by the segment's
 *       share of the year on the rule's {@link ProrationBasis}, and summed. A cap whose input is itself for part
 *       of the year cannot be split here — {@link CapContext} carries no dates — so its node splits it;
 *   <li>{@code LATEST_VERSION_GOVERNS} and {@code EARLIEST_VERSION_GOVERNS} — one version, the last or first;
 *   <li>{@code STATUTORY} — refused: no rule in the rulebook uses it, and it is built with the first that does;
 *   <li>{@code UNDETERMINED} — refused, never guessed.
 * </ul>
 *
 * <p>A node that needs the shape itself rather than its result — a slab ladder, whose bands marginal relief and
 * the rate-basis partition read — asks for the {@linkplain #governingVersion governing version}, which exists
 * only where one version governs.
 */
public final class TemporallyApportionedCap {

    private TemporallyApportionedCap() {
    }

    /** The rule's cap for the year, its versions combined as the rule declares. */
    public static CapResult apply(RuleRow rule, TemporalContext t, CapContext ctx) {
        if (!rule.changesWithin(t)) {
            return capOf(rule, rule.versionAt(t)).apply(ctx);
        }
        List<Segment> segments = segments(rule, t);
        return switch (rule.midYearCombination()) {
            case PRORATE_PER_SEGMENT -> prorated(rule, t, ctx, segments);
            case LATEST_VERSION_GOVERNS -> capOf(rule, segments.getLast().version()).apply(ctx);
            case EARLIEST_VERSION_GOVERNS -> capOf(rule, segments.getFirst().version()).apply(ctx);
            case STATUTORY -> throw statutoryNotBuilt(rule);
            case UNDETERMINED -> throw undetermined(rule, segments);
        };
    }

    /** The one version that governs the year; refused where the rule combines several. */
    public static RuleVersion governingVersion(RuleRow rule, TemporalContext t) {
        if (!rule.changesWithin(t)) {
            return rule.versionAt(t);
        }
        List<Segment> segments = segments(rule, t);
        return switch (rule.midYearCombination()) {
            case LATEST_VERSION_GOVERNS -> segments.getLast().version();
            case EARLIEST_VERSION_GOVERNS -> segments.getFirst().version();
            case PRORATE_PER_SEGMENT -> throw new IllegalStateException("Rule " + rule.id()
                    + " changes inside the year and combines its versions PRORATE_PER_SEGMENT, so no one version"
                    + " governs; a node that needs the shape itself cannot apply it — declare LATEST_VERSION_GOVERNS"
                    + " or EARLIEST_VERSION_GOVERNS if the statute allows");
            case STATUTORY -> throw statutoryNotBuilt(rule);
            case UNDETERMINED -> throw undetermined(rule, segments);
        };
    }

    private static CapResult prorated(RuleRow rule, TemporalContext t, CapContext ctx, List<Segment> segments) {
        ProrationBasis basis = rule.prorationBasis().orElseThrow(() -> new IllegalStateException(
                "Rule " + rule.id() + " is PRORATE_PER_SEGMENT with no prorationBasis"));
        Money total = Money.ZERO;
        StringBuilder limitedBy = new StringBuilder();
        for (Segment segment : segments) {
            CapResult whole = capOf(rule, segment.version()).apply(ctx);
            total = total.plus(basis.apportion(whole.value(), t.taxYear(), segment.range()));
            limitedBy.append(limitedBy.isEmpty() ? "" : "; ").append(segment.range()).append(": ")
                    .append(whole.limitedBy());
        }
        return new CapResult(total, limitedBy.toString());
    }

    /** Each version in force for part of the year, clipped to the year, in date order. */
    private static List<Segment> segments(RuleRow rule, TemporalContext t) {
        DateRange year = DateRange.between(t.taxYearStart(), t.taxYearEnd());
        return rule.versions().stream()
                .filter(version -> version.validity().window().filter(window -> window.overlaps(year)).isPresent())
                .map(version -> new Segment(version,
                        ProrationBasis.withinYear(t.taxYear(), version.validity().window().orElseThrow())
                                .orElseThrow()))
                .sorted(Comparator.comparing(segment -> segment.range().from()))
                .toList();
    }

    private static CapShape capOf(RuleRow rule, RuleVersion version) {
        return version.cap().orElseThrow(
                () -> new IllegalStateException("Rule " + rule.id() + " has a version with no cap to apply"));
    }

    private static IllegalStateException statutoryNotBuilt(RuleRow rule) {
        return new IllegalStateException("Rule " + rule.id() + " combines its mid-year versions by statute through"
                + " rule " + rule.midYearCombinationRule() + ", and no statutory combination is built yet; it is"
                + " built with the first rule that needs one (D-098)");
    }

    private static UndeterminedApportionmentException undetermined(RuleRow rule, List<Segment> segments) {
        return new UndeterminedApportionmentException(rule.id(),
                segments.stream().map(Segment::range).collect(Collectors.toList()));
    }

    /** One version and the part of the year it governs. */
    private record Segment(RuleVersion version, DateRange range) {
    }
}
