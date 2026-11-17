package com.thiru.wealthlens.taxplanning.documents.parser;

import com.thiru.wealthlens.taxengine.api.TaxCalculator;
import com.thiru.wealthlens.taxengine.rules.id.CertificateCode;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxplanning.documents.ingest.DocumentType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Reads a Form 16, the employer's certificate under the Income-tax Rules, 1962, for TY 2025-26, against that year's layout
 * ({@link CertificateReading}; W-126e1, W-126e2). Part A's header boxes are not in the layout yet (W-126e3), so a
 * certificate that prints them reads PARTIAL.
 */
@Component
@RequiredArgsConstructor
public class Form16Parser implements DocumentParser {

    private final TaxCalculator taxCalculator;

    @Override
    public DocumentType reads() {
        return DocumentType.FORM_16;
    }

    @Override
    public String id() {
        return "form-16";
    }

    @Override
    public String version() {
        return "1";
    }

    /** @throws IllegalArgumentException where no pack ships for {@code year} — the upload refuses such a year first */
    @Override
    public DocumentReading read(DocumentContent content, TaxYearId year) {
        return CertificateReading.read(content, year, taxCalculator.certificate(year, new CertificateCode(reads().name())),
                "Form 16");
    }
}
