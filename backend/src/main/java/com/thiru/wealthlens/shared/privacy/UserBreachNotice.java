package com.thiru.wealthlens.shared.privacy;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.thiru.wealthlens.shared.util.collection.TCollectionUtil;
import java.time.LocalDateTime;

/**
 * A breach notice as the affected user reads it in their account (DPDP Rules r.7(1); W-237c).
 *
 * @param id the breach
 * @param text what happened, what it means for them, what has been done, what they can do (a) – (d)
 * @param postedAt when it was posted, UTC
 * @param contact who answers their queries (e)
 */
public record UserBreachNotice(String id, BreachText text, @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = TCollectionUtil.DATE_TIME_FORMAT) LocalDateTime postedAt, String contact) {
}
