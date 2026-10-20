package com.thiru.wealthlens.portfolio.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.portfolio.entity.TradeOutcomeEntity;
import com.thiru.wealthlens.portfolio.migration.RebuildBuyChargeCorrection.Correction;
import com.thiru.wealthlens.portfolio.migration.RebuildBuyChargeCorrection.Pairing;
import java.util.List;
import org.junit.jupiter.api.Test;

/** How a user's stored rebuild rows pair with the rebuild's rows for the same sells (W-088u7). */
class RebuildBuyChargeCorrectionTest {

    private static TradeOutcomeEntity row(String sell, String lot, double quantity, double buyBrokerCharges) {
        TradeOutcomeEntity row = new TradeOutcomeEntity();
        row.setId(sell + "@" + lot + "#" + buyBrokerCharges);
        row.setEmail("pair@example.com");
        row.setBrokerName(BrokerName.ZERODHA);
        row.setAccountHolder("main");
        row.setSourceSellTransactionId(sell);
        row.setSourceBuyLotId(lot);
        row.setBuyQuantity(quantity);
        row.setBuyBrokerCharges(buyBrokerCharges);
        return row;
    }

    @Test
    void pair_whenEveryStoredRowHasItsRebuiltRow_correctsOnlyTheLotsSoldMoreThanOnce() {
        // Given — lot A sold by s1 and s2 (the second over-charged), lot B by s1 alone
        List<TradeOutcomeEntity> stored = List.of(row("s1", "lot-A", 5.0, 5.0), row("s2", "lot-A", 5.0, 10.0),
                row("s1", "lot-B", 2.0, 1.0));
        List<TradeOutcomeEntity> rebuilt = List.of(row("s1", "lot-A", 5.0, 5.0), row("s2", "lot-A", 5.0, 5.0),
                row("s1", "lot-B", 2.0, 1.0));

        // When
        Pairing pairing = RebuildBuyChargeCorrection.pair(stored, rebuilt);

        // Then — both lot-A rows examined, only s2's changes; lot B is not examined at all
        assertThat(pairing.refusal()).isEmpty();
        assertThat(pairing.corrections()).extracting(c -> c.stored().getSourceSellTransactionId())
                .containsExactly("s1", "s2");
        assertThat(pairing.corrections()).filteredOn(Correction::changes)
                .extracting(c -> c.stored().getSourceSellTransactionId()).containsExactly("s2");
    }

    @Test
    void pair_whenALaterSellHasItsOwnRow_ignoresTheRebuiltRowForIt() {
        // Given — s3 was recorded by V1 or V2 after the rebuild, so no rebuild row names it
        List<TradeOutcomeEntity> stored = List.of(row("s1", "lot-A", 5.0, 5.0), row("s2", "lot-A", 5.0, 10.0));
        List<TradeOutcomeEntity> rebuilt = List.of(row("s1", "lot-A", 5.0, 5.0), row("s2", "lot-A", 5.0, 5.0),
                row("s3", "lot-A", 2.0, 0.0));

        // When
        Pairing pairing = RebuildBuyChargeCorrection.pair(stored, rebuilt);

        // Then
        assertThat(pairing.refusal()).isEmpty();
        assertThat(pairing.corrections()).hasSize(2);
    }

    @Test
    void pair_whenAStoredRowHasNoRebuiltRow_isRefused() {
        // Given — the buy behind lot A's s2 row was edited since, so the rebuild matches s2 to another lot
        List<TradeOutcomeEntity> stored = List.of(row("s1", "lot-A", 5.0, 5.0), row("s2", "lot-A", 5.0, 10.0));
        List<TradeOutcomeEntity> rebuilt = List.of(row("s1", "lot-A", 5.0, 5.0), row("s2", "lot-C", 5.0, 5.0));

        // When / Then
        assertThat(RebuildBuyChargeCorrection.pair(stored, rebuilt).refusal()).hasValueSatisfying(
                reason -> assertThat(reason).contains("lot-A").contains("transactions changed since the rebuild"));
    }

    @Test
    void pair_whenTheQuantitiesDiffer_isRefused() {
        // Given
        List<TradeOutcomeEntity> stored = List.of(row("s1", "lot-A", 5.0, 5.0), row("s2", "lot-A", 5.0, 10.0));
        List<TradeOutcomeEntity> rebuilt = List.of(row("s1", "lot-A", 5.0, 5.0), row("s2", "lot-A", 4.0, 4.0));

        // When / Then
        assertThat(RebuildBuyChargeCorrection.pair(stored, rebuilt).refusal()).isPresent();
    }

    @Test
    void pair_whenTheRebuildMakesARowNoneIsStoredFor_isRefused() {
        // Given — s2 now also takes from lot D, which no stored row names
        List<TradeOutcomeEntity> stored = List.of(row("s1", "lot-A", 5.0, 5.0), row("s2", "lot-A", 5.0, 10.0));
        List<TradeOutcomeEntity> rebuilt = List.of(row("s1", "lot-A", 5.0, 5.0), row("s2", "lot-A", 5.0, 5.0),
                row("s2", "lot-D", 1.0, 0.0));

        // When / Then
        assertThat(RebuildBuyChargeCorrection.pair(stored, rebuilt).refusal()).hasValueSatisfying(
                reason -> assertThat(reason).contains("makes 3 rows for these sells, but 2 are stored"));
    }
}
