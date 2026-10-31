package com.thiru.wealthlens.taxengine.node;

import com.thiru.wealthlens.taxengine.input.FamilySnapshot;
import com.thiru.wealthlens.taxengine.input.Journey;
import com.thiru.wealthlens.taxengine.input.JourneyTraveller;
import com.thiru.wealthlens.taxengine.input.LeaveTravelBlock;
import com.thiru.wealthlens.taxengine.input.LedgerSnapshot;
import com.thiru.wealthlens.taxengine.pipeline.ComponentSet;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeContext;
import com.thiru.wealthlens.taxengine.rules.cap.CapContext;
import com.thiru.wealthlens.taxengine.rules.cap.CapResult;
import com.thiru.wealthlens.taxengine.rules.condition.HeadCode;
import com.thiru.wealthlens.taxengine.rules.condition.ItemFacts;
import com.thiru.wealthlens.taxengine.rules.definition.HeadTotal;
import com.thiru.wealthlens.taxengine.rules.definition.JourneysPerCalendarBlock;
import com.thiru.wealthlens.taxengine.rules.definition.RuleDefinitions;
import com.thiru.wealthlens.taxengine.rules.definition.SurvivingChildrenLimit;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.generated.RuleSetIds;
import com.thiru.wealthlens.taxengine.rules.id.BaseId;
import com.thiru.wealthlens.taxengine.rules.id.ItemFactId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.RuleRow;
import com.thiru.wealthlens.taxengine.rules.pack.TemporallyApportionedCap;
import com.thiru.wealthlens.taxengine.rules.temporal.RuleVersion;
import com.thiru.wealthlens.taxengine.rules.temporal.TemporalContext;
import com.thiru.wealthlens.taxengine.trace.StepKind;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

/**
 * The leave travel concession's lines for {@link ExemptionNode} (Sch. III Table Sl. 8 with Rules 2026 r.278;
 * 1961 s.10(5) with Rules 1962 r.2B; D-100).
 *
 * <p>Journeys are taken in date order. Each falls in a block of calendar years, not tax years ({@code DR 13} R-12),
 * and counts against that block's two after what the ledger says earlier years claimed; a journey first taken in a
 * block's first calendar year after an unused block is the carried journey and is not counted (r.278(3)–(4)). An
 * exempt journey is the lesser of the amount incurred and the fare its mode admits — the fare the taxpayer states,
 * or r.278(1)(iii)(B)'s rate per kilometre where the year has one — and the journeys together never exceed the
 * assistance received. Where the year's row does not apply in the regime, each journey still gets a line — nil,
 * naming the regime — as HRA's does (D-104, D-153): a claim answered by silence reads as one never seen.
 *
 * <p>The concession covers no more than two surviving children, not counting children born before the rule's date
 * nor children of a multiple birth after one child (r.278(5)–(6); Rules 1962 r.2B(4); D-212). Where more are
 * counted, the two eldest are covered, and each journey must be split by traveller: a child left out gets a nil
 * line of its own and the journey is exempt on the rest. A journey priced per kilometre keeps the whole rate for its
 * distance: the rate prices the journey, not a person, so the child left out takes no share of it (D-228).
 */
final class LeaveTravelExemption {

    private static final BaseId AMOUNT_INCURRED = new BaseId("AMOUNT_INCURRED");
    private static final BaseId FARE_ADMISSIBLE = new BaseId("FARE_ADMISSIBLE");
    private static final BaseId KM = new BaseId("KM");

    /** How a journey was made — what {@code SAL-LEAVE-TRAVEL-DISTANCE-RATE} reads (W-156e, D-350). */
    private static final ItemFactId JOURNEY_MODE = new ItemFactId("journey.mode");

    private LeaveTravelExemption() {
    }

    /** One EXEMPTION line per journey, numbered from {@code firstSequence}; nil ones where the regime excludes it. */
    static List<TraceLine> lines(NodeContext ctx, int firstSequence) {
        TemporalContext t = ctx.inputs().temporal();
        RuleRow concession = ctx.ruleOf(RuleSetIds.RS_SAL_LEAVE_TRAVEL_CONCESSION);
        RuleVersion version = TemporallyApportionedCap.governingVersion(concession, t);
        List<Journey> journeys = ctx.inputs().entered().leaveTravel().stream()
                .sorted(Comparator.comparing(Journey::startedOn))
                .toList();
        if (journeys.isEmpty()) {
            return List.of();
        }
        if (!ctx.inRegime(ctx.ruleIdOf(RuleSetIds.RS_SAL_LEAVE_TRAVEL_CONCESSION), version).applies()) {
            return nilLines(ctx, journeys, firstSequence, version);
        }
        JourneysPerCalendarBlock blocks = blocks(ctx, t);
        Money remaining = HeadTotalLines.of(ctx, ctx.get(NodeIds.INPUTS_COMPONENTS, ComponentSet.class),
                new HeadTotal(HeadCode.SALARIES), ctx.ruleIdOf(RuleSetIds.RS_SAL_LEAVE_TRAVEL_CONCESSION)).total();
        BlockTally tally = new BlockTally(blocks, ctx.inputs().ledgers());
        List<TraceLine> lines = new ArrayList<>();
        for (Journey journey : journeys) {
            requireWithinYear(journey, t);
            if (!tally.admit(journey.startedOn().getYear())) {
                lines.add(line(ctx, firstSequence + lines.size(), MoneyValue.of(journey.amountIncurred()), Money.ZERO,
                        version).withLimitedBy("journeys in block: the Rules exempt " + blocks.journeys()
                                + " in each block of " + blocks.calendarYears() + " calendar years"));
                continue;
            }
            Covered covered = covered(ctx, t, journey, firstSequence + lines.size());
            lines.addAll(covered.leftOut());
            int sequence = firstSequence + lines.size();
            MoneyValue spent = MoneyValue.of(covered.amountIncurred());
            Money fare = covered.fareAdmissible().orElseGet(() -> fareFor(ctx, t, journey));
            CapResult exempt = TemporallyApportionedCap.apply(concession, t, new CapContext(
                    Map.of(AMOUNT_INCURRED, covered.amountIncurred(), FARE_ADMISSIBLE, fare)));
            Money allowed = Money.min(exempt.value(), remaining);
            remaining = remaining.minus(allowed);
            lines.add(line(ctx, sequence, spent, allowed, version)
                    .withLimitedBy(allowed.isLessThan(exempt.value()) ? "assistance received" : exempt.limitedBy()));
        }
        return lines;
    }

    /** A nil line per journey, naming why the regime excludes the concession. */
    private static List<TraceLine> nilLines(NodeContext ctx, List<Journey> journeys, int firstSequence,
            RuleVersion version) {
        List<TraceLine> nil = new ArrayList<>();
        for (Journey journey : journeys) {
            nil.add(line(ctx, firstSequence + nil.size(), MoneyValue.of(journey.amountIncurred()), Money.ZERO, version)
                    .withLimitedBy(ctx.inRegime(ctx.ruleIdOf(RuleSetIds.RS_SAL_LEAVE_TRAVEL_CONCESSION), version).whyNot()));
        }
        return nil;
    }

    /** A journey's travel the concession covers, and a nil line for each child it leaves out. */
    private record Covered(Money amountIncurred, Optional<Money> fareAdmissible, List<TraceLine> leftOut) {
    }

    /**
     * The journey less the travel of the children beyond the limit: whole where no child is beyond it, else split by
     * traveller, which the journey must then be.
     */
    private static Covered covered(NodeContext ctx, TemporalContext t, Journey journey, int firstSequence) {
        RuleVersion version =
                TemporallyApportionedCap.governingVersion(ctx.ruleOf(RuleSetIds.RS_SAL_LEAVE_TRAVEL_CHILDREN), t);
        SurvivingChildrenLimit limit = RuleDefinitions.require(version.definition(), SurvivingChildrenLimit.class,
                ctx.ruleIdOf(RuleSetIds.RS_SAL_LEAVE_TRAVEL_CHILDREN));
        List<FamilySnapshot.Member> family = ctx.inputs().family().members();
        List<Integer> beyond = childrenBeyond(limit, family, journey.startedOn());
        if (beyond.isEmpty()) {
            return new Covered(journey.amountIncurred(), journey.fareAdmissible(), List.of());
        }
        requireTravellersEntered(limit, beyond, journey);
        Money spent = Money.ZERO;
        Money fares = Money.ZERO;
        List<TraceLine> leftOut = new ArrayList<>();
        for (JourneyTraveller traveller : journey.travellers()) {
            Optional<Integer> position = traveller.familyMember();
            requireInFamily(journey, traveller, family);
            if (position.filter(beyond::contains).isEmpty()) {
                spent = spent.plus(traveller.amountIncurred());
                fares = fares.plus(traveller.fareAdmissible().orElse(Money.ZERO));
                continue;
            }
            String perKilometre = journey.fareAdmissible().isEmpty()
                    ? "; the rate per kilometre is for the journey's distance, so none of it is the child's"
                            + " (r.278(1)(iii)(B); D-228)"
                    : "";
            leftOut.add(TraceLine.of(firstSequence + leftOut.size(), NodeIds.SALARY_EXEMPTIONS,
                    ctx.ruleIdOf(RuleSetIds.RS_SAL_LEAVE_TRAVEL_CHILDREN), StepKind.EXEMPTION, MoneyValue.of(traveller.amountIncurred()),
                    MoneyValue.of(Money.ZERO), version.provenance()).withLimitedBy("family position " + position.get()
                    + ": a child beyond the " + limit.children() + " surviving children the concession covers"
                    + perKilometre));
        }
        Money admitted = fares;
        return new Covered(spent, journey.fareAdmissible().map(whole -> admitted), List.copyOf(leftOut));
    }

    /** With children beyond the limit, the journey must say who travelled, so the children's share can be left out. */
    private static void requireTravellersEntered(SurvivingChildrenLimit limit, List<Integer> beyond, Journey journey) {
        if (journey.travellers().isEmpty()) {
            throw new IllegalArgumentException("The family has children beyond the " + limit.children()
                    + " surviving children the concession covers (family positions " + beyond + "); enter who"
                    + " travelled on the journey of " + journey.startedOn() + ", with each person's amount and fare"
                    + " (S4 r.278(5)–(6); Rules 1962 r.2B(4))");
        }
    }

    private static void requireInFamily(Journey journey, JourneyTraveller traveller,
            List<FamilySnapshot.Member> family) {
        traveller.familyMember().filter(member -> member >= family.size()).ifPresent(member -> {
            throw new IllegalArgumentException("A traveller on the journey of " + journey.startedOn()
                    + " is family position " + member + ", but the family has " + family.size() + " members");
        });
    }

    /**
     * The family positions of the counted children beyond the limit on {@code date}: children alive then, born on or
     * after the rule's date and not of a multiple birth after one child, past the eldest the limit allows (D-212 C3a).
     */
    private static List<Integer> childrenBeyond(SurvivingChildrenLimit limit, List<FamilySnapshot.Member> family,
            LocalDate date) {
        List<Integer> counted = IntStream.range(0, family.size())
                .filter(position -> counted(limit, family.get(position), date))
                .boxed()
                .sorted(Comparator.comparing(position -> family.get(position).dateOfBirth()))
                .toList();
        return counted.size() <= limit.children() ? List.of() : counted.subList(limit.children(), counted.size());
    }

    private static boolean counted(SurvivingChildrenLimit limit, FamilySnapshot.Member member, LocalDate date) {
        return member.relationship() == FamilySnapshot.Relationship.CHILD
                && !member.multipleBirthAfterFirstChild()
                && limit.counts(member.dateOfBirth())
                && !member.dateOfBirth().isAfter(date)
                && member.dateOfDeath().filter(death -> !death.isAfter(date)).isEmpty();
    }

    private static JourneysPerCalendarBlock blocks(NodeContext ctx, TemporalContext t) {
        RuleVersion version =
                TemporallyApportionedCap.governingVersion(ctx.ruleOf(RuleSetIds.RS_SAL_LEAVE_TRAVEL_JOURNEYS), t);
        return RuleDefinitions.require(version.definition(), JourneysPerCalendarBlock.class, ctx.ruleIdOf(RuleSetIds.RS_SAL_LEAVE_TRAVEL_JOURNEYS));
    }

    /**
     * The fare the Rules admit: r.278(1)(iii)(B)'s rate per kilometre for a journey the year's row reaches by its mode,
     * else as entered (W-156e, D-350).
     */
    private static Money fareFor(NodeContext ctx, TemporalContext t, Journey journey) {
        Optional<RuleRow> distanceRate = ctx.optionalRuleOf(RuleSetIds.RS_SAL_LEAVE_TRAVEL_DISTANCE_RATE);
        boolean ratePerKilometre = distanceRate
                .filter(row -> RowReach.holdsFor(ctx, row, ItemFacts.of(JOURNEY_MODE, journey.mode().name())))
                .isPresent();
        if (ratePerKilometre) {
            int km = journey.distanceKm().orElseThrow(() -> new IllegalArgumentException("A journey on "
                    + journey.startedOn() + " by road without public transport needs its distance; the Rules"
                    + " price it per kilometre"));
            return TemporallyApportionedCap.apply(distanceRate.get(), t,
                    new CapContext(Map.of(), Map.of(), Map.of(KM, km))).value();
        }
        return journey.fareAdmissible().orElseThrow(() -> new IllegalArgumentException("A journey on "
                + journey.startedOn() + " needs the fare the Rules admit for " + journey.mode() + "; this year's"
                + " Rules state no rate for it"));
    }

    private static void requireWithinYear(Journey journey, TemporalContext t) {
        if (journey.startedOn().isBefore(t.taxYearStart()) || journey.startedOn().isAfter(t.taxYearEnd())) {
            throw new IllegalArgumentException("A journey on " + journey.startedOn() + " is outside the tax year "
                    + t.taxYear().label() + "; claim it in the year it was made");
        }
    }

    private static TraceLine line(NodeContext ctx, int sequence, MoneyValue spent, Money exempt, RuleVersion version) {
        return TraceLine.of(sequence, NodeIds.SALARY_EXEMPTIONS, ctx.ruleIdOf(RuleSetIds.RS_SAL_LEAVE_TRAVEL_CONCESSION),
                StepKind.EXEMPTION, spent, MoneyValue.of(exempt), version.provenance());
    }

    /** Journeys counted per block: the ledger's earlier years plus this year's, with the carry-over applied. */
    private static final class BlockTally {

        private final JourneysPerCalendarBlock blocks;
        private final LedgerSnapshot ledger;
        private final Map<Integer, Integer> countedThisYear = new HashMap<>();
        private final Map<Integer, Boolean> carriedThisYear = new HashMap<>();

        BlockTally(JourneysPerCalendarBlock blocks, LedgerSnapshot ledger) {
            this.blocks = blocks;
            this.ledger = ledger;
        }

        /** Whether a journey in {@code calendarYear} is exempt, recording it if so. */
        boolean admit(int calendarYear) {
            int block = blocks.blockStarting(calendarYear);
            if (calendarYear == block && unused(block - blocks.calendarYears()) && !carried(block)) {
                carriedThisYear.put(block, true);
                return true;
            }
            int counted = ledger.leaveTravelBlock(block).journeysClaimed() + countedThisYear.getOrDefault(block, 0);
            if (counted >= blocks.journeys()) {
                return false;
            }
            countedThisYear.merge(block, 1, Integer::sum);
            return true;
        }

        private boolean unused(int block) {
            LeaveTravelBlock earlier = ledger.leaveTravelBlock(block);
            return earlier.journeysClaimed() == 0 && !earlier.carriedJourneyClaimed()
                    && countedThisYear.getOrDefault(block, 0) == 0 && !carriedThisYear.getOrDefault(block, false);
        }

        private boolean carried(int block) {
            return ledger.leaveTravelBlock(block).carriedJourneyClaimed() || carriedThisYear.getOrDefault(block, false);
        }
    }
}
