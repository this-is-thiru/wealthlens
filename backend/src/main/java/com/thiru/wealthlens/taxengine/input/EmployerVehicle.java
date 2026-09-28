package com.thiru.wealthlens.taxengine.input;

import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.util.Optional;

/**
 * A vehicle an employer provides, or runs at its cost, in the year (S2 s.17(1)(c), (f); S4 r.15(3) Table II; 1961
 * s.17(2)(iii), (iv); Rules 1962 r.3(2); D-113).
 *
 * <p>What is entered is what Table II reads, per calendar month: whose vehicle it is, what it is used for, the
 * engine, a chauffeur, and for how many calendar months — the Rules do not say how a part month counts, so the
 * taxpayer states the months (C6). The actual figures are the employer's: its running and maintenance expenditure
 * (a chauffeur's pay included), the car's cost for wear and tear, and what it charged the employee.
 *
 * @param employment which entered employment provides it, by position
 * @param owner whose vehicle it is
 * @param use what it is used for
 * @param engine the car's engine; empty for an other automotive conveyance, whose Table row has one column
 * @param chauffeur whether the employer also provides a chauffeur
 * @param employeeMeetsPrivateRunning for an employer's car used partly officially, whether the employee meets the
 *     running and maintenance for private use (Sl. 1(c)(ii)) rather than the employer (Sl. 1(c)(i))
 * @param months the calendar months in the tax year the vehicle was provided, 0 to 12
 * @param runningExpenditure what the employer spent on, or reimbursed for, running and maintenance, a chauffeur's
 *     pay included
 * @param cost the actual cost of an employer's car, for wear and tear
 * @param chargedToEmployee what the employer charged the employee for the use
 * @param officialUse the employer's records of official use
 */
public record EmployerVehicle(
        int employment,
        VehicleOwner owner,
        VehicleUse use,
        Optional<CarEngine> engine,
        boolean chauffeur,
        boolean employeeMeetsPrivateRunning,
        int months,
        Money runningExpenditure,
        Optional<Money> cost,
        Money chargedToEmployee,
        OfficialUseRecord officialUse) {

    public EmployerVehicle {
        if (owner == null || use == null || engine == null || runningExpenditure == null || cost == null
                || chargedToEmployee == null || officialUse == null) {
            throw new IllegalArgumentException("An employer vehicle needs its owner, use, expenditure, charge and"
                    + " records; pass Optional.empty() for a fact not given");
        }
        if (employment < 0) {
            throw new IllegalArgumentException("An employment position may not be negative, but was " + employment);
        }
        if (months < 0 || months > 12) {
            throw new IllegalArgumentException(
                    "A vehicle is valued per calendar month; " + months + " is not a number of months in a tax year");
        }
        requireWhatTheRowReads(owner, use, engine, chauffeur, employeeMeetsPrivateRunning);
        requireAmounts(owner, runningExpenditure, cost, chargedToEmployee);
    }

    /** A fact Table II's row reads must be given, and one it does not read is refused rather than ignored. */
    private static void requireWhatTheRowReads(VehicleOwner owner, VehicleUse use, Optional<CarEngine> engine,
            boolean chauffeur, boolean employeeMeetsPrivateRunning) {
        boolean conveyance = owner == VehicleOwner.EMPLOYEE_OTHER_CONVEYANCE;
        if (conveyance == engine.isPresent()) {
            throw new IllegalArgumentException(conveyance
                    ? "An other automotive conveyance has one Table II column; enter no engine"
                    : "A motor car is valued by its engine; state it");
        }
        if (owner != VehicleOwner.EMPLOYER_CAR && use == VehicleUse.WHOLLY_PRIVATE) {
            throw new IllegalArgumentException("Table II values an employee's own vehicle used wholly or partly"
                    + " officially; wholly private use of one run at the employer's cost is not in the Table");
        }
        if (employeeMeetsPrivateRunning && (owner != VehicleOwner.EMPLOYER_CAR || use != VehicleUse.PARTLY_OFFICIAL)) {
            throw new IllegalArgumentException("Who meets the private running is asked only of an employer's car used"
                    + " partly officially (Table II Sl. 1(c))");
        }
        if (chauffeur && conveyance) {
            throw new IllegalArgumentException("Table II gives a chauffeur's amount only for a motor car");
        }
    }

    private static void requireAmounts(VehicleOwner owner, Money runningExpenditure, Optional<Money> cost,
            Money chargedToEmployee) {
        if (runningExpenditure.isLessThan(Money.ZERO) || chargedToEmployee.isLessThan(Money.ZERO)
                || cost.filter(amount -> amount.isLessThan(Money.ZERO)).isPresent()) {
            throw new IllegalArgumentException("Vehicle amounts may not be negative");
        }
        if (cost.isPresent() && owner != VehicleOwner.EMPLOYER_CAR) {
            throw new IllegalArgumentException("Wear and tear is on the employer's car; an employee's vehicle has no"
                    + " cost to enter");
        }
    }
}
