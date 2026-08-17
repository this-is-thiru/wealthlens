package com.thiru.wealthlens.testreport;

import com.thiru.wealthlens.testreport.parser.JacksonXmlSurefireParser;
import com.thiru.wealthlens.testreport.parser.SurefireReportParser;
import com.thiru.wealthlens.testreport.parser.xml.SurefireTestSuite;
import com.thiru.wealthlens.testreport.service.ReportDataAggregator;
import com.thiru.wealthlens.testreport.service.ReportGenerationService;
import com.thiru.wealthlens.testreport.template.ThymeleafTemplateEngineFactory;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.thymeleaf.TemplateEngine;

@Slf4j
public class TestReportApplication {

    private static final String DEFAULT_INPUT_GLOB = "**/target/surefire-reports/TEST-*.xml";
    private static final String DEFAULT_OUTPUT_PATH = "target/consolidated-test-report.html";

    public static void main(String[] args) {
        String inputGlob = args.length > 0 ? args[0] : DEFAULT_INPUT_GLOB;
        String outputPathStr = args.length > 1 ? args[1] : DEFAULT_OUTPUT_PATH;

        Path modulePath = Path.of(System.getProperty("module.basedir", System.getProperty("user.dir"))).normalize();
        String reactorRoot = System.getProperty("maven.multiModuleProjectDirectory");
        Path searchRoot = reactorRoot != null ? Path.of(reactorRoot).normalize() : modulePath;
        Path outputPath = modulePath.resolve(outputPathStr).normalize();

        log.info("Module directory: {}", modulePath.toAbsolutePath());
        log.info("Search root: {}", searchRoot.toAbsolutePath());

        log.info("Searching for test reports matching glob: {}", inputGlob);
        log.info("Output path: {}", outputPath.toAbsolutePath());

        try {
            List<Path> xmlFiles = findXmlFiles(searchRoot, inputGlob);

            if (xmlFiles.isEmpty()) {
                log.warn("No test report XML files found matching: {}", inputGlob);
                printSummary(0, 0, 0, 0, outputPath);
                return;
            }

            log.info("Found {} test report file(s)", xmlFiles.size());

            SurefireReportParser parser = new JacksonXmlSurefireParser();
            List<SurefireTestSuite> suites = xmlFiles.stream()
                    .map(parser::parse)
                    .toList();

            ReportDataAggregator aggregator = new ReportDataAggregator();
            var summary = aggregator.aggregate(suites);

            TemplateEngine engine = ThymeleafTemplateEngineFactory.createEngine();
            ReportGenerationService reportService = new ReportGenerationService(engine);
            reportService.generate(summary, outputPath);

            printSummary(
                    summary.getTotalTests(),
                    summary.getTotalPassed(),
                    summary.getTotalFailed(),
                    summary.getTotalErrors(),
                    outputPath
            );

        } catch (Exception e) {
            log.error("Failed to generate consolidated test report", e);
            System.exit(1);
        }
    }

    private static List<Path> findXmlFiles(Path rootPath, String glob) throws IOException {
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + glob);
        try (var stream = Files.walk(rootPath)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(matcher::matches)
                    .toList();
        }
    }

    private static void printSummary(int total, int passed, int failed, int errors, Path outputPath) {
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════╗");
        System.out.println("║          CONSOLIDATED TEST REPORT GENERATED              ║");
        System.out.println("╠══════════════════════════════════════════════════════════╣");
        System.out.printf("║  Total: %-5d  Passed: %-5d  Failed: %-5d  Errors: %-5d║\n",
                total, passed, failed, errors);
        System.out.printf("║  Output: %-45s  ║\n", outputPath.toAbsolutePath());
        System.out.println("╚══════════════════════════════════════════════════════════╝");
    }
}
