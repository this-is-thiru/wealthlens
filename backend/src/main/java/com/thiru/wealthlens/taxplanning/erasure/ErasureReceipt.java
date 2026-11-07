package com.thiru.wealthlens.taxplanning.erasure;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.thiru.wealthlens.shared.util.collection.TCollectionUtil;
import java.time.LocalDateTime;
import java.util.List;

/**
 * What a user is told when they ask for a profile to be erased (W-235): what will be erased, that nothing is kept after
 * {@code eraseOn}, why it is held until then, and who answers questions (r.9). Times are UTC.
 */
public record ErasureReceipt(
        String profileId,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = TCollectionUtil.DATE_TIME_FORMAT) LocalDateTime requestedAt,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = TCollectionUtil.DATE_TIME_FORMAT) LocalDateTime eraseOn,
        List<String> erased,
        String heldUntilThen,
        String contact) {

    static final List<String> ERASED = List.of("the profile", "its family members, employers, landlords, lenders and bank accounts",
            "every draft of every year, filed or not, with its revisions and computations", "its regime election history",
            "the record of each time any of these was read");

    static final String HELD = "Until then the data is kept unread and unprocessed: the Digital Personal Data Protection Rules, 2025"
            + " r.8(3) require the data and the logs of any processing to be kept for one year from that processing. Nothing is kept"
            + " after it.";

    public ErasureReceipt {
        erased = List.copyOf(erased);
    }
}
