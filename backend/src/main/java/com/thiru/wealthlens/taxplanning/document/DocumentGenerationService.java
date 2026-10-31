package com.thiru.wealthlens.taxplanning.document;

import com.thiru.wealthlens.taxplanning.recommendation.RestructuringResult;
import com.thiru.wealthlens.taxplanning.salary.dto.KnownErrorsComputation;
import com.thiru.wealthlens.taxplanning.salary.dto.SalaryProfileResponse;
import com.thiru.wealthlens.taxplanning.salary.entity.SalaryProfileEntity;
import com.thiru.wealthlens.taxplanning.salary.entity.TaxComputationEntity;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.openpdf.text.DocumentException;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.xhtmlrenderer.pdf.ITextRenderer;
import org.xhtmlrenderer.util.XRRuntimeException;

@Service
@Log4j2
@RequiredArgsConstructor
public class DocumentGenerationService {

    private final TemplateEngine templateEngine;

    public byte[] generateTaxComparisonReport(
            TaxComputationEntity computation,
            SalaryProfileEntity profile,
            RestructuringResult restructuring
    ) {
        Context context = new Context();
        context.setVariable("profile", SalaryProfileResponse.fromEntity(profile));
        context.setVariable("computation", computation);
        context.setVariable("restructuring", restructuring);
        context.setVariable("generatedOn", LocalDate.now());
        context.setVariable("disclaimer", "This is an indicative computation only. Consult a qualified CA for advice.");
        // The JSON response's notice, above the figures: the report prints the same known-wrong numbers (D-063, D-168)
        context.setVariable("knownErrors", KnownErrorsComputation.NOTICE);
        String html = templateEngine.process("taxplanning/tax-comparison-report", context);
        return convertHtmlToPdf(html);
    }

    // Flying Saucer throws XRRuntimeException for XHTML it cannot parse or lay out; createPDF declares OpenPDF's
    // DocumentException (D-192). Anything else is not a rendering failure and propagates as itself.
    private byte[] convertHtmlToPdf(String html) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            ITextRenderer renderer = new ITextRenderer();
            renderer.setDocumentFromString(html);
            renderer.layout();
            renderer.createPDF(outputStream);
        } catch (XRRuntimeException | DocumentException e) {
            throw new IllegalStateException(
                    "The tax-comparison report could not be rendered as a PDF: " + e.getMessage(), e);
        }
        return outputStream.toByteArray();
    }
}
