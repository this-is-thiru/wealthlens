package com.thiru.wealthlens.testreport.parser;

import com.thiru.wealthlens.testreport.parser.xml.SurefireTestSuite;
import java.nio.file.Path;
import java.util.List;

public interface SurefireReportParser {
    SurefireTestSuite parse(Path xmlFile);
    List<SurefireTestSuite> parseDirectory(Path directory);
}
