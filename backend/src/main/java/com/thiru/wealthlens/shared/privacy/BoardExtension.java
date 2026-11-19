package com.thiru.wealthlens.shared.privacy;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.thiru.wealthlens.shared.util.collection.TCollectionUtil;
import java.time.LocalDateTime;

/**
 * A longer period the Board allowed for the detailed report, on a written request (DPDP Rules r.7(2)(b); W-237c).
 *
 * @param reportDueAt the new time the report is due, UTC
 * @param reference the Board's reference for allowing it
 */
public record BoardExtension(@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = TCollectionUtil.DATE_TIME_FORMAT) LocalDateTime reportDueAt, String reference) {
}
