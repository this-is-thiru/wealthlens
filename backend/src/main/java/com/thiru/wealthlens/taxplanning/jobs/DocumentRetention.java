package com.thiru.wealthlens.taxplanning.jobs;

import com.thiru.wealthlens.taxengine.api.TaxCalculator;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxplanning.documents.store.DocumentRemoval;
import com.thiru.wealthlens.taxplanning.documents.store.StoredDocument;
import com.thiru.wealthlens.taxplanning.documents.store.StoredDocumentRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.OptionalInt;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

/**
 * Deletes a tax year's documents, with what was read from them, once the last tax year its own year's law lets a loss be carried
 * into has ended — the span filed drafts are kept for, read from the rule pack ({@code DR 14} O5: "Uploaded documents | 8 years,
 * same reason"; W-131a, D-761 C5a). A year whose pack says nothing is refused and kept.
 */
@Component
@Log4j2
@RequiredArgsConstructor
class DocumentRetention {

    private final StoredDocumentRepository documents;
    private final DocumentRemoval removal;
    private final TaxCalculator taxCalculator;
    private final MongoTemplate mongoTemplate;

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
        return true;
    }
}
