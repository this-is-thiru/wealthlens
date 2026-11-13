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
     * Tax before rebate on one basis (Q3a, D-654): at the slab rates where {@code specialRate} is empty, otherwise at
     * that special rate, as {@code tax.bybasis} keeps it.
     */
    record FromTaxBasis(Optional<String> specialRate) implements FieldSource {
        public FromTaxBasis {
            if (specialRate == null) {
                throw new IllegalArgumentException("FromTaxBasis says its special rate, or empty for the slab rates");
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

    /** One part of the year's capital gains, as the computed return states it. */
    record FromCapitalGainsPart(CapitalGainsPart part) implements FieldSource {
        public FromCapitalGainsPart {
            if (part == null) {
                throw new IllegalArgumentException("FromCapitalGainsPart names a part");
            }
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
