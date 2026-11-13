package com.thiru.wealthlens.taxengine.rules.filing;

import com.thiru.wealthlens.taxengine.rules.definition.CapitalGainsPart;
import com.thiru.wealthlens.taxengine.rules.id.CellCode;
import com.thiru.wealthlens.taxengine.rules.id.NodeId;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import java.util.List;
import java.util.Optional;

/**
 * Where a schedule cell's figure comes from (DR 21 I5; W-092 C3a, D-654). Data, not code: a new form that reuses a cell
 * needs no mapping code. A cell the computation cannot produce says so, as {@link ManualEntry}.
 */
public sealed interface FieldSource {

    /**
     * The sum of computed nodes' amounts — one node, or several where the form's figure is a total the engine keeps
     * apart, as gross salary is {@code salary.gross} and the medical-travel perquisite (Q1a, D-654).
     */
    record FromNodes(List<NodeId> nodes) implements FieldSource {
        public FromNodes {
            if (nodes == null || nodes.isEmpty()) {
                throw new IllegalArgumentException("FromNodes names at least one node");
            }
            nodes = List.copyOf(nodes);
        }
    }

    /**
     * Tax before rebate on one basis, as {@code tax.bybasis} keeps it (Q3a, D-654; Q7a, D-655): at the slab rates, at one
     * special rate, at every special rate together, or at every rate — slab and special — together (ITR-1 D1).
     *
     * @param basis which of the three
     * @param specialRate the rate, present exactly for {@link Basis#SPECIAL_RATE}
     */
    record FromTaxBasis(Basis basis, Optional<String> specialRate) implements FieldSource {

        /** The part of the tax by basis a cell reads. */
        public enum Basis { SLAB_RATES, SPECIAL_RATE, ALL_SPECIAL_RATES, ALL_RATES }

        public FromTaxBasis {
            if (basis == null || specialRate == null || specialRate.isPresent() != (basis == Basis.SPECIAL_RATE)) {
                throw new IllegalArgumentException("FromTaxBasis names a special rate exactly when its basis is SPECIAL_RATE");
            }
        }
    }

    /**
     * The sum of the trace's figures for these rules. A list, because one cell can be filled by a different row in
     * each regime — the standard deduction is one row in the new regime and another in the old. Where {@code kind} is
     * present only lines of that step kind are added: a set-off row writes a MEASURE line for the amount allowed and a
     * SET_OFF line for the amount taken, the same figure, so a cell reads one (D-658).
     *
     * @param rules the rows
     * @param kind the step kind of the lines read, such as {@code SET_OFF}; empty for every line
     */
    record FromRules(List<RuleId> rules, Optional<String> kind) implements FieldSource {
        public FromRules {
            if (rules == null || rules.isEmpty() || kind == null) {
                throw new IllegalArgumentException("FromRules names at least one rule, and its kind or none");
            }
            rules = List.copyOf(rules);
        }
    }

    /**
     * The sum of parts of the year's capital gains, as the computed return states them — several where the form groups
     * them by rate, as "long-term chargeable @ 12.5%" holds s.112A's part and s.112's (Q8a, D-655).
     */
    record FromCapitalGainsParts(List<CapitalGainsPart> parts) implements FieldSource {
        public FromCapitalGainsParts {
            if (parts == null || parts.isEmpty()) {
                throw new IllegalArgumentException("FromCapitalGainsParts names at least one part");
            }
            parts = List.copyOf(parts);
        }
    }

    /** The sum of other cells of the same form, each added or, where {@code subtract}, taken away. */
    record SumOf(List<Term> terms) implements FieldSource {
        public SumOf {
            if (terms == null || terms.isEmpty()) {
                throw new IllegalArgumentException("SumOf names at least one cell");
            }
            terms = List.copyOf(terms);
        }

        /** One cell of a sum. */
        public record Term(CellCode cell, boolean subtract) {
            public Term {
                if (cell == null) {
                    throw new IllegalArgumentException("A SumOf term names a cell");
                }
            }
        }
    }

    /**
     * One figure of the item a {@link ScheduleGroup} is repeating over, such as a house property's {@code annualValue}
     * (W-093a C1a, D-658). Read only inside a group (I21).
     */
    record FromItemFigure(String figure) implements FieldSource {
        public FromItemFigure {
            if (figure == null || figure.isBlank()) {
                throw new IllegalArgumentException("FromItemFigure names the item's figure");
            }
        }
    }

    /**
     * The gross income from other sources of these kinds, added together — Schedule OS's rows are by kind, not by item
     * (W-093a, D-658).
     */
    record FromOtherSourceKinds(List<String> kinds) implements FieldSource {
        public FromOtherSourceKinds {
            if (kinds == null || kinds.isEmpty()) {
                throw new IllegalArgumentException("FromOtherSourceKinds names at least one kind");
            }
            kinds = List.copyOf(kinds);
        }
    }

    /**
     * A figure of the capital-gains buckets whose gain went to these parts, added together — Schedule CG's rows are totals
     * by category, each category a part (W-093a Q1a, D-658).
     *
     * @param parts the parts
     * @param figure {@code consideration}, {@code costOfAcquisition}, {@code transferExpenditure} or {@code gain}
     */
    record FromCapitalGainsBuckets(List<CapitalGainsPart> parts, String figure) implements FieldSource {

        /** The kind under which the buckets of a part are handed to the mapper. */
        public static String itemKindOf(CapitalGainsPart part) {
            return "CAPITAL_GAINS_BUCKET " + part.value();
        }

        public FromCapitalGainsBuckets {
            if (parts == null || parts.isEmpty() || figure == null || figure.isBlank()) {
                throw new IllegalArgumentException("FromCapitalGainsBuckets names at least one part and its figure");
            }
            parts = List.copyOf(parts);
        }
    }

    /**
     * A column of set-off, added over rows — Schedules CYLA, BFLA and CFL (W-093a Q4a, Q5a; D-658). A row is a head by
     * its name or a capital-gains part by its code, or one of the loss rows; a row the run does not hold adds nothing.
     *
     * @param rows the rows added together, such as both long-term parts for "long-term capital gain @ 12.5%"
     * @param column the column, such as {@code afterCurrentYear}
     */
    record FromSetOff(List<String> rows, String column) implements FieldSource {
        public FromSetOff {
            if (rows == null || rows.isEmpty() || column == null || column.isBlank()) {
                throw new IllegalArgumentException("FromSetOff names at least one row and its column");
            }
            rows = List.copyOf(rows);
        }
    }

    /** Deliberately not derived: the person enters it, and is told why and what. */
    record ManualEntry(String guidance) implements FieldSource {
        public ManualEntry {
            if (guidance == null || guidance.isBlank()) {
                throw new IllegalArgumentException("A ManualEntry tells the person what to enter and why");
            }
        }
    }
}
