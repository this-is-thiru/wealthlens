package com.thiru.wealthlens.shared.privacy;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.thiru.wealthlens.shared.util.collection.TCollectionUtil;
import java.time.LocalDateTime;
import java.util.List;

/**
 * What an {@link ErasureReapplication} applied again (W-237f C2a′), for the owner's run log.
 *
 * @param snapshotTakenAt the snapshot's time, UTC, as given
 * @param accountsErased each account erased at or after it, erased again
 * @param accountsHeld each account asked to be erased at or after it and not yet erased, held again
 * @param profiles each tax profile a user asked to erase at or after it, erased or held again
 */
public record ReappliedErasures(
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = TCollectionUtil.DATE_TIME_FORMAT) LocalDateTime snapshotTakenAt,
        List<String> accountsErased,
        List<String> accountsHeld,
        List<String> profiles) {

    public ReappliedErasures {
        accountsErased = List.copyOf(accountsErased);
        accountsHeld = List.copyOf(accountsHeld);
        profiles = List.copyOf(profiles);
    }
}
