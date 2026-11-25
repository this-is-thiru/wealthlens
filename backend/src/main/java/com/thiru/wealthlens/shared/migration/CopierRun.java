package com.thiru.wealthlens.shared.migration;

import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * The copier's one run (W-337g, D-953 C2a): with the {@code copier} profile the application starts without a web server,
 * copies, logs one line per source and stops — with an error, so a non-zero exit, when any check failed. The owner runs it
 * ({@code post-deployment-runbook.md}); nothing serves while it copies, so no write can slip past it.
 */
@Component
@Profile("copier")
@Log4j2
@RequiredArgsConstructor
public class CopierRun implements ApplicationRunner {

    /**
     * The time zone of the JVM that wrote the MongoDB rows; required, as nothing else records it: a time the application held
     * in UTC was stored shifted by it, and is shifted back to UTC with it (D-954, {@link RowMapping}).
     */
    static final String WRITTEN_ZONE = "app.copier.written-zone";

    private final MongoCopier copier;
    private final Environment environment;
    private final ApplicationContext context;

    @Override
    public void run(ApplicationArguments arguments) {
        Set<String> unspecified = CollectionCopies.unspecified();
        if (!unspecified.isEmpty()) {
            throw new IllegalStateException("The copier has no copy specified for " + unspecified + "; nothing was copied");
        }
        ZoneId writtenZone = ZoneId.of(environment.getRequiredProperty(WRITTEN_ZONE));
        copier.requireKnownSource();
        List<CopyReport> reports = copier.copyAll(CollectionCopies.SPECIFIED, writtenZone);
        reports.forEach(report -> log.info(report.describe()));
        List<String> failed = reports.stream().filter(report -> !report.isClean()).map(CopyReport::source).toList();
        if (!failed.isEmpty()) {
            throw new IllegalStateException("The copy is not proven for " + failed + "; see each one's line above");
        }
        log.info("Every source copied and proven, times written in UTC from rows written in {}", writtenZone);
        SpringApplication.exit(context);
    }
}
