package com.thiru.wealthlens.testreport.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionSummary {
    private String reportTitle;
    private LocalDateTime generatedAt;
    private int totalSuites;
    private int totalTests;
    private int totalPassed;
    private int totalFailed;
    private int totalErrors;
    private int totalSkipped;
    private double totalTimeSeconds;
    private List<TestClassSummary> classSummaries;
    private Map<String, Long> statusDistribution;
    private Map<String, ClassStatusCount> classStatusCounts;
    private Map<String, Double> timeByClass;
}
