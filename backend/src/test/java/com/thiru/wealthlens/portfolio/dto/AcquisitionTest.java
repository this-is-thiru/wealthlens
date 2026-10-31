package com.thiru.wealthlens.portfolio.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.corporate.dto.enums.CorporateActionType;
import com.thiru.wealthlens.portfolio.dto.enums.AcquisitionMode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** How a lot came to be held, carried as recorded (W-088f, D-249). */
class AcquisitionTest {

    @Test
    void of_whenTheLotCarriesNoCorporateAction_isAPurchase() {
        // When / Then
        assertThat(Acquisition.of(null)).isEqualTo(new Acquisition(AcquisitionMode.PURCHASE, Optional.empty()));
    }

    @Test
    void of_whenTheLotCarriesAnyCorporateAction_carriesThatTypeAsItself() {
        // When / Then — not only the two portfolio creates holdings for today; the tax side decides what each means
        assertThat(Acquisition.of(CorporateActionType.BONUS).corporateAction()).contains(CorporateActionType.BONUS);
        assertThat(Acquisition.of(CorporateActionType.STOCK_SPLIT))
                .isEqualTo(new Acquisition(AcquisitionMode.CORPORATE_ACTION, Optional.of(CorporateActionType.STOCK_SPLIT)));
    }

    @Test
    void stored_whenTheRowHasNoMode_isUnrecorded() {
        // When / Then — a row written before the mode was kept
        assertThat(Acquisition.stored(null, null)).isEqualTo(Acquisition.UNRECORDED);
    }

    @Test
    void common_whenMergedBuysShareAnAcquisition_isThatAcquisition() {
        // When / Then
        assertThat(Acquisition.common(List.of(Acquisition.of(CorporateActionType.BONUS),
                Acquisition.of(CorporateActionType.BONUS)))).isEqualTo(Acquisition.of(CorporateActionType.BONUS));
    }

    @Test
    void common_whenMergedBuysDiffer_isUnrecorded() {
        // When / Then — a same-day bonus and purchase merged into one lot cannot be one or the other
        assertThat(Acquisition.common(List.of(Acquisition.of(CorporateActionType.BONUS), Acquisition.of(null))))
                .isEqualTo(Acquisition.UNRECORDED);
    }

    @Test
    void acquisition_whenACorporateActionIsNamedForAPurchase_isRefused() {
        // When / Then
        assertThatThrownBy(() -> new Acquisition(AcquisitionMode.PURCHASE, Optional.of(CorporateActionType.BONUS)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exactly when");
    }
}
