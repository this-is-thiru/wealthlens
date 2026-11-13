package com.thiru.wealthlens.taxengine.rules.filing;

import com.thiru.wealthlens.taxengine.rules.condition.ReturnFacts;
import com.thiru.wealthlens.taxengine.rules.filing.FieldSource.FromCapitalGainsPart;
import com.thiru.wealthlens.taxengine.rules.filing.FieldSource.FromNodes;
import com.thiru.wealthlens.taxengine.rules.filing.FieldSource.FromRules;
import com.thiru.wealthlens.taxengine.rules.filing.FieldSource.FromTaxBasis;
import com.thiru.wealthlens.taxengine.rules.filing.FieldSource.ManualEntry;
import com.thiru.wealthlens.taxengine.rules.filing.FieldSource.SumOf;
import com.thiru.wealthlens.taxengine.rules.id.CellCode;
import com.thiru.wealthlens.taxengine.rules.id.NodeId;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Fills a form's schedules from a computed return (DR 21 I5; W-093, D-654): one fold over the schedule definitions, not
 * a mapper per form. Each cell's {@link FieldSource} is read against the run's figures; a cell the run cannot fill is
 * returned with the reason, never blank (C4a).
 */
public final class ScheduleMapper {

    private ScheduleMapper() {
    }

    /**
     * What a computed return gives the cells.
     *
     * @param nodes each node's amount, for nodes that compute one
     * @param rules each rule's figures in the trace, summed; a rule absent did not apply in the run
     * @param returned the return's facts, for the capital gains by part
     * @param taxAtSlabRates tax before rebate at the slab rates, where the run computed it
     * @param taxAtSpecialRates tax before rebate at each special rate, by the rate's code
     */
    public record Figures(Map<NodeId, Money> nodes, Map<RuleId, Money> rules, ReturnFacts returned,
            Optional<Money> taxAtSlabRates, Map<String, Money> taxAtSpecialRates) {
        public Figures {
            if (returned == null || taxAtSlabRates == null) {
                throw new IllegalArgumentException("Figures needs the return's facts and says whether slab tax was computed");
            }
            nodes = Map.copyOf(nodes);
            rules = Map.copyOf(rules);
            taxAtSpecialRates = Map.copyOf(taxAtSpecialRates);
        }
    }

    /** A cell's figure or the reason it has none. */
    private record Reading(Optional<Money> amount, Optional<String> unavailableBecause) {
        static Reading of(Money amount) {
            return new Reading(Optional.of(amount), Optional.empty());
        }

        static Reading unavailable(String reason) {
            return new Reading(Optional.empty(), Optional.of(reason));
        }
    }

    /** Every cell of the form's schedules, in the form's order. */
    public static List<FilledCell> fill(ItrFormSpec form, Figures figures) {
        Map<CellCode, ScheduleCell> cells = new HashMap<>();
        form.schedules().forEach(schedule -> schedule.fields().forEach(field -> cells.put(field.cell().code(), field.cell())));
        Map<CellCode, Reading> read = new HashMap<>();
        List<FilledCell> filled = new ArrayList<>();
        for (ItrSchedule schedule : form.schedules()) {
            for (ScheduleField field : schedule.fields()) {
                Reading reading = reading(field.cell(), cells, figures, read);
                filled.add(new FilledCell(schedule.code(), field.path(), field.cell().code(), field.cell().label(),
                        reading.amount(), reading.unavailableBecause()));
            }
        }
        return filled;
    }

    private static Reading reading(ScheduleCell cell, Map<CellCode, ScheduleCell> cells, Figures figures,
            Map<CellCode, Reading> read) {
        Reading known = read.get(cell.code());
        if (known != null) {
            return known;
        }
        Reading reading = signed(switch (cell.source()) {
            case FromNodes nodes -> fromNodes(nodes, figures);
            case FromTaxBasis basis -> fromTaxBasis(basis, figures);
            case FromRules rules -> fromRules(rules, figures);
            case FromCapitalGainsPart part ->
                    Reading.of(figures.returned().capitalGains().getOrDefault(part.part(), Money.ZERO));
            case SumOf sum -> sumOf(sum, cells, figures, read);
            case ManualEntry manual -> Reading.unavailable(manual.guidance());
        }, cell.sign());
        read.put(cell.code(), reading);
        return reading;
    }

    private static Reading signed(Reading reading, ScheduleCell.Sign sign) {
        if (reading.amount().isEmpty()) {
            return reading;
        }
        Money amount = reading.amount().get();
        return Reading.of(switch (sign) {
            case AS_COMPUTED -> amount;
            case NIL_IF_LOSS, POSITIVE_PART -> amount.isLessThan(Money.ZERO) ? Money.ZERO : amount;
            case NEGATIVE_PART -> amount.isLessThan(Money.ZERO) ? Money.ZERO.minus(amount) : Money.ZERO;
        });
    }

    private static Reading fromNodes(FromNodes source, Figures figures) {
        Money total = Money.ZERO;
        for (NodeId node : source.nodes()) {
            Money amount = figures.nodes().get(node);
            if (amount == null) {
                return Reading.unavailable("The node " + node + " was not computed in this run");
            }
            total = total.plus(amount);
        }
        return Reading.of(total);
    }

    private static Reading fromTaxBasis(FromTaxBasis source, Figures figures) {
        if (figures.taxAtSlabRates().isEmpty()) {
            return Reading.unavailable("The tax by basis was not computed in this run");
        }
        return Reading.of(source.specialRate()
                .map(rate -> figures.taxAtSpecialRates().getOrDefault(rate, Money.ZERO))
                .orElse(figures.taxAtSlabRates().get()));
    }

    private static Reading fromRules(FromRules source, Figures figures) {
        List<Money> applied = source.rules().stream().map(figures.rules()::get).filter(amount -> amount != null).toList();
        if (applied.isEmpty()) {
            return Reading.unavailable("None of " + source.rules() + " applied in this run");
        }
        return Reading.of(applied.stream().reduce(Money.ZERO, Money::plus));
    }

    private static Reading sumOf(SumOf sum, Map<CellCode, ScheduleCell> cells, Figures figures,
            Map<CellCode, Reading> read) {
        Money total = Money.ZERO;
        for (SumOf.Term term : sum.terms()) {
            ScheduleCell cell = cells.get(term.cell());
            if (cell == null) {
                throw new IllegalStateException("The sum names " + term.cell() + ", which the form does not place;"
                        + " invariant I21 keeps it out of a shipped pack");
            }
            Reading part = reading(cell, cells, figures, read);
            if (part.amount().isEmpty()) {
                return Reading.unavailable("Needs " + term.cell() + ", which is unavailable: "
                        + part.unavailableBecause().orElseThrow());
            }
            total = term.subtract() ? total.minus(part.amount().get()) : total.plus(part.amount().get());
        }
        return Reading.of(total);
    }
}
