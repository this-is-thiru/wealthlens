package com.thiru.wealthlens.testreport.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TestCaseResult {
    private String name;
    private String className;
    private double timeSeconds;
    private TestStatus status;
    private String failureMessage;
    private String failureType;
    private String failureDetail;
    private String systemOut;
    private String systemErr;
}
