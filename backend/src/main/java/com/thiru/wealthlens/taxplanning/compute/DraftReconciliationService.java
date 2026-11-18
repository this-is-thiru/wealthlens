package com.thiru.wealthlens.taxplanning.compute;

import com.thiru.wealthlens.shared.exception.BadRequestException;
import com.thiru.wealthlens.taxengine.api.CertificateLayout;
import com.thiru.wealthlens.taxengine.api.ComputedTax;
import com.thiru.wealthlens.taxengine.api.SalaryCertificateLaw;
import com.thiru.wealthlens.taxengine.api.TaxCalculator;
import com.thiru.wealthlens.taxengine.rules.id.CertificateCode;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxplanning.assemble.EngineInputsAssembler;
import com.thiru.wealthlens.taxplanning.documents.DocumentSwitch;
import com.thiru.wealthlens.taxplanning.documents.assembly.Assembly;
import com.thiru.wealthlens.taxplanning.documents.assembly.EmployerAssembly;
import com.thiru.wealthlens.taxplanning.documents.ingest.ConfirmedCertificate;
import com.thiru.wealthlens.taxplanning.documents.ingest.DocumentType;
import com.thiru.wealthlens.taxplanning.documents.reconcile.Reconciliation;
import com.thiru.wealthlens.taxplanning.documents.store.DocumentSettings;
import com.thiru.wealthlens.taxplanning.documents.store.StoredConfirmation;
import com.thiru.wealthlens.taxplanning.documents.store.StoredConfirmationRepository;
import com.thiru.wealthlens.taxplanning.documents.store.StoredDocument;
import com.thiru.wealthlens.taxplanning.documents.store.StoredDocumentRepository;
import com.thiru.wealthlens.taxplanning.documents.store.StoredParseAttempt;
import com.thiru.wealthlens.taxplanning.documents.store.StoredParseAttemptRepository;
import com.thiru.wealthlens.taxplanning.protection.ReadSubject;
import com.thiru.wealthlens.taxplanning.protection.TaxAccessLog;
import com.thiru.wealthlens.taxplanning.workspace.DraftService;
import com.thiru.wealthlens.taxplanning.workspace.FilingDraft;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A draft's confirmed certificates reconciled with a fresh computation of it (W-131d2, D-765 C1a): the certificates are those
 * of the draft's profile and year whose layout pairs printed figures with the computation's (C2a), each by its latest
 * confirmed reading; the computation is under the draft's regime and stored nowhere, as the forecast's is (C3a). Behind the
 * documents switch; each certificate used is a recorded read (C4a).
 */
@Service
@Transactional
@RequiredArgsConstructor
public class DraftReconciliationService {

    private final DocumentSettings settings;
    private final DraftService draftService;
    private final StoredDocumentRepository documents;
    private final StoredParseAttemptRepository attempts;
    private final StoredConfirmationRepository confirmations;
    private final EngineInputsAssembler assembler;
    private final TaxCalculator taxCalculator;
    private final TaxAccessLog accessLog;
    private final Clock clock;

    /**
     * @throws BadRequestException if the draft is not this account's, or no certificate of its year is confirmed
     */
    public DraftReconciliation reconcile(String email, String draftId) {
        DocumentSwitch.requireEnabled(settings);
        FilingDraft draft = draftService.ownedDraft(email, draftId).draft();
        TaxYearId year = TaxYearId.of(draft.getTaxYearStart());
        List<StoredDocument> certificates = documents.findByProfileId(draft.getProfileId()).stream()
                .filter(document -> document.getTaxYearStart() == draft.getTaxYearStart())
                .filter(document -> comparable(year, document.getDocumentType()).isPresent())
                .sorted(Comparator.comparing(StoredDocument::getUploadedAt).thenComparing(StoredDocument::getId)).toList();
        Map<String, ConfirmedCertificate> confirmed = confirmedReadings(certificates);
        List<ConfirmedCertificate> used = certificates.stream().map(StoredDocument::getId).filter(confirmed::containsKey)
                .map(confirmed::get).toList();
        if (used.isEmpty()) {
            throw new BadRequestException("No certificate of this draft's tax year is confirmed yet: confirm one to reconcile it");
        }
        CertificateLayout layout = onlyForm(certificates, year);
        SalaryCertificateLaw law = taxCalculator.salaryCertificateLaw(year);
        ComputedTax computed = taxCalculator.compute(assembler.assemble(email, draftId,
                DraftForecastService.regimeOf(draft, taxCalculator), LocalDate.now(clock)).inputs());
        used.forEach(certificate -> accessLog.record(email, ReadSubject.DOCUMENT, certificate.documentId()));
        Assembly assembly = EmployerAssembly.assemble(used, layout, law);
        return new DraftReconciliation(draftId, draft.getCurrentRevision(), computed.regime(), assembly,
                Reconciliation.reconcile(used, assembly, layout, computed, law),
                certificates.stream().map(StoredDocument::getId).filter(id -> !confirmed.containsKey(id)).toList());
    }

    /** The year's layout of {@code type} where it pairs printed figures with the computation's (C2a): data, not a form named here. */
    private Optional<CertificateLayout> comparable(TaxYearId year, DocumentType type) {
        return taxCalculator.certificate(year, new CertificateCode(type.name()))
                .filter(layout -> !layout.checks().comparisons().isEmpty());
    }

    /** The one form the year's certificates are reconciled as; a pack pairing two forms with the computation is not read. */
    private CertificateLayout onlyForm(List<StoredDocument> certificates, TaxYearId year) {
        List<DocumentType> forms = certificates.stream().map(StoredDocument::getDocumentType).distinct().toList();
        if (forms.size() != 1) {
            throw new IllegalStateException("The pack for " + year.label() + " pairs " + forms + " with the computation: one form is read");
        }
        return comparable(year, forms.getFirst()).orElseThrow();
    }

    /** Each document's latest reading the person confirmed (C2a): a later reading not yet confirmed does not displace it. */
    private Map<String, ConfirmedCertificate> confirmedReadings(List<StoredDocument> certificates) {
        List<StoredParseAttempt> read = attempts.findByDocumentIdIn(certificates.stream().map(StoredDocument::getId).toList());
        Map<String, StoredConfirmation> byAttempt = confirmations.findAllById(read.stream().map(StoredParseAttempt::getId).toList())
                .stream().collect(Collectors.toMap(StoredConfirmation::getId, Function.identity()));
        return read.stream().filter(attempt -> byAttempt.containsKey(attempt.getId()))
                .collect(Collectors.toMap(StoredParseAttempt::getDocumentId, Function.identity(),
                        BinaryOperator.maxBy(Comparator.comparing(StoredParseAttempt::getStartedAt))))
                .values().stream().collect(Collectors.toMap(StoredParseAttempt::getDocumentId,
                        attempt -> new ConfirmedCertificate(attempt.getDocumentId(), byAttempt.get(attempt.getId()).toRecord())));
    }
}
