package com.thiru.wealthlens.taxengine.rules.json;

import com.thiru.wealthlens.taxengine.rules.component.Liquidity;
import com.thiru.wealthlens.taxengine.rules.component.UnstatedTerms;
import com.thiru.wealthlens.taxengine.rules.component.WithdrawalTerm;
import tools.jackson.databind.JsonNode;

/**
 * A row's {@code liquidity} (W-238e1): its withdrawal terms and the periods without them. Absent means
 * {@link Liquidity#NONE}; whether a row may go without is the pack's check, not the reader's.
 */
public final class LiquidityJson {

    private LiquidityJson() {
    }

    /** The {@code liquidity} object of {@code row}, or {@link Liquidity#NONE} where it has none. */
    public static Liquidity of(JsonNode row, String path) {
        return PackJson.optionalObject(row, "liquidity", path)
                .map(node -> liquidity(node, path + ".liquidity"))
                .orElse(Liquidity.NONE);
    }

    private static Liquidity liquidity(JsonNode node, String path) {
        return PackJson.construct(path, () -> new Liquidity(
                PackJson.arrayOrEmpty(node, "terms", path, e -> term(e.node(), e.path())),
                PackJson.arrayOrEmpty(node, "unstated", path, e -> unstated(e.node(), e.path()))));
    }

    private static WithdrawalTerm term(JsonNode node, String path) {
        return PackJson.construct(path, () -> new WithdrawalTerm(
                PackJson.text(node, "event", path),
                PackJson.text(node, "terms", path),
                ValueJson.dateRange(PackJson.object(node, "validity", path), path + ".validity"),
                ValueJson.provenance(PackJson.object(node, "provenance", path), path + ".provenance")));
    }

    private static UnstatedTerms unstated(JsonNode node, String path) {
        return PackJson.construct(path, () -> new UnstatedTerms(
                ValueJson.dateRange(PackJson.object(node, "validity", path), path + ".validity"),
                PackJson.text(node, "reason", path)));
    }
}
