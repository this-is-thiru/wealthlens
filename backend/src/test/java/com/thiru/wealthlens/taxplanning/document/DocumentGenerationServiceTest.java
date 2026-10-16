package com.thiru.wealthlens.taxplanning.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.parser.PdfTextExtractor;
import com.thiru.wealthlens.taxplanning.enums.RegimeType;
import com.thiru.wealthlens.taxplanning.salary.dto.KnownErrorsComputation;
import com.thiru.wealthlens.taxplanning.salary.entity.SalaryComponentEntity;
import com.thiru.wealthlens.taxplanning.salary.entity.SalaryProfileEntity;
import com.thiru.wealthlens.taxplanning.salary.entity.TaxComputationEntity;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.templateresolver.StringTemplateResolver;
import org.xhtmlrenderer.util.XRRuntimeException;

/**
 * The tax-report PDF carries the old calculator's known-errors notice before any figure (D-063, D-168): the report
 * prints the same known-wrong numbers as the JSON endpoint, which carries the same notice.
 */
class DocumentGenerationServiceTest {

    private static SpringTemplateEngine templateEngine() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }

    private static TaxComputationEntity.TaxResult result(long tax) {
        TaxComputationEntity.TaxResult result = new TaxComputationEntity.TaxResult();
        result.setGrossSalary(1_200_000L);
        result.setTotalExemptions(0L);
        result.setTotalDeductions(75_000L);
        result.setTaxableIncome(1_125_000L);
        result.setBasicTaxBeforeRebate(tax);
        result.setRebate87aApplied(0L);
        result.setTaxAfterRebate(tax);
        result.setSurcharge(0L);
        result.setCess(tax / 25);
        result.setTotalTax(tax + tax / 25);
        result.setNetTakeHome(1_200_000L - tax - tax / 25);
        return result;
    }

    private static TaxComputationEntity computation() {
        TaxComputationEntity computation = new TaxComputationEntity();
        computation.setNewRegimeResult(result(50_000L));
        computation.setOldRegimeResult(result(100_000L));
        computation.setRecommendedRegime(RegimeType.NEW_REGIME);
        computation.setAnnualSavingVsCurrent(0L);
        return computation;
    }

    private static SalaryProfileEntity profile() {
        SalaryProfileEntity profile = new SalaryProfileEntity();
        profile.setEmail("someone@example.com");
        profile.setProfileName("Fixture");
        profile.setEmployerName("Fixture employer");
        profile.setTaxYear("2026-27");
        profile.setCityName("Mumbai");
        profile.setRegimeType(RegimeType.NEW_REGIME);
        SalaryComponentEntity basic = new SalaryComponentEntity();
        basic.setAllowanceCode("BASIC");
        basic.setAnnualAmount(1_200_000L);
        profile.setComponents(List.of(basic));
        return profile;
    }

    private static String firstPageText(byte[] pdf) throws IOException {
        PdfReader reader = new PdfReader(pdf);
        try {
            return PdfTextExtractor.getTextFromPage(reader, 1).replaceAll("\\s+", " ");
        } finally {
            reader.close();
        }
    }

    @Test
    void generateTaxComparisonReport_whenRendered_printsTheKnownErrorsNoticeBeforeAnyFigure() throws IOException {
        // Given
        DocumentGenerationService service = new DocumentGenerationService(templateEngine());

        // When
        byte[] pdf = service.generateTaxComparisonReport(computation(), profile(), null);

        // Then — the JSON endpoint's own notice, ahead of the employee details and their first figure
        String text = firstPageText(pdf);
        String opening = KnownErrorsComputation.NOTICE.substring(0, KnownErrorsComputation.NOTICE.indexOf(" its results"));
        assertThat(text).contains(opening).contains("Do not rely on these figures.");
        assertThat(text.indexOf(opening)).isLessThan(text.indexOf("Employee Details"));
        assertThat(text.indexOf(opening)).isLessThan(text.indexOf("Annual CTC"));
    }

    @Test
    void generateTaxComparisonReport_whenTheTemplateIsNotXhtml_reportsTheRenderingFailure() {
        // Given — a string resolver renders the template name itself, which is not XML, so the PDF renderer must fail
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(new StringTemplateResolver());
        DocumentGenerationService service = new DocumentGenerationService(engine);

        // When
        Throwable failure = catchThrowable(() -> service.generateTaxComparisonReport(computation(), profile(), null));

        // Then — the failure names the report and keeps the renderer's own reason, rather than a bare "failed"
        assertThat(failure)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("tax-comparison report could not be rendered as a PDF")
                .hasMessageContaining("Content is not allowed in prolog")
                .hasCauseInstanceOf(XRRuntimeException.class);
    }
}
