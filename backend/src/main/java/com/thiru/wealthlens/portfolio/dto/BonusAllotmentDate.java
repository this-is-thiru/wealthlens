package com.thiru.wealthlens.portfolio.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.thiru.wealthlens.shared.util.collection.TCollectionUtil;
import java.time.LocalDate;

/**
 * The allotment date the owner read from a company's announcement for one bonus action processed before W-088k4a
 * (W-088k4b, D-308).
 *
 * @param corporateActionId the bonus action's id
 * @param allotmentDate the date the bonus shares were allotted — when their holding period starts (1961 Explanation
 *     1(i)(f) to s.2(42A); 2025 s.2(101)(c)(C)(IV))
 */
public record BonusAllotmentDate(
        String corporateActionId,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = TCollectionUtil.DATE_FORMAT) LocalDate allotmentDate) {
}
