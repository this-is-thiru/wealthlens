package com.thiru.wealthlens.taxplanning.documents.ingest;

import com.thiru.wealthlens.taxengine.rules.id.ComponentCode;
import java.util.Optional;

/**
 * One value read from a document ({@code DR 05} M4). A value inferred from the form's own arithmetic has no text and no place
 * on the page ({@code DR 06} G5.1 L3); a text the parser could not interpret has no value — never zero ({@code D-NOSILENT}).
 *
 * @param fieldPath the row, e.g. {@code partC.annexureI.row2b}
 * @param rawText exactly as it appeared
 * @param confidence from 0 to 1
 * @param mappedTo the salary component the row feeds, where one does
 */
public record ExtractedField(String fieldPath, Optional<String> rawText, Optional<ExtractedValue> interpretedValue,
        double confidence, Optional<FieldLocation> location, Optional<ComponentCode> mappedTo) {

    public ExtractedField {
        Checks.text(fieldPath, "A field read needs its path");
        Checks.present(rawText, "A field's text is present or empty, never null");
        Checks.present(interpretedValue, "A field's value is present or empty, never null");
        Checks.present(location, "A field's location is present or empty, never null");
        Checks.present(mappedTo, "A field's component is present or empty, never null");
        if (!(confidence >= 0 && confidence <= 1)) {
            throw new IllegalArgumentException("A confidence is from 0 to 1");
        }
    }
}
