package com.thiru.wealthlens.taxplanning.workspace.importing;

import java.util.List;
import tools.jackson.databind.node.ObjectNode;

/**
 * What the user decided about a previewed salary workbook (W-133b C2a, C4a): a component for each returned row they map, the
 * returned rows they leave out, and the employment the pay lines go on, as a manual entry sends it but without its components.
 *
 * @param mappings each returned row given a component, by row number
 * @param leftOut each returned row the user leaves out, by row number
 * @param employment the employment's own facts; its pay lines are the import's
 */
public record SalaryImportDecisions(List<Mapping> mappings, List<Integer> leftOut, ObjectNode employment) {

    public SalaryImportDecisions {
        mappings = mappings == null ? List.of() : List.copyOf(mappings);
        leftOut = leftOut == null ? List.of() : List.copyOf(leftOut);
        if (employment == null) {
            throw new IllegalArgumentException("The decisions need the employment the pay lines go on");
        }
    }

    /** A returned row mapped to a pay-line component of the draft's year, by its code. */
    public record Mapping(int row, String code) {
    }
}
