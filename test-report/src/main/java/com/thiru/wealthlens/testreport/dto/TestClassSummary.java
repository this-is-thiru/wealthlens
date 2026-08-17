package com.thiru.wealthlens.testreport.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestClassSummary {
    private String fullyQualifiedClassName;
    private String packageName;
    private String simpleClassName;
    private int totalTests;
    private int passedCount;
    private int failedCount;
    private int errorCount;
    private int skippedCount;
    private double totalTimeSeconds;
    private List<TestCaseResult> testCases;
}
