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
     * each regime — the standard deduction is one row in the new regime and another in the old.
     */
    record FromRules(List<RuleId> rules) implements FieldSource {
        public FromRules {
            if (rules == null || rules.isEmpty()) {
                throw new IllegalArgumentException("FromRules names at least one rule");
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

    /** Deliberately not derived: the person enters it, and is told why and what. */
    record ManualEntry(String guidance) implements FieldSource {
        public ManualEntry {
            if (guidance == null || guidance.isBlank()) {
                throw new IllegalArgumentException("A ManualEntry tells the person what to enter and why");
            }
        }
    }
}
