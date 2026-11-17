package com.thiru.wealthlens.taxplanning.documents.reconcile;

import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.util.List;

/**
 * One figure the certificate prints and the computation also produces (M4.2).
 *
 * @param printed the certificate's row, by its path in a reading; summed over the year's certificates for a table's Total
 *     where several employers deducted (W-130c)
 * @param difference the certificate's figure less the computation's
 * @param reasons why they differ, or why they are paired (TAXSUM); none where nothing explains a difference, several where
 *     more than one cause does (T8-1, W-130c)
 */
public record ReconciliationLine(String printed, Money documentValue, Money computedValue, Money difference,
        List<ReconciliationReason> reasons) {

    public ReconciliationLine {
        if (printed == null || documentValue == null || computedValue == null || difference == null || reasons == null) {
            throw new IllegalArgumentException("A reconciliation line needs its row, both figures, their difference and its reasons or none");
        }
        reasons = List.copyOf(reasons);
    }
}
