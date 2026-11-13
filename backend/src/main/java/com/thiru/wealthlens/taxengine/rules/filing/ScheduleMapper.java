package com.thiru.wealthlens.taxengine.rules.filing;

import com.thiru.wealthlens.taxengine.rules.condition.ReturnFacts;
import com.thiru.wealthlens.taxengine.rules.definition.CapitalGainsPart;
import com.thiru.wealthlens.taxengine.rules.filing.FieldSource.Accrual;
import com.thiru.wealthlens.taxengine.rules.filing.FieldSource.FromCapitalGainsAccrual;
import com.thiru.wealthlens.taxengine.rules.filing.FieldSource.FromCapitalGainsBuckets;
import com.thiru.wealthlens.taxengine.rules.filing.FieldSource.FromCapitalGainsParts;
import com.thiru.wealthlens.taxengine.rules.filing.FieldSource.FromItemFigure;
import com.thiru.wealthlens.taxengine.rules.filing.FieldSource.FromNodes;
import com.thiru.wealthlens.taxengine.rules.filing.FieldSource.FromOtherSourceAccrual;
import com.thiru.wealthlens.taxengine.rules.filing.FieldSource.FromOtherSourceKinds;
import com.thiru.wealthlens.taxengine.rules.filing.FieldSource.FromRules;
import com.thiru.wealthlens.taxengine.rules.filing.FieldSource.FromSetOff;
import com.thiru.wealthlens.taxengine.rules.filing.FieldSource.FromTaxBasis;
import com.thiru.wealthlens.taxengine.rules.filing.FieldSource.ManualEntry;
import com.thiru.wealthlens.taxengine.rules.filing.FieldSource.SumOf;
import com.thiru.wealthlens.taxengine.rules.id.CellCode;
import com.thiru.wealthlens.taxengine.rules.id.NodeId;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.time.LocalDate;
import java.time.MonthDay;
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
     * @param otherSourcesByKind the gross income from other sources, by kind
     * @param items each kind of item's figures, one map per item in input order, such as each house property's
     * @param rulesByKind each rule's figures in the trace, summed by step kind
     * @param setOff set-off row by row, each row's columns by name
     * @param accruals each kind of item's amounts with the day each arose, for the accrual tables (W-323)
     */
    public record Figures(Map<NodeId, Money> nodes, Map<RuleId, Money> rules, ReturnFacts returned,
            Optional<Money> taxAtSlabRates, Map<String, Money> taxAtSpecialRates, Map<String, Money> otherSourcesByKind,
            Map<String, List<Map<String, Money>>> items, Map<RuleId, Map<String, Money>> rulesByKind,
            Map<String, Map<String, Money>> setOff, Accruals accruals) {
        public Figures {
            if (returned == null || taxAtSlabRates == null || accruals == null) {
                throw new IllegalArgumentException("Figures needs the return's facts, says whether slab tax was computed and"
                        + " carries the accruals, none where nothing arose");
            }
            nodes = Map.copyOf(nodes);
            rules = Map.copyOf(rules);
            taxAtSpecialRates = Map.copyOf(taxAtSpecialRates);
            otherSourcesByKind = Map.copyOf(otherSourcesByKind);
            items = Map.copyOf(items);
            rulesByKind = Map.copyOf(rulesByKind);
            setOff = Map.copyOf(setOff);
        }
    }

    /**
     * The amounts that arose in the year, each with its day, by the item kind the cells name — a capital-gains bucket's gain
     * under its part's kind, an item of other-source income's gross under its kind (W-323, D-662).
     *
     * @param yearStartsOn the first day of the financial year, against which a cell's period is read
     * @param byKind each kind's amounts, in input order
     */
    public record Accruals(LocalDate yearStartsOn, Map<String, List<Accrued>> byKind) {
        public Accruals {
            if (yearStartsOn == null || byKind == null) {
                throw new IllegalArgumentException("Accruals needs the year's first day and its amounts, an empty map for none");
            }
            byKind = Map.copyOf(byKind);
        }
    }

    /**
     * One amount and the day it arose.
     *
     * @param amount the amount, a loss negative
     * @param on the day it arose or was received; {@code Optional.empty()} where none was entered
     */
    public record Accrued(Money amount, Optional<LocalDate> on) {
        public Accrued {
            if (amount == null || on == null) {
                throw new IllegalArgumentException("An accrued amount needs its amount, and its day or Optional.empty()");
            }
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

    /**
     * Every cell of the form's schedules, in the form's order: each schedule's fields, then each group once for each
     * item of its kind, its cells read against that item.
     */
    public static List<FilledCell> fill(ItrFormSpec form, Figures figures) {
        Map<CellCode, ScheduleCell> cells = new HashMap<>();
        form.schedules().forEach(schedule -> {
            schedule.fields().forEach(field -> cells.put(field.cell().code(), field.cell()));
            schedule.groups().forEach(group -> group.fields().forEach(field -> cells.put(field.cell().code(), field.cell())));
        });
        Map<CellCode, Reading> read = new HashMap<>();
        List<FilledCell> filled = new ArrayList<>();
        for (ItrSchedule schedule : form.schedules()) {
            for (ScheduleField field : schedule.fields()) {
                filled.add(filledCell(schedule, field.path(), field.cell(),
                        reading(field.cell(), cells, figures, read, Optional.empty())));
            }
            for (ScheduleGroup group : schedule.groups()) {
                List<Map<String, Money>> items = figures.items().getOrDefault(group.item(), List.of());
                for (int number = 1; number <= items.size(); number++) {
                    // each item reads its own group cells, and shares what was read outside the group
                    Map<CellCode, Reading> readForItem = new HashMap<>(read);
                    for (ScheduleField field : group.fields()) {
                        filled.add(filledCell(schedule, group.path() + "[" + number + "]." + field.path(), field.cell(),
                                reading(field.cell(), cells, figures, readForItem, Optional.of(items.get(number - 1)))));
                    }
                }
            }
        }
        return filled;
    }

    private static FilledCell filledCell(ItrSchedule schedule, String path, ScheduleCell cell, Reading reading) {
        return new FilledCell(schedule.code(), path, cell.code(), cell.label(), reading.amount(),
                reading.unavailableBecause());
    }

    private static Reading reading(ScheduleCell cell, Map<CellCode, ScheduleCell> cells, Figures figures,
            Map<CellCode, Reading> read, Optional<Map<String, Money>> item) {
        Reading known = read.get(cell.code());
        if (known != null) {
            return known;
        }
        Reading reading = signed(sourced(cell, cells, figures, read, item), cell.sign());
        read.put(cell.code(), reading);
        return reading;
    }

    /** The cell's figure as its source states it, before the sign the form shows it with. */
    private static Reading sourced(ScheduleCell cell, Map<CellCode, ScheduleCell> cells, Figures figures,
            Map<CellCode, Reading> read, Optional<Map<String, Money>> item) {
        return switch (cell.source()) {
            case FromNodes nodes -> fromNodes(nodes, figures);
            case FromTaxBasis basis -> fromTaxBasis(basis, figures);
            case FromRules rules -> fromRules(rules, figures);
            case FromCapitalGainsParts parts -> Reading.of(parts.parts().stream()
                    .map(part -> figures.returned().capitalGains().getOrDefault(part, Money.ZERO))
                    .reduce(Money.ZERO, Money::plus));
            case SumOf sum -> sumOf(sum, cells, figures, read, item);
            case FromItemFigure figure -> fromItemFigure(figure, item);
            case FromCapitalGainsBuckets buckets -> fromBuckets(buckets, figures);
            case FromSetOff setOff -> fromSetOff(setOff, figures);
            case FromOtherSourceKinds kinds -> Reading.of(kinds.kinds().stream()
                    .map(kind -> figures.otherSourcesByKind().getOrDefault(kind, Money.ZERO))
                    .reduce(Money.ZERO, Money::plus));
            case Accrual accrual -> fromAccrual(accrual, figures);
            case ManualEntry manual -> Reading.unavailable(manual.guidance());
        };
    }

    /** One figure of the item a group repeats over. */
    private static Reading fromItemFigure(FromItemFigure figure, Optional<Map<String, Money>> item) {
        return item.flatMap(values -> Optional.ofNullable(values.get(figure.figure()))).map(Reading::of)
                .orElseGet(() -> Reading.unavailable(item.isEmpty()
                        ? "The item figure " + figure.figure() + " is read outside a group"
                        : "The item has no figure " + figure.figure()));
    }

    /** An accrual table's cell: the amounts that arose in its period, where every one can be placed in a period. */
    private static Reading fromAccrual(Accrual accrual, Figures figures) {
        Accruals accruals = figures.accruals();
        List<String> kinds = switch (accrual) {
            case FromCapitalGainsAccrual gains -> gains.parts().stream().map(FromCapitalGainsBuckets::itemKindOf).toList();
            case FromOtherSourceAccrual items -> items.kinds();
        };
        List<Accrued> amounts = kinds.stream().flatMap(kind -> accruals.byKind().getOrDefault(kind, List.of()).stream())
                .toList();
        Optional<String> unplaced = unplaced(accrual, amounts, figures);
        if (unplaced.isPresent()) {
            return Reading.unavailable(unplaced.get());
        }
        LocalDate from = dayOfYear(accruals.yearStartsOn(), accrual.period().from());
        LocalDate to = dayOfYear(accruals.yearStartsOn(), accrual.period().to());
        return Reading.of(amounts.stream().filter(amount -> !amount.on().get().isBefore(from) && !amount.on().get().isAfter(to))
                .map(Accrued::amount).reduce(Money.ZERO, Money::plus));
    }

    /** Why the amounts cannot be split by period, where they cannot. */
    private static Optional<String> unplaced(Accrual accrual, List<Accrued> amounts, Figures figures) {
        LocalDate start = figures.accruals().yearStartsOn();
        LocalDate end = start.plusYears(1).minusDays(1);
        if (amounts.stream().anyMatch(amount -> amount.on().isEmpty())) {
            return Optional.of("An item this row holds was entered without the date it was received; enter the split by"
                    + " period from your records");
        }
        if (amounts.stream().anyMatch(amount -> amount.on().get().isBefore(start) || amount.on().get().isAfter(end))) {
            return Optional.of("An item this row holds is dated outside the year; enter the split by period from your records");
        }
        if (accrual instanceof FromCapitalGainsAccrual gains) {
            Money arose = amounts.stream().map(Accrued::amount).reduce(Money.ZERO, Money::plus);
            Money settled = fromSetOff(new FromSetOff(gains.parts().stream().map(CapitalGainsPart::value).toList(),
                    gains.settledColumn()), figures).amount().orElse(arose);
            if (amounts.stream().anyMatch(amount -> amount.amount().isLessThan(Money.ZERO)) || arose.compareTo(settled) != 0) {
                return Optional.of("A loss was set off against these gains, and the form does not say in which period it"
                        + " falls; split the set-off figure by period from your records");
            }
        }
        return Optional.empty();
    }

    /** The day of the financial year beginning {@code yearStartsOn} that falls on {@code day}. */
    private static LocalDate dayOfYear(LocalDate yearStartsOn, MonthDay day) {
        LocalDate date = day.atYear(yearStartsOn.getYear());
        return date.isBefore(yearStartsOn) ? day.atYear(yearStartsOn.getYear() + 1) : date;
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

    private static Reading fromSetOff(FromSetOff source, Figures figures) {
        Money total = Money.ZERO;
        for (String row : source.rows()) {
            Map<String, Money> columns = figures.setOff().get(row);
            if (columns == null) {
                continue;
            }
            Money amount = columns.get(source.column());
            if (amount == null) {
                return Reading.unavailable("The set-off row " + row + " has no column " + source.column());
            }
            total = total.plus(amount);
        }
        return Reading.of(total);
    }

    private static Reading fromBuckets(FromCapitalGainsBuckets source, Figures figures) {
        Money total = Money.ZERO;
        for (CapitalGainsPart part : source.parts()) {
            for (Map<String, Money> bucket : figures.items().getOrDefault(FromCapitalGainsBuckets.itemKindOf(part), List.of())) {
                Money amount = bucket.get(source.figure());
                if (amount == null) {
                    return Reading.unavailable("A capital-gains bucket has no figure " + source.figure());
                }
                total = total.plus(amount);
            }
        }
        return Reading.of(total);
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
        return Reading.of(switch (source.basis()) {
            case SLAB_RATES -> figures.taxAtSlabRates().get();
            case SPECIAL_RATE -> figures.taxAtSpecialRates().getOrDefault(source.specialRate().orElseThrow(), Money.ZERO);
            case ALL_SPECIAL_RATES -> figures.taxAtSpecialRates().values().stream().reduce(Money.ZERO, Money::plus);
            case ALL_RATES -> figures.taxAtSpecialRates().values().stream().reduce(figures.taxAtSlabRates().get(), Money::plus);
        });
    }

    private static Reading fromRules(FromRules source, Figures figures) {
        List<Money> applied = source.rules().stream()
                .map(rule -> source.kind().isPresent()
                        ? figures.rulesByKind().getOrDefault(rule, Map.of()).get(source.kind().get())
                        : figures.rules().get(rule))
                .filter(amount -> amount != null).toList();
        if (applied.isEmpty()) {
            return Reading.unavailable("None of " + source.rules() + source.kind().map(kind -> " (" + kind + " lines)").orElse("")
                    + " applied in this run");
        }
        return Reading.of(applied.stream().reduce(Money.ZERO, Money::plus));
    }

    private static Reading sumOf(SumOf sum, Map<CellCode, ScheduleCell> cells, Figures figures,
            Map<CellCode, Reading> read, Optional<Map<String, Money>> item) {
        Money total = Money.ZERO;
        for (SumOf.Term term : sum.terms()) {
            ScheduleCell cell = cells.get(term.cell());
            if (cell == null) {
                throw new IllegalStateException("The sum names " + term.cell() + ", which the form does not place;"
                        + " invariant I21 keeps it out of a shipped pack");
            }
            Reading part = reading(cell, cells, figures, read, item);
            if (part.amount().isEmpty()) {
                return Reading.unavailable("Needs " + term.cell() + ", which is unavailable: "
                        + part.unavailableBecause().orElseThrow());
            }
            total = term.subtract() ? total.minus(part.amount().get()) : total.plus(part.amount().get());
        }
        return Reading.of(total);
    }
}
