package com.thiru.wealthlens.taxengine.input;

import static com.thiru.wealthlens.taxengine.input.InputRefusals.refusal;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.util.Optional;
import java.util.stream.Stream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

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
                Optional.of(CarEngine.ofCapacity(1500)), true, true, 12, Optional.empty());

        // Then
        assertThat(car.months()).isEqualTo(12);
    }

    @Test
    void construct_whenThirteenMonths_isRefused() {
        // When / Then
        assertThatThrownBy(() -> vehicle(VehicleOwner.EMPLOYER_CAR, VehicleUse.PARTLY_OFFICIAL,
                        Optional.of(CarEngine.ofCapacity(1500)), false, false, 13, Optional.empty()))
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
    void construct_whenAHigherOfficialUseIsClaimedWithoutRecords_isRefused() {
        // When / Then
        assertThatThrownBy(() -> new OfficialUseRecord(false, Optional.of(Money.of("10000"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("certificate");
    }

    @Test
    void carEngine_whenElectricWithACapacity_isRefused() {
        // When / Then — an electric car has no cubic capacity (D-113 C5a)
        assertThatThrownBy(() -> new CarEngine(true, Optional.of(1500)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("electric");
    }

    @Test
    void carEngine_whenNotElectricWithoutACapacity_isRefused() {
        // When / Then — the column turns on it (D-257)
        assertThatThrownBy(() -> new CarEngine(false, Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cubic capacity");
    }

    @Test
    void carEngine_whenTheCapacityIsNotPositive_isRefused() {
        // When / Then
        assertThatThrownBy(() -> CarEngine.ofCapacity(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
    }

    /** Each constructor argument made bad on its own, the rest a valid employer car (W-240a). */
    static Stream<Arguments> refusals() {
        VehicleOwner car = VehicleOwner.EMPLOYER_CAR;
        VehicleUse partly = VehicleUse.PARTLY_OFFICIAL;
        Optional<CarEngine> engine = Optional.of(CarEngine.ofCapacity(1500));
        OfficialUseRecord none = OfficialUseRecord.none();
        Money negative = Money.of("-1");
        String needs = "An employer vehicle needs its owner, use, expenditure, charge and records";
        String amounts = "Vehicle amounts may not be negative";
        return Stream.of(
                refusal("owner null", () -> new EmployerVehicle(0, null, partly, engine, false, false, 12, Money.ZERO,
                        Optional.empty(), Money.ZERO, none), needs),
                refusal("use null", () -> new EmployerVehicle(0, car, null, engine, false, false, 12, Money.ZERO,
                        Optional.empty(), Money.ZERO, none), needs),
                refusal("engine null", () -> new EmployerVehicle(0, car, partly, null, false, false, 12, Money.ZERO,
                        Optional.empty(), Money.ZERO, none), needs),
                refusal("runningExpenditure null", () -> new EmployerVehicle(0, car, partly, engine, false, false, 12,
                        null, Optional.empty(), Money.ZERO, none), needs),
                refusal("cost null", () -> new EmployerVehicle(0, car, partly, engine, false, false, 12, Money.ZERO,
                        null, Money.ZERO, none), needs),
                refusal("chargedToEmployee null", () -> new EmployerVehicle(0, car, partly, engine, false, false, 12,
                        Money.ZERO, Optional.empty(), null, none), needs),
                refusal("officialUse null", () -> new EmployerVehicle(0, car, partly, engine, false, false, 12,
                        Money.ZERO, Optional.empty(), Money.ZERO, null), needs),
                refusal("employment negative", () -> new EmployerVehicle(-1, car, partly, engine, false, false, 12,
                        Money.ZERO, Optional.empty(), Money.ZERO, none), "An employment position may not be negative"),
                refusal("months negative", () -> new EmployerVehicle(0, car, partly, engine, false, false, -1,
                        Money.ZERO, Optional.empty(), Money.ZERO, none), "-1 is not a number of months in a tax year"),
                refusal("a conveyance with an engine", () -> new EmployerVehicle(0,
                        VehicleOwner.EMPLOYEE_OTHER_CONVEYANCE, partly, engine, false, false, 12, Money.ZERO,
                        Optional.empty(), Money.ZERO, none), "An other automotive conveyance has one Table II column"),
                refusal("runningExpenditure negative", () -> new EmployerVehicle(0, car, partly, engine, false, false,
                        12, negative, Optional.empty(), Money.ZERO, none), amounts),
                refusal("chargedToEmployee negative", () -> new EmployerVehicle(0, car, partly, engine, false, false,
                        12, Money.ZERO, Optional.empty(), negative, none), amounts),
                refusal("cost negative", () -> new EmployerVehicle(0, car, partly, engine, false, false, 12,
                        Money.ZERO, Optional.of(negative), Money.ZERO, none), amounts));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("refusals")
    void construct_whenOneFieldIsBad_isRefusedNamingIt(String badField, ThrowingCallable construct, String message) {
        // When / Then
        assertThatThrownBy(construct).isInstanceOf(IllegalArgumentException.class).hasMessageContaining(message);
    }
}
