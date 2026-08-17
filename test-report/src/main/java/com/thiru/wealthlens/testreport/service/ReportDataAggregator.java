package com.thiru.wealthlens.testreport.service;

import com.thiru.wealthlens.testreport.dto.ClassStatusCount;
import com.thiru.wealthlens.testreport.dto.ExecutionSummary;
import com.thiru.wealthlens.testreport.dto.TestCaseResult;
import com.thiru.wealthlens.testreport.dto.TestClassSummary;
import com.thiru.wealthlens.testreport.dto.TestStatus;
import com.thiru.wealthlens.testreport.parser.xml.SurefireTestCase;
import com.thiru.wealthlens.testreport.parser.xml.SurefireTestSuite;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@NoArgsConstructor
public class ReportDataAggregator {

    public ExecutionSummary aggregate(List<SurefireTestSuite> suites) {
        if (suites == null || suites.isEmpty()) {
            return buildEmptySummary();
        }

        int totalTests = 0;
        int totalPassed = 0;
        int totalFailed = 0;
        int totalErrors = 0;
        int totalSkipped = 0;
        double totalTime = 0.0;

        Map<String, Long> statusDistribution = new HashMap<>();
        Map<String, Double> timeByClass = new HashMap<>();
        Map<String, ClassStatusCount> classStatusCounts = new HashMap<>();
        List<TestClassSummary> classSummaries = new ArrayList<>();

        for (SurefireTestSuite suite : suites) {
            totalTests += suite.getTests();
            totalFailed += suite.getFailures();
            totalErrors += suite.getErrors();
            totalSkipped += suite.getSkipped();
            totalPassed += (suite.getTests() - suite.getFailures() - suite.getErrors() - suite.getSkipped());
            totalTime += suite.getTime();

            TestClassSummary classSummary = buildClassSummary(suite);
            classSummaries.add(classSummary);

            classStatusCounts.put(classSummary.getFullyQualifiedClassName(),
                    ClassStatusCount.builder()
                            .passed(classSummary.getPassedCount())
                            .failed(classSummary.getFailedCount())
                            .skipped(classSummary.getSkippedCount())
                            .error(classSummary.getErrorCount())
                            .build());

            // Update status distribution
            for (SurefireTestCase tc : suite.getTestCases()) {
                TestStatus status = determineStatus(tc);
                String statusKey = status.name();
                statusDistribution.merge(statusKey, 1L, Long::sum);

                String className = tc.getClassname();
                timeByClass.merge(className, tc.getTime(), Double::sum);
            }
        }

        return ExecutionSummary.builder()
                .reportTitle("Consolidated Test Report")
                .generatedAt(LocalDateTime.now())
                .totalSuites(suites.size())
                .totalTests(totalTests)
                .totalPassed(totalPassed)
                .totalFailed(totalFailed)
                .totalErrors(totalErrors)
                .totalSkipped(totalSkipped)
                .totalTimeSeconds(totalTime)
                .classSummaries(classSummaries)
                .statusDistribution(statusDistribution)
                .classStatusCounts(classStatusCounts)
                .timeByClass(timeByClass)
                .build();
    }

    private TestClassSummary buildClassSummary(SurefireTestSuite suite) {
        int passedCount = 0;
        int failedCount = 0;
        int errorCount = 0;
        int skippedCount = 0;
        double totalTime = 0.0;
        List<TestCaseResult> testCaseResults = new ArrayList<>();

        String fullyQualifiedClassName = suite.getName();
        String packageName = "";
        String simpleClassName = fullyQualifiedClassName;

        int lastDot = fullyQualifiedClassName.lastIndexOf('.');
        if (lastDot > 0) {
            packageName = fullyQualifiedClassName.substring(0, lastDot);
            simpleClassName = fullyQualifiedClassName.substring(lastDot + 1);
        }

        for (SurefireTestCase tc : suite.getTestCases()) {
            TestStatus status = determineStatus(tc);
            totalTime += tc.getTime();

            switch (status) {
                case PASSED -> passedCount++;
                case FAILED -> failedCount++;
                case ERROR -> errorCount++;
                case SKIPPED -> skippedCount++;
            }

            TestCaseResult result = TestCaseResult.builder()
                    .name(tc.getName())
                    .className(tc.getClassname())
                    .timeSeconds(tc.getTime())
                    .status(status)
                    .failureMessage(getFailureMessage(tc))
                    .failureType(getFailureType(tc))
                    .failureDetail(getFailureDetail(tc))
                    .systemOut(tc.getSystemOut())
                    .systemErr(tc.getSystemErr())
                    .build();

            testCaseResults.add(result);
        }

        return TestClassSummary.builder()
                .fullyQualifiedClassName(fullyQualifiedClassName)
                .packageName(packageName)
                .simpleClassName(simpleClassName)
                .totalTests(suite.getTests())
                .passedCount(passedCount)
                .failedCount(failedCount)
                .errorCount(errorCount)
                .skippedCount(skippedCount)
                .totalTimeSeconds(totalTime)
                .testCases(testCaseResults)
                .build();
    }

    private TestStatus determineStatus(SurefireTestCase tc) {
        if (tc.getSkipped() != null) {
            return TestStatus.SKIPPED;
        }
        if (tc.getFailure() != null) {
            return TestStatus.FAILED;
        }
        if (tc.getError() != null) {
            return TestStatus.ERROR;
        }
        return TestStatus.PASSED;
    }

    private String getFailureMessage(SurefireTestCase tc) {
        if (tc.getFailure() != null) {
            return tc.getFailure().getMessage();
        }
        if (tc.getError() != null) {
            return tc.getError().getMessage();
        }
        return null;
    }

    private String getFailureType(SurefireTestCase tc) {
        if (tc.getFailure() != null) {
            return tc.getFailure().getType();
        }
        if (tc.getError() != null) {
            return tc.getError().getType();
        }
        return null;
    }

    private String getFailureDetail(SurefireTestCase tc) {
        if (tc.getFailure() != null) {
            return tc.getFailure().getDetail();
        }
        if (tc.getError() != null) {
            return tc.getError().getDetail();
        }
        return null;
    }

    private ExecutionSummary buildEmptySummary() {
        return ExecutionSummary.builder()
                .reportTitle("Consolidated Test Report")
                .generatedAt(LocalDateTime.now())
                .totalSuites(0)
                .totalTests(0)
                .totalPassed(0)
                .totalFailed(0)
                .totalErrors(0)
                .totalSkipped(0)
                .totalTimeSeconds(0.0)
                .classSummaries(new ArrayList<>())
                .statusDistribution(new HashMap<>())
                .classStatusCounts(new HashMap<>())
                .timeByClass(new HashMap<>())
                .build();
    }
}
