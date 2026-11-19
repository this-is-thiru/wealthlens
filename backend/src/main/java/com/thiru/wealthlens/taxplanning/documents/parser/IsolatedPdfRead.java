package com.thiru.wealthlens.taxplanning.documents.parser;

import com.thiru.wealthlens.taxplanning.documents.store.DocumentSettings;
import com.thiru.wealthlens.taxplanning.isolation.ChildReply;
import com.thiru.wealthlens.taxplanning.isolation.IsolatedChild;
import java.time.Duration;
import java.util.List;
import lombok.extern.log4j.Log4j2;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads an uploaded PDF in a child process ({@code DR 14} O6, "Parsing isolation"; W-131f): only {@link PdfReadChild} hands the
 * untrusted bytes to iText, run by {@link IsolatedChild} under its limits. The application trusts nothing from it but a
 * size-capped reply that parses strictly. Every way the child can fail is an {@code UNREADABLE} reading with its reason, so the
 * caller decides what it means; only a child that cannot be started is the application's fault.
 *
 * @param launch the command that starts the child, up to and without the {@link PdfText.Depth}
 */
@Log4j2
public record IsolatedPdfRead(DocumentSettings.Isolation isolation, Duration parseBudget, List<String> launch) {

    private static final JsonMapper STRICT = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES,
                    DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES, DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES)
            .build();

    public IsolatedPdfRead {
        launch = List.copyOf(launch);
    }

    public static IsolatedPdfRead of(DocumentSettings.Isolation isolation, Duration parseBudget) {
        return new IsolatedPdfRead(isolation, parseBudget, IsolatedChild.launching(PdfReadChild.class, isolation));
    }

    /** As {@link IsolatedChild#launching(Class, DocumentSettings.Isolation)}. */
    static List<String> launching(Class<?> main, DocumentSettings.Isolation isolation) {
        return IsolatedChild.launching(main, isolation);
    }

    /** As {@link IsolatedChild#launching(Class, DocumentSettings.Isolation, String)}. */
    static List<String> launching(Class<?> main, DocumentSettings.Isolation isolation, String user) {
        return IsolatedChild.launching(main, isolation, user);
    }

    /** Whether the child runs as {@code nobody}. */
    public boolean handsToNobody() {
        return child().handsToNobody();
    }

    /** The page count, or why there is none, within the upload's budget. */
    public PdfText pages(DocumentContent content) {
        return read(content, PdfText.Depth.PAGES, isolation.uploadReadBudget());
    }

    /** The page count and the printed words, or why there are none, within the parse budget. */
    PdfText words(DocumentContent content) {
        return read(content, PdfText.Depth.WORDS, parseBudget);
    }

    private IsolatedChild child() {
        return new IsolatedChild(isolation, launch);
    }

    private PdfText read(DocumentContent content, PdfText.Depth depth, Duration budget) {
        return switch (child().run(depth.name(), content.bytes(), budget)) {
            case ChildReply.Answered answered -> understood(answered.bytes());
            case ChildReply.Failed failed -> unreadable(failed.reason().replace("The reader", "The PDF reader"));
        };
    }

    private PdfText understood(byte[] reply) {
        try {
            return STRICT.readValue(reply, PdfText.class);
        } catch (JacksonException e) {
            return unreadable("The PDF reader's reply was not understood");
        }
    }

    private static PdfText unreadable(String reason) {
        log.warn("A PDF could not be read in isolation: {}", reason);
        return new PdfText(PdfText.Outcome.UNREADABLE, 0, List.of(), reason);
    }
}
