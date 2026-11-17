package com.thiru.wealthlens.taxplanning.documents.reconcile;

import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.util.Optional;

/**
 * One figure the certificate prints and the computation also produces (M4.2).
 *
 * @param printed the certificate's row, by its path in a reading
 * @param difference the certificate's figure less the computation's
 * @param reason why they differ, or why they are paired (TAXSUM); none where nothing explains a difference
 */
public record ReconciliationLine(String printed, Money documentValue, Money computedValue, Money difference,
        Optional<ReconciliationReason> reason) {

    public ReconciliationLine {
        if (printed == null || documentValue == null || computedValue == null || difference == null || reason == null) {
            throw new IllegalArgumentException("A reconciliation line needs its row, both figures, their difference and its reason or none");
        }
    }
}
