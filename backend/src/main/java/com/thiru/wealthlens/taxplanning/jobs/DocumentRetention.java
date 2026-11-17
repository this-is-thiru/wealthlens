package com.thiru.wealthlens.taxplanning.jobs;

import com.thiru.wealthlens.taxengine.api.TaxCalculator;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxplanning.documents.store.DocumentBytes;
import com.thiru.wealthlens.taxplanning.documents.store.DocumentRemoval;
import com.thiru.wealthlens.taxplanning.documents.store.DocumentSettings;
import com.thiru.wealthlens.taxplanning.documents.store.StoredDocument;
import com.thiru.wealthlens.taxplanning.documents.store.StoredDocumentRepository;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

/**
 * Deletes a tax year's documents, with what was read from them, once the last tax year its own year's law lets a loss be carried
 * into has ended — the span filed drafts are kept for, read from the rule pack ({@code DR 14} O5: "Uploaded documents | 8 years,
 * same reason"; W-131a, D-761 C5a). A year whose pack says nothing is refused and kept. Last, stored bytes no document names —
 * left by an upload or a removal cut off part-way — are deleted once {@link DocumentSettings#orphanAfter()} old (W-131b, D-762 C5a).
 */
@Component
@Log4j2
@RequiredArgsConstructor
class DocumentRetention {

    private final StoredDocumentRepository documents;
    private final DocumentRemoval removal;
    private final TaxCalculator taxCalculator;
    private final MongoTemplate mongoTemplate;
    private final DocumentBytes bytes;
    private final DocumentSettings settings;

    /** False once the lease is lost, so the job stops. */
    boolean purge(String job, JobContext context) {
        LocalDate today = context.nowUtc().toLocalDate();
        List<Integer> years = mongoTemplate.findDistinct("tax_year", StoredDocument.class, Integer.class);
        for (int year : years) {
            TaxYearId taxYear = TaxYearId.of(year);
            OptionalInt span = taxCalculator.longestCarryForward(taxYear);
            if (span.isEmpty()) {
                context.refuse("documents of tax year " + taxYear.label(), "no shipped pack carries a loss forward for it, so how long "
                        + "its documents must be kept cannot be derived; kept");
            } else if (today.isAfter(TaxYearId.of(year + span.getAsInt()).endsOn())) {
                if (!context.holdsLease()) {
                    log.warn("Job {} lost its lease; stopping", job);
                    return false;
                }
                List<StoredDocument> expired = documents.findByTaxYearStart(year);
                removal.removeAll(expired);
                log.info("Job {} deleted {} document(s) of tax year {}", job, expired.size(), taxYear.label());
            }
        }
        removeOrphans(job, context);
        return true;
    }

    private void removeOrphans(String job, JobContext context) {
        List<String> old = bytes.storedBefore(context.nowUtc().minus(settings.orphanAfter()).toInstant(ZoneOffset.UTC));
        Set<String> named = new HashSet<>();
        documents.findAllById(old).forEach(document -> named.add(document.getId()));
        List<String> orphans = old.stream().filter(id -> !named.contains(id)).toList();
        bytes.remove(orphans);
        if (!orphans.isEmpty()) {
            log.info("Job {} deleted the bytes of {} document(s) no longer stored", job, orphans.size());
        }
    }
}
