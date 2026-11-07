package com.thiru.wealthlens.shared.privacy;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.thiru.wealthlens.shared.util.collection.TCollectionUtil;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * A grievance as its author and the owner see it (W-237b): when it was received, the date by which it will be answered
 * (Digital Personal Data Protection Rules, 2025 r.14(3)), the answer once given, and the contact named in every response
 * to a request exercising a right (r.9).
 *
 * @param id the grievance
 * @param email whose grievance it is
 * @param text what was raised
 * @param receivedAt when it was received, UTC
 * @param respondBy the date promised on receipt, UTC
 * @param answer the answer, absent until given
 * @param contact who answers questions about the processing of personal data
 */
public record GrievanceReceipt(
        String id,
        String email,
        String text,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = TCollectionUtil.DATE_TIME_FORMAT) LocalDateTime receivedAt,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = TCollectionUtil.DATE_TIME_FORMAT) LocalDateTime respondBy,
        Optional<GrievanceAnswer> answer,
        String contact) {
}
