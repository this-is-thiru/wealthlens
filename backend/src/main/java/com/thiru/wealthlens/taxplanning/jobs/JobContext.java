package com.thiru.wealthlens.taxplanning.jobs;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

/** One run of a job: its clock, whether it still holds its lease, and where a refusal is recorded. */
@Log4j2
@RequiredArgsConstructor
public class JobContext {

    private final String job;
    private final Clock clock;
    private final BooleanSupplier lease;
    private final JobFailureRepository failures;

    public Clock clock() {
        return clock;
    }

    /** Now, in UTC: what stored dates are kept in. */
    public LocalDateTime nowUtc() {
        return LocalDateTime.now(clock.withZone(ZoneOffset.UTC));
    }

    /** Asked before each change: false once another run has taken the lease over. */
    public boolean holdsLease() {
        return lease.getAsBoolean();
    }

    /** Records that the job left {@code subject} alone, and why; logged at ERROR so it is seen. */
    public void refuse(String subject, String reason) {
        JobFailure failure = new JobFailure();
        failure.setId(UUID.randomUUID().toString());
        failure.setJob(job);
        failure.setSubject(subject);
        failure.setReason(reason);
        failure.setFailedAt(nowUtc());
        failures.insert(failure);
        log.error("Job {} refused {}: {}", job, subject, reason);
    }
}
