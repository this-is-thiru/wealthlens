package com.thiru.wealthlens.taxplanning.document;

import com.thiru.wealthlens.taxplanning.recommendation.RestructuringResult;
import com.thiru.wealthlens.taxplanning.salary.dto.SalaryProfileResponse;
import com.thiru.wealthlens.taxplanning.salary.entity.SalaryProfileEntity;
import com.thiru.wealthlens.taxplanning.salary.entity.TaxComputationEntity;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.xhtmlrenderer.pdf.ITextRenderer;

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
        String html = templateEngine.process("taxplanning/tax-comparison-report", context);
        return convertHtmlToPdf(html);
    }

    private byte[] convertHtmlToPdf(String html) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            ITextRenderer renderer = new ITextRenderer();
            renderer.setDocumentFromString(html);
            renderer.layout();
            renderer.createPDF(outputStream);
            return outputStream.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate PDF", e);
        }
    }
}
