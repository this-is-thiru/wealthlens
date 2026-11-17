package com.thiru.wealthlens.taxplanning.documents.parser;

import com.thiru.wealthlens.taxengine.api.TaxCalculator;
import com.thiru.wealthlens.taxengine.rules.id.CertificateCode;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxplanning.documents.ingest.DocumentType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Reads a Form No. 130, the employer's certificate under the Income-tax Rules, 2026, against its year's layout
 * ({@link CertificateReading}; W-126a – W-127b).
 */
@Component
@RequiredArgsConstructor
public class Form130Parser implements DocumentParser {

    private final TaxCalculator taxCalculator;

    @Override
    public DocumentType reads() {
        return DocumentType.FORM_130;
    }

    @Override
    public String id() {
        return "form-130";
    }

    @Override
    public String version() {
        return "4";
    }

    /** @throws IllegalArgumentException where no pack ships for {@code year} — the upload refuses such a year first */
    @Override
    public DocumentReading read(DocumentContent content, TaxYearId year) {
        return CertificateReading.read(content, year, taxCalculator.certificate(year, new CertificateCode(reads().name())),
                "Form No. 130");
    }
}
