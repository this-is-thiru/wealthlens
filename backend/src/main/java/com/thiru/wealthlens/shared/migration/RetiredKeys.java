package com.thiru.wealthlens.shared.migration;

import java.util.Map;
import java.util.Set;

/**
 * The keys {@code m1.6.1}'s documents still hold but {@code m1.6.1} no longer read (W-417, D-999), so the copier leaves each
 * out and counts it: copying one would refuse the document, or fill a field {@code m1.6.1} showed as empty. A document's own
 * keys are listed by collection; a key inside a JSON column by the class it sits in, named by class name as
 * {@link FormerFieldNames} does, so the copier names no other module's type. Removed with the copier (W-337j).
 */
final class RetiredKeys {

    private static final Map<String, Set<String>> BY_COLLECTION = Map.of(
            // AssetEntity's totalValue, removed by d55dd15f (#45); m1.6.1's AssetEntity has no such field
            "assets", Set.of("total_value"));

    private static final Map<String, Set<String>> BY_CLASS = Map.of(
            // stored under the Java names before CorporateActionEntity took its snake_case @Field names, which m1.6.1 read
            // (CorporateActionEntity.java:21 – 55 at m1.6.1), and an audit pair stored before audit_metadata
            "com.thiru.wealthlens.corporate.entity.CorporateActionEntity",
            Set.of("stockCode", "stockName", "toStockCode", "toStockName", "exDate", "recordDate", "actionPrice", "createdAt",
                    "createdBy"));

    private RetiredKeys() {
    }

    static boolean isRetired(String collection, String key) {
        return BY_COLLECTION.getOrDefault(collection, Set.of()).contains(key);
    }

    static boolean isRetired(Class<?> type, String key) {
        return BY_CLASS.getOrDefault(type.getName(), Set.of()).contains(key);
    }
}
