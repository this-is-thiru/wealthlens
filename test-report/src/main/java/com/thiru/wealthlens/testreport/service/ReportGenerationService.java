package com.thiru.wealthlens.testreport.service;

import com.thiru.wealthlens.testreport.dto.ExecutionSummary;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Slf4j
@RequiredArgsConstructor
public class ReportGenerationService {

    private final TemplateEngine templateEngine;

    public void generate(ExecutionSummary summary, Path outputPath) {
        Context ctx = new Context();
        ctx.setVariable("summary", summary);

        try {
            Files.createDirectories(outputPath.getParent());
            String html = templateEngine.process("consolidated-report", ctx);
            Files.writeString(outputPath, html, java.nio.charset.StandardCharsets.UTF_8);
            log.info("Consolidated test report generated at: {}", outputPath.toAbsolutePath());
        } catch (IOException e) {
            log.error("Failed to write report to: {}", outputPath, e);
            throw new RuntimeException("Failed to write report to: " + outputPath, e);
        }
    }
}
