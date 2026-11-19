package com.thiru.wealthlens.shared.privacy;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.thiru.wealthlens.shared.util.collection.TCollectionUtil;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * One affected user and how they were told (DPDP Rules r.7(2)(b)(vi); W-237c).
 *
 * @param email the user
 * @param postedAt when the notice appeared in their account, UTC
 * @param readAt when they first read it there, UTC
 * @param emailedAt when the owner recorded emailing it, UTC
 */
public record BreachIntimationLine(
        String email,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = TCollectionUtil.DATE_TIME_FORMAT) LocalDateTime postedAt,
        Optional<LocalDateTime> readAt,
        Optional<LocalDateTime> emailedAt) {
}
