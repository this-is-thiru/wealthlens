package com.thiru.wealthlens.brokercharges.dto.response;

import com.thiru.wealthlens.brokercharges.dto.enums.ChargeResolution;
import java.util.Map;

public record ChargeBackfillReport(
        int transactionsRead,
        int priced,
        int skipped,
        int sellsWithNoLotsFound,
        double totalComputed,
        Map<ChargeResolution, Integer> byResolution) {}
