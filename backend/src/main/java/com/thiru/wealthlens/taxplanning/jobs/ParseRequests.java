package com.thiru.wealthlens.taxplanning.jobs;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Starts a {@link ParseDocuments} run in the background after an upload, so a document is read at once rather than at the next
 * minute (W-131d1, D-765; W-131c's C1a). Only where the jobs run at all: the schedule exists only when
 * {@code app.tax.jobs.enabled} is true, and a run skipped because another holds the lease is taken by the next minute's.
 */
@Component
@RequiredArgsConstructor
public class ParseRequests {

    private final ObjectProvider<TaxJobSchedule> schedule;

    public void request() {
        schedule.ifAvailable(jobs -> Thread.ofVirtual().name("parse-after-upload").start(jobs::parseDocuments));
    }
}
