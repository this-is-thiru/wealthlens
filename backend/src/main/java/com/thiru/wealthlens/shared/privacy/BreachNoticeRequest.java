package com.thiru.wealthlens.shared.privacy;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.thiru.wealthlens.shared.util.collection.TCollectionUtil;
import java.time.LocalDateTime;
import java.util.List;

/**
 * The owner's report of a breach (W-237c).
 *
 * @param text what each affected user is told
 * @param becameAwareAt when the owner became aware of it, UTC: the Board's clock starts here (r.7(2))
 * @param scope who is affected
 * @param emails the affected users when the scope is {@link BreachScope#LISTED}
 */
public record BreachNoticeRequest(
        BreachText text,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = TCollectionUtil.DATE_TIME_FORMAT) LocalDateTime becameAwareAt,
        BreachScope scope,
        List<String> emails) {

    public BreachNoticeRequest {
        emails = emails == null ? List.of() : List.copyOf(emails);
    }
}
