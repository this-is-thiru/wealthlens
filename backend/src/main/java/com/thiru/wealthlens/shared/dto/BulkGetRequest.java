package com.thiru.wealthlens.shared.dto;

import com.thiru.wealthlens.shared.entity.query.QueryFilter;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class BulkGetRequest {
	private DateRange dateRange;
	private List<QueryFilter> queryFilters = new ArrayList<>();
}
