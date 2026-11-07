package com.thiru.wealthlens.shared.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.thiru.wealthlens.shared.util.collection.TCollectionUtil;
import java.time.LocalDate;
import lombok.Data;

/** The dates a bulk read is filtered to, both ends as entered ({@code BulkGetRequest.dateRange}). */
@Data
public class DateRangeFilter {

	@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = TCollectionUtil.DATE_FORMAT)
	private LocalDate startDate;

	@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = TCollectionUtil.DATE_FORMAT)
	private LocalDate endDate;
}
