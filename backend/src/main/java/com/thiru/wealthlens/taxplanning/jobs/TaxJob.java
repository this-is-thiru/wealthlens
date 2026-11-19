package com.thiru.wealthlens.taxplanning.jobs;

import java.time.Duration;

/**
 * A background job that runs on one instance at a time ({@code DR 19} E8). Each is idempotent, so a run that fails or is cut
 * off is completed by the next; no retry policy is kept, since no job here needs one sooner.
 */
public interface TaxJob {

    /** The lease's key, and how the job is named in logs and failures. */
    String name();

    /** How long a run holds its lease; one not finished by then may be overtaken, and stops at its next lease check. */
    Duration timeout();

    void run(JobContext context);
}
