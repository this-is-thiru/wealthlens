package com.thiru.wealthlens.taxplanning.documents;

import com.thiru.wealthlens.shared.exception.BadRequestException;
import com.thiru.wealthlens.taxengine.api.CertificateLayout;
import com.thiru.wealthlens.taxplanning.documents.ingest.ExtractedField;
import com.thiru.wealthlens.taxplanning.documents.ingest.ExtractedValue;
import com.thiru.wealthlens.taxplanning.documents.ingest.ParseAttempt;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The rows a confirmation takes, by path (W-131d1, D-765 C3a): an accepted row as read, a corrected row read from its text by its
 * kind — the kind of the value read, or of the layout's line for a row the parser did not read. A row named that the reading
 * cannot take is refused by name.
 */
final class ConfirmedRows {

    private ConfirmedRows() {
    }

    static Map<String, ExtractedValue> accepted(ParseAttempt attempt, CertificateLayout layout, ConfirmationRequest request) {
        Map<String, ExtractedField> read = attempt.extractedFields().stream()
                .collect(Collectors.toMap(ExtractedField::fieldPath, Function.identity()));
        Map<String, ExtractedValue> accepted = new LinkedHashMap<>();
        for (String path : request.accepted()) {
            ExtractedValue value = Optional.ofNullable(read.get(path)).flatMap(ExtractedField::interpretedValue).orElseThrow(
                    () -> new BadRequestException(path + " was not read, so it cannot be accepted: correct it instead"));
            accepted.put(path, value);
        }
        request.corrected().forEach((path, text) -> {
            if (accepted.containsKey(path)) {
                throw new BadRequestException(path + " is both accepted and corrected");
            }
            accepted.put(path, Corrections.read(path, kindOf(path, read, layout), text));
        });
        for (String path : request.rejected()) {
            if (!read.containsKey(path)) {
                throw new BadRequestException(path + " was not read, so it cannot be rejected");
            }
        }
        return accepted;
    }

    private static String kindOf(String path, Map<String, ExtractedField> read, CertificateLayout layout) {
        Optional<ExtractedValue> value = Optional.ofNullable(read.get(path)).flatMap(ExtractedField::interpretedValue);
        if (value.isPresent()) {
            return Corrections.kindOf(value.get());
        }
        return layout.lines().stream().filter(line -> line.path().equals(path) && line.columns().isEmpty()).findFirst()
                .map(CertificateLayout.PrintedLine::kind)
                .orElseThrow(() -> new BadRequestException(path + " is not a row of " + layout.code() + " that can be written"));
    }
}
