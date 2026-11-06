package com.thiru.wealthlens.taxplanning.jobs;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * When the jobs run (D-449 C4a). {@code app.tax.jobs.enabled} is the switch: off, nothing is scheduled and stored drafts are left
 * as they are; tests run the jobs directly.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.tax.jobs.enabled", havingValue = "true")
@RequiredArgsConstructor
public class TaxJobSchedule {

    private final TaxJobRunner runner;
    private final AutoArchive autoArchive;
    private final RetentionPurge retentionPurge;

    @Scheduled(cron = "${app.tax.jobs.auto-archive-cron}")
    public void autoArchive() {
        runner.run(autoArchive);
    }

    @Scheduled(cron = "${app.tax.jobs.retention-purge-cron}")
    public void retentionPurge() {
        runner.run(retentionPurge);
    }
}
