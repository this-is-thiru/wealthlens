package com.thiru.wealthlens.taxplanning.policy.dto;

import com.thiru.wealthlens.taxplanning.policy.entity.AllowanceCatalogueEntity;
import com.thiru.wealthlens.taxplanning.policy.entity.AllowanceLimitEntity;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ResolvedAllowance {

    AllowanceCatalogueEntity metadata;
    AllowanceLimitEntity limit;
}
