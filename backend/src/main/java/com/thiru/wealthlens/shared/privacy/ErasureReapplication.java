package com.thiru.wealthlens.shared.privacy;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.thiru.wealthlens.shared.util.collection.TCollectionUtil;
import java.time.LocalDateTime;

/**
 * The owner's call after restoring the database from a snapshot (W-237f C2a′): every erasure done, and every hold asked, at or
 * after the snapshot's time is in the live records but not in the restored data, so it is applied again.
 *
 * @param snapshotTakenAt when the restored snapshot was taken, UTC
 */
public record ErasureReapplication(
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = TCollectionUtil.DATE_TIME_FORMAT) LocalDateTime snapshotTakenAt) {
}
