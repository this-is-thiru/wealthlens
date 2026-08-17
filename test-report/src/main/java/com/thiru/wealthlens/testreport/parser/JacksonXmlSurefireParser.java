package com.thiru.wealthlens.testreport.parser;

import com.thiru.wealthlens.testreport.parser.xml.SurefireTestSuite;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.dataformat.xml.XmlMapper;

@Slf4j
public class JacksonXmlSurefireParser implements SurefireReportParser {

    private final XmlMapper xmlMapper;

    public JacksonXmlSurefireParser() {
        this.xmlMapper = XmlMapper.builder()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
    }

    @Override
    public SurefireTestSuite parse(Path xmlFile) {
        if (!Files.exists(xmlFile)) {
            throw new IllegalArgumentException("File does not exist: " + xmlFile);
        }
        return xmlMapper.readValue(xmlFile.toFile(), SurefireTestSuite.class);
    }

    @Override
    public List<SurefireTestSuite> parseDirectory(Path directory) {
        List<SurefireTestSuite> suites = new ArrayList<>();
        try (var stream = Files.newDirectoryStream(directory, "TEST-*.xml")) {
            for (Path path : stream) {
                try {
                    suites.add(parse(path));
                } catch (Exception e) {
                    log.warn("Skipping invalid report file: {}", path, e);
                }
            }
        } catch (IOException e) {
            log.error("Failed to read directory: {}", directory, e);
        }
        return suites;
    }
}
