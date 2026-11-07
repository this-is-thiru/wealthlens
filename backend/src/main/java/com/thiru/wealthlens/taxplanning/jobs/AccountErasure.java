package com.thiru.wealthlens.taxplanning.jobs;

import com.thiru.wealthlens.shared.privacy.AccountErasureRequest;
import com.thiru.wealthlens.shared.privacy.AccountErasureService;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

/**
 * Erases each account whose hold has passed (W-237d C4a). The work is {@link AccountErasureService}'s, for every module; it
 * runs here because this is where jobs get a lease and a dead letter. A run that fails part-way leaves the request open, and the
 * next run completes it.
 */
@Component
@Log4j2
@RequiredArgsConstructor
public class AccountErasure implements TaxJob {

    private final AccountErasureService service;

    @Override
    public String name() {
        return "account-erasure";
    }

    @Override
    public Duration timeout() {
        return Duration.ofHours(1);
    }

    @Override
    public void run(JobContext context) {
        for (AccountErasureRequest request : service.due(context.nowUtc())) {
            if (!context.holdsLease()) {
                log.warn("Job {} lost its lease; stopping", name());
                return;
            }
            service.erase(request, context.nowUtc());
        }
    }
}
