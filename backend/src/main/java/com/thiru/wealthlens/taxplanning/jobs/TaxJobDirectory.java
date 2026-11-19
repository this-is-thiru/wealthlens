package com.thiru.wealthlens.taxplanning.jobs;

import com.thiru.wealthlens.taxplanning.gazette.GazetteWatch;
import com.thiru.wealthlens.taxplanning.gazette.GazetteWatchSettings;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

/**
 * The tax jobs by name, so the owner can run one on request (W-336, D-783): while the app runs only on the owner's machine
 * nothing is scheduled. A run goes through {@link TaxJobRunner}, so it takes the same lease as a scheduled run and the two never
 * run one job at once.
 */
@Service
@Log4j2
@RequiredArgsConstructor
public class TaxJobDirectory {

    private final List<TaxJob> jobs;
    private final TaxJobRunner runner;
    private final GazetteWatchSettings gazetteWatchSettings;

    /**
     * Runs the job named {@code name}. Refuses a name no job has, and the Gazette watch while its own switch is off, as the
     * schedule does.
     */
    public JobRunReceipt run(String name) {
        TaxJob job = jobs.stream().filter(candidate -> candidate.name().equals(name)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No tax job is named " + name + "; the jobs are " + names()));
        if (job instanceof GazetteWatch && !gazetteWatchSettings.enabled()) {
            throw new IllegalArgumentException(
                    "The Gazette watch is off (app.tax.gazette-watch.enabled); runbook §1.w turns it on");
        }
        log.info("Job {} run on request", name);
        return new JobRunReceipt(name, runner.run(job));
    }

    private List<String> names() {
        return jobs.stream().map(TaxJob::name).sorted().toList();
    }
}
