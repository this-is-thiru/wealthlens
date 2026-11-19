package com.thiru.wealthlens.shared.privacy;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.thiru.wealthlens.shared.util.collection.TCollectionUtil;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * What a user is told when they ask for their account to be erased (W-237d), and what the owner reads later.
 *
 * @param email whose account
 * @param requestedAt when it was asked, UTC
 * @param erasedFrom when the hold ends and the erasure runs, UTC (Rules r.8(3))
 * @param erasedAt when it was done, absent until then
 * @param contact who answers questions about the processing of personal data (Rules r.9)
 */
public record AccountErasureReceipt(
        String email,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = TCollectionUtil.DATE_TIME_FORMAT) LocalDateTime requestedAt,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = TCollectionUtil.DATE_TIME_FORMAT) LocalDateTime erasedFrom,
        Optional<LocalDateTime> erasedAt,
        String contact) {
}
