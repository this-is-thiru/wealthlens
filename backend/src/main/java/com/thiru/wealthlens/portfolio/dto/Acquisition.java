package com.thiru.wealthlens.portfolio.dto;

import com.thiru.wealthlens.corporate.dto.enums.CorporateActionType;
import com.thiru.wealthlens.portfolio.dto.enums.AcquisitionMode;
import java.util.Collection;
import java.util.Optional;

/**
 * How a lot came to be held, as recorded — the mode and, for a corporate action, which one, carried as itself so a
 * type {@code portfolio} learns later reaches the tax side unchanged (W-088f, D-249).
 *
 * @param mode how the lot was acquired
 * @param corporateAction the corporate action that created the lot; present exactly when {@code mode} is
 *     {@code CORPORATE_ACTION}
 */
public record Acquisition(AcquisitionMode mode, Optional<CorporateActionType> corporateAction) {

    /** A lot whose record says nothing of how it was acquired. */
    public static final Acquisition UNRECORDED = new Acquisition(AcquisitionMode.UNRECORDED, Optional.empty());

    public Acquisition {
        if (mode == null || corporateAction == null) {
            throw new IllegalArgumentException("An acquisition needs its mode; pass Optional.empty() for no corporate"
                    + " action");
        }
        if (corporateAction.isPresent() != (mode == AcquisitionMode.CORPORATE_ACTION)) {
            throw new IllegalArgumentException("A corporate action is named exactly when the lot was acquired by one,"
                    + " but the mode was " + mode + " and the corporate action " + corporateAction);
        }
    }

    /** The acquisition of a lot whose own record carries {@code corporateActionType}, absent for a purchase. */
    public static Acquisition of(CorporateActionType corporateActionType) {
        return corporateActionType == null
                ? new Acquisition(AcquisitionMode.PURCHASE, Optional.empty())
                : new Acquisition(AcquisitionMode.CORPORATE_ACTION, Optional.of(corporateActionType));
    }

    /** As stored on a {@code trade_outcomes} row; a row without a mode was written before it was kept. */
    public static Acquisition stored(AcquisitionMode mode, CorporateActionType corporateActionType) {
        return mode == null ? UNRECORDED : new Acquisition(mode, Optional.ofNullable(corporateActionType));
    }

    /** The one acquisition merged buys share, or {@link #UNRECORDED} when they differ. */
    public static Acquisition common(Collection<Acquisition> acquisitions) {
        return acquisitions.stream().distinct().count() == 1 ? acquisitions.iterator().next() : UNRECORDED;
    }
}
