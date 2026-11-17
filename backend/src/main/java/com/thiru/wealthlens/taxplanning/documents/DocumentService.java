package com.thiru.wealthlens.taxplanning.documents;

import com.thiru.wealthlens.shared.exception.BadRequestException;
import com.thiru.wealthlens.taxengine.api.CertificateLayout;
import com.thiru.wealthlens.taxengine.api.TaxCalculator;
import com.thiru.wealthlens.taxengine.rules.id.CertificateCode;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxplanning.documents.ingest.DocumentConfirmation;
import com.thiru.wealthlens.taxplanning.documents.ingest.DocumentType;
import com.thiru.wealthlens.taxplanning.documents.ingest.ExtractedField;
import com.thiru.wealthlens.taxplanning.documents.ingest.ExtractedValue;
import com.thiru.wealthlens.taxplanning.documents.ingest.IngestedDocument;
import com.thiru.wealthlens.taxplanning.documents.ingest.ParseAttempt;
import com.thiru.wealthlens.taxplanning.documents.ingest.ParseOutcome;
import com.thiru.wealthlens.taxplanning.documents.parser.DocumentContent;
import com.thiru.wealthlens.taxplanning.documents.parser.DocumentParsers;
import com.thiru.wealthlens.taxplanning.documents.store.DocumentBytes;
import com.thiru.wealthlens.taxplanning.documents.store.DocumentSettings;
import com.thiru.wealthlens.taxplanning.documents.store.StoredConfirmation;
import com.thiru.wealthlens.taxplanning.documents.store.StoredConfirmationRepository;
import com.thiru.wealthlens.taxplanning.documents.store.StoredDocument;
import com.thiru.wealthlens.taxplanning.documents.store.StoredDocumentRepository;
import com.thiru.wealthlens.taxplanning.documents.store.StoredParseAttempt;
import com.thiru.wealthlens.taxplanning.documents.store.StoredParseAttemptRepository;
import com.thiru.wealthlens.taxplanning.jobs.ParseRequests;
import com.thiru.wealthlens.taxplanning.profile.TaxProfileService;
import com.thiru.wealthlens.taxplanning.profile.TaxpayerProfile;
import com.thiru.wealthlens.taxplanning.protection.ReadSubject;
import com.thiru.wealthlens.taxplanning.protection.TaxAccessLog;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * Upload, reading and confirmation of a certificate (TS T8; W-131d1, D-765). Every entry checks the switch first (C2a) and the
 * owner second: a document of another account or of a held profile is refused in the words an absent one is. An upload is
 * accepted for a form some parser reads and a year whose pack has its layout (C4a), and is read in the background (W-131c). A
 * confirmation is refused unless the employee PAN it carries is the profile's (B9-73, C3a).
 */
@Service
@Log4j2
@RequiredArgsConstructor
public class DocumentService {

    private static final String NO_SUCH_DOCUMENT = "This account holds no such document";
    private static final String EMPLOYEE_PAN = "EMPLOYEE_PAN";

    private final DocumentSettings settings;
    private final TaxProfileService profiles;
    private final StoredDocumentRepository documents;
    private final StoredParseAttemptRepository attempts;
    private final StoredConfirmationRepository confirmations;
    private final DocumentBytes bytes;
    private final DocumentParsers parsers;
    private final TaxCalculator taxCalculator;
    private final TaxAccessLog accessLog;
    private final ParseRequests parseRequests;
    private final Clock clock;

    public DocumentView upload(String email, String profileId, int taxYear, DocumentType type, byte[] file) {
        DocumentSwitch.requireEnabled(settings);
        profiles.owned(email, profileId);
        TaxYearId year = TaxYearId.of(taxYear);
        if (!parsers.byType().containsKey(type)) {
            throw new BadRequestException("No parser reads " + type + " yet");
        }
        layout(year, type).orElseThrow(() -> new BadRequestException(type + " has no layout for tax year " + year.label()));
        DocumentContent content = new DocumentContent(file);
        Optional<StoredDocument> stored = documents.findByProfileIdAndSha256(profileId, content.sha256());
        if (stored.isPresent()) {
            return DocumentView.of(stored.get().toRecord(), false);
        }
        String documentId = UUID.randomUUID().toString();
        int pages = bytes.store(documentId, content);
        IngestedDocument document = new IngestedDocument(documentId, profileId, year, type, content.sha256(), now(), pages);
        try {
            documents.save(StoredDocument.of(document));
        } catch (DuplicateKeyException sameBytesAtOnce) {
            return DocumentView.of(documents.findByProfileIdAndSha256(profileId, content.sha256()).orElseThrow().toRecord(), false);
        }
        parseRequests.request();
        log.info("Stored document {} of {} pages", documentId, pages);
        return DocumentView.of(document, true);
    }

    public ParseView parse(String email, String documentId) {
        DocumentSwitch.requireEnabled(settings);
        StoredDocument document = owned(email, documentId);
        accessLog.record(email, ReadSubject.DOCUMENT, documentId);
        return view(document, profiles.owned(email, document.getProfileId()));
    }

    public ParseView confirm(String email, String documentId, ConfirmationRequest request) {
        DocumentSwitch.requireEnabled(settings);
        StoredDocument document = owned(email, documentId);
        TaxpayerProfile profile = profiles.owned(email, document.getProfileId());
        ParseAttempt attempt = latest(documentId).filter(read -> read.attemptId().equals(request.attemptId()))
                .orElseThrow(() -> new BadRequestException("Only the latest reading of this document can be confirmed"));
        if (attempt.outcome() == ParseOutcome.FAILED) {
            throw new BadRequestException("A reading that failed has nothing to confirm");
        }
        if (confirmations.existsById(attempt.attemptId())) {
            throw new BadRequestException("This reading is already confirmed");
        }
        CertificateLayout layout = layout(document.toRecord().taxYear(), document.getDocumentType()).orElseThrow();
        java.util.Map<String, ExtractedValue> accepted = ConfirmedRows.accepted(attempt, layout, request);
        if (panCheck(accepted.get(panPath(layout)), profile) != ParseView.PanCheck.MATCHES) {
            throw new BadRequestException(accepted.containsKey(panPath(layout))
                    ? "This certificate is for another PAN than the profile's"
                    : "The certificate's employee PAN (Part A item 11) is not read: correct it to confirm");
        }
        confirmations.save(StoredConfirmation.of(new DocumentConfirmation(attempt.attemptId(), email, now(), accepted,
                java.util.Set.copyOf(request.rejected()))));
        log.info("Confirmed reading {} of document {}", attempt.attemptId(), documentId);
        return view(document, profile);
    }

    private ParseView view(StoredDocument document, TaxpayerProfile profile) {
        Optional<ParseAttempt> attempt = latest(document.getId());
        if (attempt.isEmpty()) {
            return new ParseView(document.getId(), ParseView.State.WAITING, Optional.empty(), ParseView.PanCheck.NOT_READ, false);
        }
        String panPath = panPath(layout(document.toRecord().taxYear(), document.getDocumentType()).orElseThrow());
        Optional<ExtractedValue> pan = attempt.get().extractedFields().stream().filter(field -> field.fieldPath().equals(panPath))
                .findFirst().flatMap(ExtractedField::interpretedValue);
        return new ParseView(document.getId(), ParseView.State.READ, Optional.of(ParseView.Reading.of(attempt.get())),
                panCheck(pan.orElse(null), profile), confirmations.existsById(attempt.get().attemptId()));
    }

    private StoredDocument owned(String email, String documentId) {
        StoredDocument document = documents.findById(documentId).orElseThrow(() -> new BadRequestException(NO_SUCH_DOCUMENT));
        try {
            profiles.owned(email, document.getProfileId());
        } catch (BadRequestException notTheCallers) {
            throw new BadRequestException(NO_SUCH_DOCUMENT);
        }
        return document;
    }

    private Optional<ParseAttempt> latest(String documentId) {
        return attempts.findByDocumentIdIn(List.of(documentId)).stream().max(Comparator.comparing(StoredParseAttempt::getStartedAt))
                .map(StoredParseAttempt::toRecord);
    }

    private Optional<CertificateLayout> layout(TaxYearId year, DocumentType type) {
        return taxCalculator.certificate(year, new CertificateCode(type.name()));
    }

    private static String panPath(CertificateLayout layout) {
        return layout.lines().stream().filter(line -> line.fact().filter(EMPLOYEE_PAN::equals).isPresent()).findFirst()
                .orElseThrow(() -> new IllegalStateException(layout.code() + " names no " + EMPLOYEE_PAN + " line")).path();
    }

    private static ParseView.PanCheck panCheck(ExtractedValue pan, TaxpayerProfile profile) {
        if (!(pan instanceof ExtractedValue.TextRead words)) {
            return ParseView.PanCheck.NOT_READ;
        }
        return normal(words.text()).equals(normal(profile.getPan())) ? ParseView.PanCheck.MATCHES : ParseView.PanCheck.DIFFERS;
    }

    private static String normal(String pan) {
        return pan == null ? "" : pan.trim().toUpperCase(Locale.ROOT);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock.withZone(ZoneOffset.UTC));
    }
}
