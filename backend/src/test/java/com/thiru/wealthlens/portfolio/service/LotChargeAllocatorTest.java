package com.thiru.wealthlens.portfolio.service;

import static com.thiru.wealthlens.testsupport.MoneyAssert.assertCanonical;

import com.thiru.wealthlens.portfolio.entity.AssetEntity;
import com.thiru.wealthlens.shared.util.money.TMoney;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class LotChargeAllocatorTest {

    private static AssetEntity lot(double quantity, double brokerCharges, double miscCharges) {
        AssetEntity asset = new AssetEntity();
        asset.setQuantity(quantity);
        asset.setBrokerCharges(brokerCharges);
        asset.setMiscCharges(miscCharges);
        return asset;
    }

    @Test
    @DisplayName("selling a lot one unit at a time deducts exactly what was paid, never more")
    void deduct_acrossSuccessiveSells_sumsToWhatWasPaid() {
        // Given -- the B-7/B-8 case: dividing the full charge by the REMAINING quantity each time
        // deducted 3.33, then 5.00, then 10.00 -- Rs.18.33 against Rs.10.00 paid
        AssetEntity asset = lot(3.0, 10.0, 0.0);
        double deducted = 0;

        // When
        for (double remaining = 3.0; remaining >= 1.0; remaining--) {
            deducted = TMoney.add(deducted, LotChargeAllocator.deductBroker(asset, 1.0, remaining));
            asset.setQuantity(remaining - 1.0);
        }

        // Then
        assertCanonical("lifetime broker charge", 10.00, deducted);
        assertCanonical("nothing left on the lot", 10.00, asset.getAllocatedBuyCharges());
    }

    @Test
    @DisplayName("selling the whole lot at once takes the whole charge")
    void deduct_whenTheLotIsConsumedInOneSell_takesAllOfIt() {
        // Given
        AssetEntity asset = lot(3.0, 10.0, 4.0);

        // When
        double broker = LotChargeAllocator.deductBroker(asset, 3.0, 3.0);
        double misc = LotChargeAllocator.deductMisc(asset, 3.0, 3.0);

        // Then
        assertCanonical("broker", 10.00, broker);
        assertCanonical("misc", 4.00, misc);
    }

    @Test
    @DisplayName("an exhausted lot yields nothing further, however often it is asked")
    void deduct_whenAlreadyFullyAllocated_returnsZero() {
        // Given
        AssetEntity asset = lot(3.0, 10.0, 0.0);
        LotChargeAllocator.deductBroker(asset, 3.0, 3.0);

        // When / Then
        assertCanonical("second attempt", 0.0, LotChargeAllocator.deductBroker(asset, 3.0, 3.0));
        assertCanonical("total still what was paid", 10.00, asset.getAllocatedBuyCharges());
    }

    @Test
    @DisplayName("a zero-quantity lot allocates nothing rather than dividing by zero")
    void deduct_whenTheLotHasNoQuantity_isZero() {
        // Given
        AssetEntity asset = lot(0.0, 10.0, 0.0);

        // When / Then
        assertCanonical("no quantity to spread over", 0.0, LotChargeAllocator.deductBroker(asset, 1.0, 0.0));
    }

    @Test
    @DisplayName("broker and misc are tracked independently")
    void deduct_tracksBrokerAndMiscSeparately() {
        // Given
        AssetEntity asset = lot(2.0, 10.0, 4.0);

        // When
        LotChargeAllocator.deductBroker(asset, 1.0, 2.0);

        // Then
        assertCanonical("broker half taken", 5.00, asset.getAllocatedBuyCharges());
        assertCanonical("misc untouched", 0.0, asset.getAllocatedBuyMiscCharges());
    }
}
