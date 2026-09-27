package com.thiru.wealthlens.taxengine.node;

import com.thiru.wealthlens.taxengine.input.Journey;
import com.thiru.wealthlens.taxengine.input.JourneyMode;
import com.thiru.wealthlens.taxengine.input.LeaveTravelBlock;
import com.thiru.wealthlens.taxengine.input.LedgerSnapshot;
import com.thiru.wealthlens.taxengine.pipeline.ComponentSet;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeContext;
import com.thiru.wealthlens.taxengine.rules.cap.CapContext;
import com.thiru.wealthlens.taxengine.rules.cap.CapResult;
import com.thiru.wealthlens.taxengine.rules.condition.HeadCode;
import com.thiru.wealthlens.taxengine.rules.definition.CommutedPensionShare;
import com.thiru.wealthlens.taxengine.rules.definition.DisabledTransportLimit;
import com.thiru.wealthlens.taxengine.rules.definition.HeadTotal;
import com.thiru.wealthlens.taxengine.rules.definition.JourneysPerCalendarBlock;
import com.thiru.wealthlens.taxengine.rules.definition.RuleDefinition;
import com.thiru.wealthlens.taxengine.rules.definition.SalaryBase;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.generated.RuleIds;
import com.thiru.wealthlens.taxengine.rules.id.BaseId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.RuleRow;
import com.thiru.wealthlens.taxengine.rules.pack.TemporallyApportionedCap;
import com.thiru.wealthlens.taxengine.rules.temporal.RuleVersion;
import com.thiru.wealthlens.taxengine.rules.temporal.TemporalContext;
import com.thiru.wealthlens.taxengine.trace.StepKind;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The leave travel concession's lines for {@link ExemptionNode} (Sch. III Table Sl. 8 with Rules 2026 r.278;
 * 1961 s.10(5) with Rules 1962 r.2B; D-100).
 *
 * <p>Journeys are taken in date order. Each falls in a block of calendar years, not tax years ({@code DR 13} R-12),
 * and counts against that block's two after what the ledger says earlier years claimed; a journey first taken in a
 * block's first calendar year after an unused block is the carried journey and is not counted (r.278(3)–(4)). An
 * exempt journey is the lesser of the amount incurred and the fare its mode admits — the fare the taxpayer states,
 * or r.278(1)(iii)(B)'s rate per kilometre where the year has one — and the journeys together never exceed the
 * assistance received.
 */
final class LeaveTravelExemption {

    private static final BaseId AMOUNT_INCURRED = new BaseId("AMOUNT_INCURRED");
    private static final BaseId FARE_ADMISSIBLE = new BaseId("FARE_ADMISSIBLE");
    private static final BaseId KM = new BaseId("KM");

    private LeaveTravelExemption() {
    }

    /** One EXEMPTION line per journey, numbered from {@code firstSequence}; none where the rule does not apply. */
    static List<TraceLine> lines(NodeContext ctx, int firstSequence) {
        TemporalContext t = ctx.inputs().temporal();
        RuleRow concession = ctx.rule(RuleIds.SAL_LEAVE_TRAVEL_CONCESSION);
        RuleVersion version = TemporallyApportionedCap.governingVersion(concession, t);
        List<Journey> journeys = ctx.inputs().entered().leaveTravel().stream()
                .sorted(Comparator.comparing(Journey::startedOn))
                .toList();
        if (journeys.isEmpty() || !version.regimes().appliesIn(ctx.regime().code())) {
            return List.of();
        }
        JourneysPerCalendarBlock blocks = blocks(ctx, t);
        Money remaining = HeadTotalLines.of(ctx, ctx.get(NodeIds.INPUTS_COMPONENTS, ComponentSet.class),
                new HeadTotal(HeadCode.SALARIES), RuleIds.SAL_LEAVE_TRAVEL_CONCESSION).total();
        BlockTally tally = new BlockTally(blocks, ctx.inputs().ledgers());
        List<TraceLine> lines = new ArrayList<>();
        for (Journey journey : journeys) {
            requireWithinYear(journey, t);
            int sequence = firstSequence + lines.size();
            MoneyValue spent = MoneyValue.of(journey.amountIncurred());
            if (!tally.admit(journey.startedOn().getYear())) {
                lines.add(line(sequence, spent, Money.ZERO, version)
                        .withLimitedBy("journeys in block: the Rules exempt " + blocks.journeys() + " in each block of "
                                + blocks.calendarYears() + " calendar years"));
                continue;
            }
            CapResult exempt = TemporallyApportionedCap.apply(concession, t, new CapContext(
                    Map.of(AMOUNT_INCURRED, journey.amountIncurred(), FARE_ADMISSIBLE, fareFor(ctx, t, journey))));
            Money allowed = Money.min(exempt.value(), remaining);
            remaining = remaining.minus(allowed);
            lines.add(line(sequence, spent, allowed, version)
                    .withLimitedBy(allowed.isLessThan(exempt.value()) ? "assistance received" : exempt.limitedBy()));
        }
        return lines;
    }

    private static JourneysPerCalendarBlock blocks(NodeContext ctx, TemporalContext t) {
        RuleVersion version =
                TemporallyApportionedCap.governingVersion(ctx.rule(RuleIds.SAL_LEAVE_TRAVEL_JOURNEYS), t);
        RuleDefinition definition = version.definition().orElseThrow(() -> new IllegalStateException(
                RuleIds.SAL_LEAVE_TRAVEL_JOURNEYS + " carries no definition of its blocks"));
        return switch (definition) {
            case JourneysPerCalendarBlock journeys -> journeys;
            case HeadTotal headTotal -> throw new IllegalStateException(
                    RuleIds.SAL_LEAVE_TRAVEL_JOURNEYS + " defines a head's total, not journeys per block");
            case SalaryBase salaryBase -> throw new IllegalStateException(
                    RuleIds.SAL_LEAVE_TRAVEL_JOURNEYS + " defines a salary base, not journeys per block");
            case CommutedPensionShare share -> throw new IllegalStateException(
                    RuleIds.SAL_LEAVE_TRAVEL_JOURNEYS + " defines a share of a commuted pension, not journeys per block");
            case DisabledTransportLimit transport -> throw new IllegalStateException(
                    RuleIds.SAL_LEAVE_TRAVEL_JOURNEYS + " defines a transport limit, not journeys per block");
        };
    }

    /** The fare the Rules admit: r.278(1)(iii)(B)'s rate per kilometre where the year has one, else as entered. */
    private static Money fareFor(NodeContext ctx, TemporalContext t, Journey journey) {
        boolean ratePerKilometre = journey.mode() == JourneyMode.NOT_RAIL_CONNECTED_WITHOUT_PUBLIC_TRANSPORT
                && ctx.pack().rules().containsKey(RuleIds.SAL_LEAVE_TRAVEL_DISTANCE_RATE);
        if (ratePerKilometre) {
            int km = journey.distanceKm().orElseThrow(() -> new IllegalArgumentException("A journey on "
                    + journey.startedOn() + " by road without public transport needs its distance; the Rules"
                    + " price it per kilometre"));
            return TemporallyApportionedCap.apply(ctx.rule(RuleIds.SAL_LEAVE_TRAVEL_DISTANCE_RATE), t,
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

    private static TraceLine line(int sequence, MoneyValue spent, Money exempt, RuleVersion version) {
        return TraceLine.of(sequence, NodeIds.SALARY_EXEMPTIONS, RuleIds.SAL_LEAVE_TRAVEL_CONCESSION,
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
