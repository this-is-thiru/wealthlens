package com.thiru.wealthlens.shared.dto;

import com.thiru.wealthlens.shared.entity.query.QueryFilter;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EntityExportRequest {
    private String entityName;
    private List<String> emailAddresses = new ArrayList<>();
    private List<String> selectedColumns = new ArrayList<>();
    private List<QueryFilter> queryFilters = new ArrayList<>();
}
