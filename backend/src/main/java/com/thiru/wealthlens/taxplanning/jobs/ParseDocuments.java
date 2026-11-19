package com.thiru.wealthlens.taxplanning.jobs;

import com.thiru.wealthlens.taxplanning.documents.ingest.IngestedDocument;
import com.thiru.wealthlens.taxplanning.documents.ingest.ParseAttempt;
import com.thiru.wealthlens.taxplanning.documents.ingest.ParseOutcome;
import com.thiru.wealthlens.taxplanning.documents.ingest.ParserRun;
import com.thiru.wealthlens.taxplanning.documents.parser.DocumentContent;
import com.thiru.wealthlens.taxplanning.documents.parser.DocumentParser;
import com.thiru.wealthlens.taxplanning.documents.parser.DocumentParsers;
import com.thiru.wealthlens.taxplanning.documents.store.DocumentBytes;
import com.thiru.wealthlens.taxplanning.documents.store.DocumentSettings;
import com.thiru.wealthlens.taxplanning.documents.store.StoredDocument;
import com.thiru.wealthlens.taxplanning.documents.store.StoredDocumentRepository;
import com.thiru.wealthlens.taxplanning.documents.store.StoredParseAttempt;
import com.thiru.wealthlens.taxplanning.documents.store.StoredParseAttemptRepository;
import com.thiru.wealthlens.taxplanning.profile.TaxpayerProfileRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

/**
 * Reads each stored document with its form's parser ({@code DR 19} E8 {@code ParseDocument}; W-131c, D-764). One attempt per
 * parser version: a document is due while every attempt at it failed and none was by the current version, so a later parser
 * retries a failure ({@code DR 14} O9) and never re-reads what an earlier one read. A profile held for erasure is not read
 * (W-235). Each parse runs in a thread of its own within {@link DocumentSettings#parseBudget()}; past it, or if the parser
 * throws, the attempt is {@code FAILED} with the reason and the document is kept. The PDF itself is read in a child process
 * killed at the same budget (W-131f), so an overrun leaves nothing reading it.
 */
@Component
@Log4j2
@RequiredArgsConstructor
public class ParseDocuments implements TaxJob {

    private final StoredDocumentRepository documents;
    private final StoredParseAttemptRepository attempts;
    private final DocumentBytes bytes;
    private final DocumentParsers parsers;
    private final TaxpayerProfileRepository profiles;
    private final DocumentSettings settings;

    @Override
    public String name() {
        return "parse-documents";
    }

    @Override
    public Duration timeout() {
        return Duration.ofMinutes(10);
    }

    @Override
    public void run(JobContext context) {
        for (StoredDocument document : due()) {
            if (!context.holdsLease()) {
                log.warn("Job {} lost its lease; stopping", name());
                return;
            }
            parse(document.toRecord(), parsers.byType().get(document.getDocumentType()), context);
        }
    }

    private List<StoredDocument> due() {
        Set<String> held = profiles.idsHeldForErasure();
        List<StoredDocument> candidates = documents.findByDocumentTypeIn(parsers.byType().keySet()).stream()
                .filter(document -> !held.contains(document.getProfileId())).toList();
        Map<String, List<StoredParseAttempt>> byDocument = attempts.findByDocumentIdIn(candidates.stream().map(StoredDocument::getId).toList())
                .stream().collect(Collectors.groupingBy(StoredParseAttempt::getDocumentId));
        return candidates.stream().filter(document -> {
            String current = parsers.byType().get(document.getDocumentType()).version();
            return byDocument.getOrDefault(document.getId(), List.of()).stream()
                    .allMatch(attempt -> attempt.getOutcome() == ParseOutcome.FAILED && !attempt.getParserVersion().equals(current));
        }).toList();
    }

    private void parse(IngestedDocument document, DocumentParser parser, JobContext context) {
        DocumentContent content;
        try {
            content = bytes.read(document.documentId());
        } catch (IllegalStateException e) {
            context.refuse("document " + document.documentId(), e.getMessage());
            return;
        }
        if (!content.sha256().equals(document.sha256())) {
            log.error("Job {}: the stored bytes of document {} are not the document's", name(), document.documentId());
            save(failed(document, parser, context.nowUtc(), "The stored bytes are not this document's", context));
            return;
        }
        save(withinBudget(document, content, parser, context));
    }

    private ParseAttempt withinBudget(IngestedDocument document, DocumentContent content, DocumentParser parser, JobContext context) {
        LocalDateTime startedAt = context.nowUtc();
        FutureTask<ParseAttempt> task = new FutureTask<>(() -> parsers.parse(document, content));
        Thread thread = Thread.ofPlatform().daemon().name("parse-" + document.documentId()).start(task);
        try {
            return task.get(settings.parseBudget().toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            thread.interrupt();
            log.error("Job {}: document {} overran its budget of {}; its thread is abandoned", name(), document.documentId(),
                    settings.parseBudget());
            return failed(document, parser, startedAt, "The parser took longer than " + settings.parseBudget(), context);
        } catch (ExecutionException e) {
            log.error("Job {}: the parser stopped on document {}", name(), document.documentId(), e.getCause());
            return failed(document, parser, startedAt, "The parser stopped: " + e.getCause().getClass().getSimpleName(), context);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Job " + name() + " was interrupted while parsing document " + document.documentId(), e);
        }
    }

    private static ParseAttempt failed(IngestedDocument document, DocumentParser parser, LocalDateTime startedAt, String reason,
            JobContext context) {
        return new ParseAttempt(UUID.randomUUID().toString(), document.documentId(),
                new ParserRun(parser.id(), parser.version(), startedAt, context.nowUtc()), ParseOutcome.FAILED, List.of(), List.of(),
                List.of(reason));
    }

    private void save(ParseAttempt attempt) {
        attempts.save(StoredParseAttempt.of(attempt));
        log.info("Job {} recorded a {} attempt at document {}", name(), attempt.outcome(), attempt.documentId());
    }
}
