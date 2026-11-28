package com.thiru.wealthlens.shared.protection;

import com.thiru.wealthlens.shared.protection.PlainTextRewrite.RewrittenColumn;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * The rewrite's one run (W-428a, D-1036 C1a): with the {@code plain-text-rewrite} profile the application starts with no port
 * open, makes every stored ciphertext plain, logs one line per column it changed, and stops. The owner runs it on each database
 * before that database's first normal start on this build ({@code post-deployment-runbook.md}): until then a sign-in looks for a
 * plain email among encrypted ones and finds nobody. A failure stops the run with an error, so a non-zero exit.
 */
@Component
@Profile("plain-text-rewrite")
@Log4j2
@RequiredArgsConstructor
public class PlainTextRewriteRun implements ApplicationRunner {

    private final PlainTextRewrite rewrite;
    private final ApplicationContext context;

    @Override
    public void run(ApplicationArguments arguments) {
        List<RewrittenColumn> columns = rewrite.rewriteAll();
        columns.forEach(column -> log.info(column.describe()));
        int total = columns.stream().mapToInt(RewrittenColumn::rewritten).sum();
        log.info("Plain-text rewrite done: {} value(s) made plain in {} column(s); a second run changes nothing", total,
                columns.stream().filter(column -> column.rewritten() > 0).count());
        SpringApplication.exit(context);
    }
}
