package com.thiru.wealthlens.testreport.parser.xml;

import lombok.Data;
import lombok.NoArgsConstructor;
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;

@Data
@NoArgsConstructor
public class SurefireTestCase {

    @JacksonXmlProperty(isAttribute = true)
    private String name;

    @JacksonXmlProperty(isAttribute = true)
    private String classname;

    @JacksonXmlProperty(isAttribute = true)
    private double time;

    @JacksonXmlProperty(localName = "failure")
    private SurefireFailure failure;

    @JacksonXmlProperty(localName = "error")
    private SurefireFailure error;

    @JacksonXmlProperty(localName = "skipped")
    private Skipped skipped;

    @JacksonXmlProperty(localName = "system-out")
    private String systemOut;

    @JacksonXmlProperty(localName = "system-err")
    private String systemErr;

    public static class Skipped {
    }
}
