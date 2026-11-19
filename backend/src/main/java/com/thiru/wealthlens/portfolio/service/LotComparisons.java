package com.thiru.wealthlens.portfolio.service;

import com.thiru.wealthlens.portfolio.dto.AssetsRebuildReport.LotComparison;
import com.thiru.wealthlens.portfolio.dto.AssetsRebuildReport.LotFigures;
import com.thiru.wealthlens.portfolio.entity.AssetEntity;
import com.thiru.wealthlens.shared.util.math.DoubleUtil;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Stored lots against rebuilt ones (W-283a2, D-582). A lot is matched by stock, broker, account holder, date and the
 * buy transactions it lists — what a lot is, rather than its id, which a rebuilt lot does not have yet (W-283a3 C3a).
 * A demerged lot lists no buy, so two demerged from different lots of one date share a key and are compared in order.
 */
final class LotComparisons {

    private LotComparisons() {
    }

    /** A stored lot and the rebuilt lot paired with it; either side is empty when the lot exists only on the other. */
    record LotPair(Optional<AssetEntity> stored, Optional<AssetEntity> rebuilt) {

        AssetEntity either() {
            return stored.or(() -> rebuilt).orElseThrow();
        }
    }

    /** The pairing the report shows and the write keeps ids by (W-283a3 C3a of D-582). */
    static List<LotPair> pairs(List<AssetEntity> stored, List<AssetEntity> rebuilt) {
        Map<String, List<AssetEntity>> storedByKey = byKey(stored);
        Map<String, List<AssetEntity>> rebuiltByKey = byKey(rebuilt);
        List<String> keys = new ArrayList<>(storedByKey.keySet());
        rebuiltByKey.keySet().stream().filter(key -> !storedByKey.containsKey(key)).forEach(keys::add);

        List<LotPair> pairs = new ArrayList<>();
        for (String key : keys) {
            List<AssetEntity> storedLots = storedByKey.getOrDefault(key, List.of());
            List<AssetEntity> rebuiltLots = rebuiltByKey.getOrDefault(key, List.of());
            for (int i = 0; i < Math.max(storedLots.size(), rebuiltLots.size()); i++) {
                pairs.add(new LotPair(at(storedLots, i), at(rebuiltLots, i)));
            }
        }
        return pairs;
    }

    static List<LotComparison> of(List<LotPair> pairs) {
        return pairs.stream().map(pair -> {
            AssetEntity sample = pair.either();
            Optional<LotFigures> storedFigures = pair.stored().map(LotComparisons::figuresOf);
            Optional<LotFigures> rebuiltFigures = pair.rebuilt().map(LotComparisons::figuresOf);
            return new LotComparison(sample.getStockCode(), sample.getBrokerName(), sample.getAccountHolder(),
                    sample.getTransactionDate(), sample.getBuyTransactionIds().stream().sorted().toList(), storedFigures,
                    rebuiltFigures, differences(storedFigures, rebuiltFigures));
        }).toList();
    }

    private static Optional<AssetEntity> at(List<AssetEntity> lots, int index) {
        return index < lots.size() ? Optional.of(lots.get(index)) : Optional.empty();
    }

    private static Map<String, List<AssetEntity>> byKey(List<AssetEntity> lots) {
        Map<String, List<AssetEntity>> byKey = new LinkedHashMap<>();
        lots.forEach(lot -> byKey.computeIfAbsent(String.join("|", lot.getStockCode(),
                String.valueOf(lot.getBrokerName()), String.valueOf(lot.getAccountHolder()),
                String.valueOf(lot.getTransactionDate()), String.join(",",
                        lot.getBuyTransactionIds().stream().sorted().toList())), key -> new ArrayList<>()).add(lot));
        return byKey;
    }

    private static LotFigures figuresOf(AssetEntity lot) {
        return new LotFigures(lot.getQuantity(), lot.getPrice(), lot.getBrokerCharges(), lot.getMiscCharges(),
                lot.getAllocatedBuyCharges(), lot.getAllocatedBuyMiscCharges(),
                Optional.ofNullable(lot.getCorporateActionType()));
    }

    private static List<String> differences(Optional<LotFigures> stored, Optional<LotFigures> rebuilt) {
        if (stored.isEmpty() || rebuilt.isEmpty()) {
            return List.of(stored.isEmpty() ? "storedLot" : "rebuiltLot");
        }
        LotFigures a = stored.get();
        LotFigures b = rebuilt.get();
        List<String> differing = new ArrayList<>();
        addIfDifferent(differing, "quantity", a.quantity(), b.quantity());
        addIfDifferent(differing, "price", a.priceRupees(), b.priceRupees());
        addIfDifferent(differing, "brokerCharges", a.brokerChargesRupees(), b.brokerChargesRupees());
        addIfDifferent(differing, "miscCharges", a.miscChargesRupees(), b.miscChargesRupees());
        addIfDifferent(differing, "allocatedBuyCharges", a.allocatedBuyChargesRupees(), b.allocatedBuyChargesRupees());
        addIfDifferent(differing, "allocatedBuyMiscCharges", a.allocatedBuyMiscChargesRupees(),
                b.allocatedBuyMiscChargesRupees());
        if (!Objects.equals(a.corporateActionType(), b.corporateActionType())) {
            differing.add("corporateActionType");
        }
        return differing;
    }

    private static void addIfDifferent(List<String> differing, String field, double stored, double rebuilt) {
        if (!DoubleUtil.equal(stored, rebuilt)) {
            differing.add(field);
        }
    }
}
