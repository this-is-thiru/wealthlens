package com.thiru.wealthlens.taxengine.input;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** What a vehicle entry accepts and refuses (D-113). */
class EmployerVehicleTest {

    private static EmployerVehicle vehicle(VehicleOwner owner, VehicleUse use, Optional<CarEngine> engine,
            boolean chauffeur, boolean employeeMeetsPrivateRunning, int months, Optional<Money> cost) {
        return new EmployerVehicle(0, owner, use, engine, chauffeur, employeeMeetsPrivateRunning, months, Money.ZERO,
                cost, Money.ZERO, OfficialUseRecord.none());
    }

    @Test
    void construct_whenAnEmployerCarForTwelveMonths_isAccepted() {
        // When
        EmployerVehicle car = vehicle(VehicleOwner.EMPLOYER_CAR, VehicleUse.PARTLY_OFFICIAL,
                Optional.of(CarEngine.UP_TO_1600_CC), true, true, 12, Optional.empty());

        // Then
        assertThat(car.months()).isEqualTo(12);
    }

    @Test
    void construct_whenThirteenMonths_isRefused() {
        // When / Then
        assertThatThrownBy(() -> vehicle(VehicleOwner.EMPLOYER_CAR, VehicleUse.PARTLY_OFFICIAL,
                        Optional.of(CarEngine.UP_TO_1600_CC), false, false, 13, Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("13");
    }

    @Test
    void construct_whenACarHasNoEngine_isRefused() {
        // When / Then
        assertThatThrownBy(() -> vehicle(VehicleOwner.EMPLOYEE_CAR, VehicleUse.PARTLY_OFFICIAL, Optional.empty(),
                        false, false, 12, Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("engine");
    }

    @Test
    void construct_whenTheEmployeesOwnCarIsWhollyPrivate_isRefused() {
        // When / Then
        assertThatThrownBy(() -> vehicle(VehicleOwner.EMPLOYEE_CAR, VehicleUse.WHOLLY_PRIVATE,
                        Optional.of(CarEngine.UP_TO_1600_CC), false, false, 12, Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("wholly private");
    }

    @Test
    void construct_whenACostIsEnteredForTheEmployeesVehicle_isRefused() {
        // When / Then
        assertThatThrownBy(() -> vehicle(VehicleOwner.EMPLOYEE_CAR, VehicleUse.PARTLY_OFFICIAL,
                        Optional.of(CarEngine.UP_TO_1600_CC), false, false, 12, Optional.of(Money.of("500000"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cost");
    }

    @Test
    void construct_whenAHigherOfficialUseIsClaimedWithoutRecords_isRefused() {
        // When / Then
        assertThatThrownBy(() -> new OfficialUseRecord(false, Optional.of(Money.of("10000"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("certificate");
    }
}
