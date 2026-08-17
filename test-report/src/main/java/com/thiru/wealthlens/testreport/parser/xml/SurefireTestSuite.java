package com.thiru.wealthlens.testreport.parser.xml;

import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import tools.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import tools.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

@JacksonXmlRootElement(localName = "testsuite")
@Data
@NoArgsConstructor
public class SurefireTestSuite {

    @JacksonXmlProperty(isAttribute = true)
    private String name;

    @JacksonXmlProperty(isAttribute = true)
    private double time;

    @JacksonXmlProperty(isAttribute = true)
    private int tests;

    @JacksonXmlProperty(isAttribute = true)
    private int errors;

    @JacksonXmlProperty(isAttribute = true)
    private int skipped;

    @JacksonXmlProperty(isAttribute = true)
    private int failures;

    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "testcase")
    private List<SurefireTestCase> testCases;
}
