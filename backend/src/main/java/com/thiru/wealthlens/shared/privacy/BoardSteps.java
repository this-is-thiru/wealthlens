package com.thiru.wealthlens.shared.privacy;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.thiru.wealthlens.shared.util.collection.TCollectionUtil;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * What has been done towards the Board on a breach, and when (DPDP Rules r.7(2); W-237c).
 *
 * @param reportDueAt when the detailed report is due, UTC
 * @param extensionReference the Board's reference for a longer period, if it allowed one
 * @param firstIntimatedAt when the Board was first told, UTC (r.7(2)(a))
 * @param reportSentAt when the detailed report was sent, UTC (r.7(2)(b))
 */
public record BoardSteps(
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = TCollectionUtil.DATE_TIME_FORMAT) LocalDateTime reportDueAt,
        Optional<String> extensionReference,
        Optional<LocalDateTime> firstIntimatedAt,
        Optional<LocalDateTime> reportSentAt) {
}
