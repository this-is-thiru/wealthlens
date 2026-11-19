package com.thiru.wealthlens.shared.privacy;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.thiru.wealthlens.shared.util.collection.TCollectionUtil;
import java.time.LocalDateTime;
import java.util.List;

/**
 * A breach as the owner manages it (W-237c): the notice, who it went to, and the steps towards the Board.
 *
 * @param id the breach
 * @param text what each affected user is told
 * @param becameAwareAt when the owner became aware of it, UTC
 * @param scope who was affected
 * @param affected the emails it was posted to
 * @param postedAt when it was posted, UTC
 * @param board the steps towards the Board
 */
public record BreachCase(
        String id,
        BreachText text,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = TCollectionUtil.DATE_TIME_FORMAT) LocalDateTime becameAwareAt,
        BreachScope scope,
        List<String> affected,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = TCollectionUtil.DATE_TIME_FORMAT) LocalDateTime postedAt,
        BoardSteps board) {

    public BreachCase {
        affected = List.copyOf(affected);
    }
}
