package com.thiru.wealthlens.taxplanning.documents;

import java.util.List;
import java.util.Map;

/**
 * A person's confirmation of one reading (W-131d1, D-765 C1a, C2a): the rows taken as read, the rows written anew as text, and
 * the rows left out. A list or map left out of the body is empty.
 *
 * @param corrected by row, each value written as {@link ParseView.Field#value()} shows it
 */
public record ConfirmationRequest(String attemptId, List<String> accepted, Map<String, String> corrected, List<String> rejected) {

    public ConfirmationRequest {
        accepted = accepted == null ? List.of() : List.copyOf(accepted);
        corrected = corrected == null ? Map.of() : Map.copyOf(corrected);
        rejected = rejected == null ? List.of() : List.copyOf(rejected);
    }
}
