package com.thiru.wealthlens.taxplanning.compute;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.thiru.wealthlens.shared.util.time.TLocalDateTime;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * One stored computation of a profile, enough to find it and take its report by receipt before the profile is erased
 * (W-123b C1a; D-477 C1a).
 *
 * @param receipt what its report is fetched by
 * @param taxYear the tax year computed
 * @param draftLabel the label of the draft it read
 * @param revision the draft revision it read
 * @param regime the regime computed
 * @param computedAt when it was computed, in UTC; empty for one stored before W-123b, which recorded no time
 */
public record ComputationListing(String receipt, String taxYear, String draftLabel, int revision, String regime,
        @JsonFormat(pattern = TLocalDateTime.COMPLETE_DATE_TIME_FORMAT) Optional<LocalDateTime> computedAt) {
}
