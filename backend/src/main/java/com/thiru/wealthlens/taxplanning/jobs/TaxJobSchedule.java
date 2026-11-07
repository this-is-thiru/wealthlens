package com.thiru.wealthlens.taxplanning.jobs;

import com.thiru.wealthlens.taxplanning.gazette.GazetteWatch;
import com.thiru.wealthlens.taxplanning.gazette.GazetteWatchSettings;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
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
    private final AccountErasure accountErasure;
    private final StalenessSweep stalenessSweep;

    private final GazetteWatch gazetteWatch;

    private final GazetteWatchSettings gazetteWatchSettings;

    @Scheduled(cron = "${app.tax.jobs.auto-archive-cron}")
    public void autoArchive() {
        runner.run(autoArchive);
    }

    /** On every start: the first after a pack upgrade flags; later ones find nothing new ({@code DR 19} E8, W-138). */
    @EventListener(ApplicationReadyEvent.class)
    public void stalenessSweep() {
        runner.run(stalenessSweep);
    }

    @Scheduled(cron = "${app.tax.jobs.retention-purge-cron}")
    public void retentionPurge() {
        runner.run(retentionPurge);
    }

    @Scheduled(cron = "${app.tax.jobs.account-erasure-cron}")
    public void accountErasure() {
        runner.run(accountErasure);
    }

    /** Daily, and only once the owner has turned the watch on: it calls out to the e-Gazette (W-236 C4a). */
    @Scheduled(cron = "${app.tax.jobs.gazette-watch-cron}")
    public void gazetteWatch() {
        if (gazetteWatchSettings.enabled()) {
            runner.run(gazetteWatch);
        }
    }
}
