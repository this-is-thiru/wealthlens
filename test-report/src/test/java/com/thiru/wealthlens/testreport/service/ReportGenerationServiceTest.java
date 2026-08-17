package com.thiru.wealthlens.testreport.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.testreport.dto.ClassStatusCount;
import com.thiru.wealthlens.testreport.dto.ExecutionSummary;
import com.thiru.wealthlens.testreport.dto.TestCaseResult;
import com.thiru.wealthlens.testreport.dto.TestClassSummary;
import com.thiru.wealthlens.testreport.dto.TestStatus;
import com.thiru.wealthlens.testreport.template.ThymeleafTemplateEngineFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.thymeleaf.TemplateEngine;

class ReportGenerationServiceTest {

    @TempDir
    Path tempDir;

    private ReportGenerationService createService() {
        TemplateEngine engine = ThymeleafTemplateEngineFactory.createEngine();
        return new ReportGenerationService(engine);
    }

    private ExecutionSummary buildSampleSummary() {
        TestCaseResult passed = TestCaseResult.builder()
                .name("testBuyStock")
                .className("PortfolioServiceTest")
                .timeSeconds(0.1)
                .status(TestStatus.PASSED)
                .build();

        TestCaseResult failed = TestCaseResult.builder()
                .name("testSellStock")
                .className("PortfolioServiceTest")
                .timeSeconds(0.2)
                .status(TestStatus.FAILED)
                .failureMessage("Not enough stocks")
                .failureType("java.lang.AssertionError")
                .failureDetail("PortfolioServiceTest.testSellStock line 45")
                .build();

        TestCaseResult skipped = TestCaseResult.builder()
                .name("testCancelledOrder")
                .className("PortfolioServiceTest")
                .timeSeconds(0.05)
                .status(TestStatus.SKIPPED)
                .build();

        TestCaseResult passed2 = TestCaseResult.builder()
                .name("testGetPortfolio")
                .className("PortfolioServiceTest")
                .timeSeconds(0.137)
                .status(TestStatus.PASSED)
                .build();

        TestClassSummary classSummary = TestClassSummary.builder()
                .fullyQualifiedClassName("PortfolioServiceTest")
                .packageName("")
                .simpleClassName("PortfolioServiceTest")
                .totalTests(4)
                .passedCount(2)
                .failedCount(1)
                .errorCount(0)
                .skippedCount(1)
                .totalTimeSeconds(0.487)
                .testCases(List.of(passed, failed, skipped, passed2))
                .build();

        return ExecutionSummary.builder()
                .reportTitle("Consolidated Test Report")
                .generatedAt(LocalDateTime.of(2024, 1, 1, 12, 0, 0))
                .totalSuites(1)
                .totalTests(4)
                .totalPassed(2)
                .totalFailed(1)
                .totalErrors(0)
                .totalSkipped(1)
                .totalTimeSeconds(0.487)
                .classSummaries(List.of(classSummary))
                .statusDistribution(Map.of("PASSED", 2L, "FAILED", 1L, "SKIPPED", 1L))
                .classStatusCounts(Map.of("PortfolioServiceTest",
                        ClassStatusCount.builder().passed(2).failed(1).skipped(1).error(0).build()))
                .timeByClass(Map.of("PortfolioServiceTest", 0.487))
                .build();
    }

    @Test
    void generate_containsSummaryCards() throws IOException {
        // Given
        ExecutionSummary summary = buildSampleSummary();
        Path output = tempDir.resolve("report.html");

        // When
        createService().generate(summary, output);
        String html = Files.readString(output);

        // Then
        assertThat(html).contains("Overall Summary");
        assertThat(html).contains("summary-cards");
        assertThat(html).contains("Total Tests");
        assertThat(html).contains("Passed");
        assertThat(html).contains("Failed");
        assertThat(html).contains("Success Rate");
    }

    @Test
    void generate_containsCharts() throws IOException {
        // Given
        ExecutionSummary summary = buildSampleSummary();
        Path output = tempDir.resolve("report.html");

        // When
        createService().generate(summary, output);
        String html = Files.readString(output);

        // Then
        assertThat(html).contains("<canvas id=\"statusPie\">");
        assertThat(html).contains("<canvas id=\"passFailBar\">");
        assertThat(html).contains("Test Status Distribution");
        assertThat(html).contains("Pass / Fail by Class");
    }

    @Test
    void generate_containsClassTable() throws IOException {
        // Given
        ExecutionSummary summary = buildSampleSummary();
        Path output = tempDir.resolve("report.html");

        // When
        createService().generate(summary, output);
        String html = Files.readString(output);

        // Then
        assertThat(html).contains("Class-Level Summary");
        assertThat(html).contains("PortfolioServiceTest");
        assertThat(html).contains("status-fail");
        assertThat(html).contains("status-badge fail");
    }

    @Test
    void generate_containsFailedTestDetails() throws IOException {
        // Given
        ExecutionSummary summary = buildSampleSummary();
        Path output = tempDir.resolve("report.html");

        // When
        createService().generate(summary, output);
        String html = Files.readString(output);

        // Then
        assertThat(html).contains("Failed Test Details");
        assertThat(html).contains("testSellStock");
        assertThat(html).contains("java.lang.AssertionError");
        assertThat(html).contains("Not enough stocks");
        assertThat(html).contains("collapsible");
        assertThat(html).doesNotContain("#booleanCompare");
    }

    @Test
    void generate_allPassed_showsNoFailuresMessage() throws IOException {
        // Given
        TestClassSummary allPassed = TestClassSummary.builder()
                .fullyQualifiedClassName("OrderServiceTest")
                .packageName("")
                .simpleClassName("OrderServiceTest")
                .totalTests(2)
                .passedCount(2)
                .failedCount(0)
                .errorCount(0)
                .skippedCount(0)
                .totalTimeSeconds(0.5)
                .testCases(List.of())
                .build();

        ExecutionSummary summary = ExecutionSummary.builder()
                .reportTitle("Consolidated Test Report")
                .generatedAt(LocalDateTime.now())
                .totalSuites(1)
                .totalTests(2)
                .totalPassed(2)
                .totalFailed(0)
                .totalErrors(0)
                .totalSkipped(0)
                .totalTimeSeconds(0.5)
                .classSummaries(List.of(allPassed))
                .statusDistribution(Map.of("PASSED", 2L))
                .classStatusCounts(Map.of("OrderServiceTest",
                        ClassStatusCount.builder().passed(2).failed(0).skipped(0).error(0).build()))
                .timeByClass(Map.of("OrderServiceTest", 0.5))
                .build();

        Path output = tempDir.resolve("report-all-passed.html");

        // When
        createService().generate(summary, output);
        String html = Files.readString(output);

        // Then
        assertThat(html).contains("No failed tests! All tests passed.");
        assertThat(html).contains("status-pass");
    }
}
