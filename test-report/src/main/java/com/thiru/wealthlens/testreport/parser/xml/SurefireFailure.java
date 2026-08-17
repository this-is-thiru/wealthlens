package com.thiru.wealthlens.testreport.parser.xml;

import lombok.Data;
import lombok.NoArgsConstructor;
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import tools.jackson.dataformat.xml.annotation.JacksonXmlText;

@Data
@NoArgsConstructor
public class SurefireFailure {
    @JacksonXmlProperty(isAttribute = true)
    private String message;

    @JacksonXmlProperty(isAttribute = true)
    private String type;

    @JacksonXmlText
    private String detail;
}
