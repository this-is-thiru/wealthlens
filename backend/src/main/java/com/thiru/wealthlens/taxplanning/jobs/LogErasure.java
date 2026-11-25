package com.thiru.wealthlens.taxplanning.jobs;

import com.thiru.wealthlens.shared.privacy.LogRetention;
import com.thiru.wealthlens.taxplanning.profile.ProfileErasureRepository;
import com.thiru.wealthlens.taxplanning.protection.AccessRecordRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

/**
 * Erases the logs of processing and the privacy records a year after their processing (W-237e C2a): the app-wide ones through
 * {@link LogRetention}, and the reads of special-category tax data and the records of profile erasures held here (W-237f). It runs here because this is where jobs get a
 * lease and a dead letter. Each step is a delete by date, so a run cut off part-way is completed by the next.
 */
@Log4j2
@Component
@RequiredArgsConstructor
public class LogErasure implements TaxJob {

    private final LogRetention retention;
    private final AccessRecordRepository accessRecords;
    private final ProfileErasureRepository profileErasures;

    @Override
    public String name() {
        return "log-retention";
    }

    @Override
    public Duration timeout() {
        return Duration.ofHours(1);
    }

    @Override
    public void run(JobContext context) {
        LocalDateTime nowUtc = context.nowUtc();
        retention.eraseExpired(nowUtc);
        long reads = accessRecords.deleteByReadAtLessThanEqual(retention.cutoff(nowUtc).toInstant(ZoneOffset.UTC));
        long erasures = profileErasures.deleteByErasedAtLessThanEqual(retention.cutoff(nowUtc));
        log.info("Erased {} reads of special-category tax data, {} profile erasure records", reads, erasures);
    }
}
