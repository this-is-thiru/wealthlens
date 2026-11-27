package com.thiru.wealthlens.taxplanning.jobs;

import com.thiru.wealthlens.auth.config.SecurityAuditorAware;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

/**
 * Runs a job under its lease ({@code DR 19} E8), so that two instances never run one job at once. A failure inside the job is not
 * caught here: it reaches the scheduler, which logs it, and the lease lapses at its timeout. A job writes as its own name, so its
 * rows say which job wrote them (W-257).
 */
@Service
@Log4j2
@RequiredArgsConstructor
public class TaxJobRunner {

    private final JobLeaseRepository leases;
    private final JobFailureRepository failures;
    private final Clock clock;
    private final SecurityAuditorAware auditor;

    /** Runs {@code job} if no other run holds it; false where one does. */
    public boolean run(TaxJob job) {
        String holder = UUID.randomUUID().toString();
        if (!take(job, holder)) {
            log.info("Job {} is held by another run; skipped", job.name());
            return false;
        }
        try {
            auditor.runAs(job.name(), () -> job.run(new JobContext(job.name(), clock, () -> holds(job, holder), failures)));
        } finally {
            leases.deleteByJobAndHolder(job.name(), holder);
        }
        return true;
    }

    private boolean take(TaxJob job, String holder) {
        Instant now = clock.instant();
        return leases.acquire(job.name(), holder, now.plus(job.timeout()), now) == 1;
    }

    private boolean holds(TaxJob job, String holder) {
        return leases.existsByJobAndHolderAndExpiresAtAfter(job.name(), holder, clock.instant());
    }
}
