package com.thiru.wealthlens.testreport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.thiru.wealthlens.testreport.dto.ExecutionSummary;
import com.thiru.wealthlens.testreport.dto.TestClassSummary;
import com.thiru.wealthlens.testreport.parser.JacksonXmlSurefireParser;
import com.thiru.wealthlens.testreport.parser.xml.SurefireTestSuite;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReportDataAggregatorTest {

    private ReportDataAggregator aggregator;
    private JacksonXmlSurefireParser parser;
    private Path sampleReportPath;

    @BeforeEach
    void setUp() throws URISyntaxException {
        aggregator = new ReportDataAggregator();
        parser = new JacksonXmlSurefireParser();
        sampleReportPath = Paths.get(getClass().getClassLoader().getResource("TEST-sample-surefire-report.xml").toURI());
    }

    @Test
    void aggregate_singleSuite_returnsCorrectTotals() {
        // Given
        SurefireTestSuite suite = parser.parse(sampleReportPath);
        List<SurefireTestSuite> suites = List.of(suite);

        // When
        ExecutionSummary summary = aggregator.aggregate(suites);

        // Then
        assertThat(summary.getTotalTests()).isEqualTo(4);
        assertThat(summary.getTotalPassed()).isEqualTo(2);
        assertThat(summary.getTotalFailed()).isEqualTo(1);
        assertThat(summary.getTotalSkipped()).isEqualTo(1);
    }

    @Test
    void aggregate_singleSuite_calculatesStatusDistribution() {
        // Given
        SurefireTestSuite suite = parser.parse(sampleReportPath);
        List<SurefireTestSuite> suites = List.of(suite);

        // When
        ExecutionSummary summary = aggregator.aggregate(suites);

        // Then
        assertThat(summary.getStatusDistribution()).isNotNull();
        assertThat(summary.getStatusDistribution()).containsKey("PASSED");
        assertThat(summary.getStatusDistribution()).containsKey("FAILED");
        assertThat(summary.getStatusDistribution()).containsKey("SKIPPED");

        assertThat(summary.getStatusDistribution().get("PASSED")).isEqualTo(2L);
        assertThat(summary.getStatusDistribution().get("FAILED")).isEqualTo(1L);
        assertThat(summary.getStatusDistribution().get("SKIPPED")).isEqualTo(1L);
    }

    @Test
    void aggregate_singleSuite_containsClassSummary() {
        // Given
        SurefireTestSuite suite = parser.parse(sampleReportPath);
        List<SurefireTestSuite> suites = List.of(suite);

        // When
        ExecutionSummary summary = aggregator.aggregate(suites);

        // Then
        assertThat(summary.getClassSummaries()).isNotNull();
        assertThat(summary.getClassSummaries()).hasSize(1);
    }

    @Test
    void aggregate_singleSuite_classSummaryHasCorrectCounts() {
        // Given
        SurefireTestSuite suite = parser.parse(sampleReportPath);
        List<SurefireTestSuite> suites = List.of(suite);

        // When
        ExecutionSummary summary = aggregator.aggregate(suites);

        // Then
        TestClassSummary classSummary = summary.getClassSummaries().get(0);

        assertThat(classSummary.getTotalTests()).isEqualTo(4);
        assertThat(classSummary.getPassedCount()).isEqualTo(2);
        assertThat(classSummary.getFailedCount()).isEqualTo(1);
        assertThat(classSummary.getSkippedCount()).isEqualTo(1);
    }

    @Test
    void aggregate_singleSuite_classSummaryHasCorrectClassName() {
        // Given
        SurefireTestSuite suite = parser.parse(sampleReportPath);
        List<SurefireTestSuite> suites = List.of(suite);

        // When
        ExecutionSummary summary = aggregator.aggregate(suites);

        // Then
        TestClassSummary classSummary = summary.getClassSummaries().get(0);

        assertThat(classSummary.getFullyQualifiedClassName()).isEqualTo("PortfolioServiceTest");
        assertThat(classSummary.getSimpleClassName()).isEqualTo("PortfolioServiceTest");
    }

    @Test
    void aggregate_singleSuite_includesTestCaseResults() {
        // Given
        SurefireTestSuite suite = parser.parse(sampleReportPath);
        List<SurefireTestSuite> suites = List.of(suite);

        // When
        ExecutionSummary summary = aggregator.aggregate(suites);

        // Then
        TestClassSummary classSummary = summary.getClassSummaries().get(0);

        assertThat(classSummary.getTestCases()).hasSize(4);
        assertThat(classSummary.getTestCases())
                .extracting("name")
                .containsExactlyInAnyOrder("testBuyStock", "testSellStock", "testCancelledOrder", "testGetPortfolio");
    }

    @Test
    void aggregate_singleSuite_preservesFailureDetails() {
        // Given
        SurefireTestSuite suite = parser.parse(sampleReportPath);
        List<SurefireTestSuite> suites = List.of(suite);

        // When
        ExecutionSummary summary = aggregator.aggregate(suites);

        // Then
        TestClassSummary classSummary = summary.getClassSummaries().get(0);

        var failedTestCase = classSummary.getTestCases().stream()
                .filter(tc -> tc.getStatus().name().equals("FAILED"))
                .findFirst();

        assertThat(failedTestCase).isPresent();
        assertThat(failedTestCase.get().getFailureMessage()).isEqualTo("Not enough stocks");
        assertThat(failedTestCase.get().getFailureDetail()).contains("PortfolioServiceTest");
    }

    @Test
    void aggregate_emptyList_returnsEmptySummary() {
        // Given
        List<SurefireTestSuite> suites = List.of();

        // When
        ExecutionSummary summary = aggregator.aggregate(suites);

        // Then
        assertThat(summary.getTotalTests()).isEqualTo(0);
        assertThat(summary.getTotalPassed()).isEqualTo(0);
        assertThat(summary.getTotalFailed()).isEqualTo(0);
        assertThat(summary.getClassSummaries()).isEmpty();
        assertThat(summary.getStatusDistribution()).isEmpty();
    }

    @Test
    void aggregate_nullList_returnsEmptySummary() {
        // When
        ExecutionSummary summary = aggregator.aggregate(null);

        // Then
        assertThat(summary.getTotalTests()).isEqualTo(0);
        assertThat(summary.getClassSummaries()).isEmpty();
    }

    @Test
    void aggregate_singleSuite_populatesTimeByClass() {
        // Given
        SurefireTestSuite suite = parser.parse(sampleReportPath);
        List<SurefireTestSuite> suites = List.of(suite);

        // When
        ExecutionSummary summary = aggregator.aggregate(suites);

        // Then
        assertThat(summary.getTimeByClass()).isNotEmpty();
        assertThat(summary.getTimeByClass()).containsKey("PortfolioServiceTest");
        // Total time: 0.1 + 0.2 + 0.05 + 0.137 = 0.487
        assertThat(summary.getTimeByClass().get("PortfolioServiceTest")).isCloseTo(0.487, within(0.001));
    }

    @Test
    void aggregate_singleSuite_calculatesTotalTime() {
        // Given
        SurefireTestSuite suite = parser.parse(sampleReportPath);
        List<SurefireTestSuite> suites = List.of(suite);

        // When
        ExecutionSummary summary = aggregator.aggregate(suites);

        // Then
        // Suite time from XML is 0.487
        assertThat(summary.getTotalTimeSeconds()).isCloseTo(0.487, within(0.001));
    }

    @Test
    void aggregate_singleSuite_setsTotalSuites() {
        // Given
        SurefireTestSuite suite = parser.parse(sampleReportPath);
        List<SurefireTestSuite> suites = List.of(suite);

        // When
        ExecutionSummary summary = aggregator.aggregate(suites);

        // Then
        assertThat(summary.getTotalSuites()).isEqualTo(1);
    }

    @Test
    void aggregate_multipleSuites_aggregatesCorrectly() {
        // Given
        SurefireTestSuite suite1 = parser.parse(sampleReportPath);
        SurefireTestSuite suite2 = parser.parse(sampleReportPath);
        List<SurefireTestSuite> suites = List.of(suite1, suite2);

        // When
        ExecutionSummary summary = aggregator.aggregate(suites);

        // Then
        assertThat(summary.getTotalSuites()).isEqualTo(2);
        assertThat(summary.getTotalTests()).isEqualTo(8);
        assertThat(summary.getTotalPassed()).isEqualTo(4);
        assertThat(summary.getTotalFailed()).isEqualTo(2);
        assertThat(summary.getTotalSkipped()).isEqualTo(2);
    }
}
