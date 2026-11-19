package com.thiru.wealthlens.shared.privacy;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.thiru.wealthlens.shared.util.collection.TCollectionUtil;
import java.time.LocalDateTime;

/**
 * The answer to a grievance and when it was given (W-237b).
 *
 * @param text the answer
 * @param answeredAt when it was given, UTC
 */
public record GrievanceAnswer(
        String text,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = TCollectionUtil.DATE_TIME_FORMAT) LocalDateTime answeredAt) {
}
