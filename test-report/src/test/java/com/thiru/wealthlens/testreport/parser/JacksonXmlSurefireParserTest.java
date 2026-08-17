package com.thiru.wealthlens.testreport.parser;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.testreport.parser.xml.SurefireTestSuite;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JacksonXmlSurefireParserTest {

    private JacksonXmlSurefireParser parser;
    private Path sampleReportPath;

    @BeforeEach
    void setUp() throws URISyntaxException {
        parser = new JacksonXmlSurefireParser();
        sampleReportPath = Paths.get(getClass().getClassLoader().getResource("TEST-sample-surefire-report.xml").toURI());
    }

    @Test
    void parse_validXmlFile_returnsTestSuite() {
        // Given
        // sampleReportPath points to sample-surefire-report.xml

        // When
        SurefireTestSuite suite = parser.parse(sampleReportPath);

        // Then
        assertThat(suite).isNotNull();
        assertThat(suite.getName()).isEqualTo("PortfolioServiceTest");
        assertThat(suite.getTests()).isEqualTo(4);
        assertThat(suite.getFailures()).isEqualTo(1);
        assertThat(suite.getErrors()).isEqualTo(0);
        assertThat(suite.getSkipped()).isEqualTo(1);
    }

    @Test
    void parse_validXmlFile_parsesAllTestCases() {
        // When
        SurefireTestSuite suite = parser.parse(sampleReportPath);

        // Then
        assertThat(suite.getTestCases()).isNotNull();
        assertThat(suite.getTestCases()).hasSize(4);
    }

    @Test
    void parse_validXmlFile_identifiesPassedTests() {
        // When
        SurefireTestSuite suite = parser.parse(sampleReportPath);

        // Then
        var testCaseNames = suite.getTestCases().stream()
                .map(tc -> tc.getName())
                .toList();

        assertThat(testCaseNames).contains("testBuyStock", "testGetPortfolio");
    }

    @Test
    void parse_validXmlFile_identifiesFailedTest() {
        // When
        SurefireTestSuite suite = parser.parse(sampleReportPath);

        // Then
        var failedTestCase = suite.getTestCases().stream()
                .filter(tc -> tc.getFailure() != null)
                .findFirst();

        assertThat(failedTestCase).isPresent();
        assertThat(failedTestCase.get().getName()).isEqualTo("testSellStock");
        assertThat(failedTestCase.get().getFailure().getMessage()).isEqualTo("Not enough stocks");
        assertThat(failedTestCase.get().getFailure().getDetail()).contains("PortfolioServiceTest");
    }

    @Test
    void parse_validXmlFile_identifiesSkippedTest() {
        // When
        SurefireTestSuite suite = parser.parse(sampleReportPath);

        // Then
        var skippedTestCase = suite.getTestCases().stream()
                .filter(tc -> tc.getSkipped() != null)
                .findFirst();

        assertThat(skippedTestCase).isPresent();
        assertThat(skippedTestCase.get().getName()).isEqualTo("testCancelledOrder");
    }

    @Test
    void parse_validXmlFile_parsesTestCaseTimes() {
        // When
        SurefireTestSuite suite = parser.parse(sampleReportPath);

        // Then
        var testBuyStock = suite.getTestCases().stream()
                .filter(tc -> tc.getName().equals("testBuyStock"))
                .findFirst();

        assertThat(testBuyStock).isPresent();
        assertThat(testBuyStock.get().getTime()).isEqualTo(0.1);
    }

    @Test
    void parse_nonExistentFile_throwsException() {
        // Given
        Path nonExistentPath = Path.of("/non/existent/file.xml");

        // When / Then
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> parser.parse(nonExistentPath)
        );
    }

    @Test
    void parseDirectory_withSampleReports_parsesAllSuites() {
        // Given
        Path testResourcesDir = sampleReportPath.getParent();

        // When
        List<SurefireTestSuite> suites = parser.parseDirectory(testResourcesDir);

        // Then
        assertThat(suites).isNotEmpty();
        assertThat(suites).hasSize(1);
        assertThat(suites.get(0).getName()).isEqualTo("PortfolioServiceTest");
    }
}
