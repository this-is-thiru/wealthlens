package com.thiru.wealthlens.taxengine.node;

import com.thiru.wealthlens.taxengine.input.CarEngine;
import com.thiru.wealthlens.taxengine.input.EmployerVehicle;
import com.thiru.wealthlens.taxengine.input.VehicleOwner;
import com.thiru.wealthlens.taxengine.input.VehicleUse;
import com.thiru.wealthlens.taxengine.pipeline.NodeContext;
import com.thiru.wealthlens.taxengine.rules.cap.CapContext;
import com.thiru.wealthlens.taxengine.rules.cap.CapResult;
import com.thiru.wealthlens.taxengine.rules.condition.ItemFacts;
import com.thiru.wealthlens.taxengine.rules.generated.RuleSetIds;
import com.thiru.wealthlens.taxengine.rules.id.BaseId;
import com.thiru.wealthlens.taxengine.rules.id.ItemFactId;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.id.RuleSetId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.RuleRow;
import com.thiru.wealthlens.taxengine.rules.pack.TemporallyApportionedCap;
import com.thiru.wealthlens.taxengine.rules.temporal.TemporalContext;
import com.thiru.wealthlens.taxengine.trace.StepKind;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The value of a vehicle an employer provides or runs, per calendar month (S4 r.15(3) Table II; Rules 1962 r.3(2);
 * D-113), for {@link PerquisiteNode}.
 *
 * <p>Whom it is a perquisite for is the Act's, not the Table's (OQ-10): the employer's own or hired car is a "benefit
 * or amenity" — salary only for a director or substantial-interest holder of a company employer, or an employee whose
 * salary by way of monetary payment exceeds the prescribed amount (S2 s.17(1)(c); 1961 s.17(2)(iii)); the employer
 * running the employee's own vehicle meets the employee's obligation, salary for everyone (s.17(1)(f); 1961
 * s.17(2)(iv)). A wholly official vehicle has no value only with the employer's records, and is refused without them
 * (D-113 C7). Where the employer lets one employee use several of its cars otherwise than wholly officially, the
 * first entered is valued at Sl. 1(c)(i) and the rest at Sl. 1(b) (r.15(3)(b); r.3(2) proviso).
 */
final class MotorCarPerquisite {

    static final Set<RuleSetId> RULES = Set.of(RuleSetIds.RS_PERQ_CAR_PARTLY, RuleSetIds.RS_PERQ_CAR_OWNRUN,
            RuleSetIds.RS_PERQ_CAR_CHAUFFEUR, RuleSetIds.RS_PERQ_CAR_PRIVATE, RuleSetIds.RS_PERQ_CAR_EMPLOYEE,
            RuleSetIds.RS_PERQ_CAR_CONVEYANCE);

    /** What a Table II column's rows are chosen by: whether the car is electric, and otherwise its capacity (D-257). */
    private static final ItemFactId ELECTRIC = new ItemFactId("vehicle.engine.electric");
    private static final ItemFactId CUBIC_CAPACITY_CC = new ItemFactId("vehicle.engine.cc");

    private static final BaseId MONTH = new BaseId("MONTH");
    private static final BaseId CAR_COST = new BaseId("CAR_COST");
    private static final BaseId EXPENDITURE = new BaseId("EXPENDITURE");
    private static final BaseId TABLE_OFFICIAL_USE = new BaseId("TABLE_OFFICIAL_USE");
    private static final BaseId CLAIMED_OFFICIAL_USE = new BaseId("CLAIMED_OFFICIAL_USE");

    private MotorCarPerquisite() {
    }

    /** One line per vehicle not wholly official, numbered from {@code firstSequence}. */
    static List<TraceLine> lines(NodeContext ctx, int firstSequence) {
        List<EmployerVehicle> vehicles = ctx.inputs().entered().vehicles();
        List<TraceLine> lines = new ArrayList<>();
        for (int i = 0; i < vehicles.size(); i++) {
            EmployerVehicle vehicle = vehicles.get(i);
            requireEnteredEmployment(ctx, vehicle);
            if (vehicle.use() == VehicleUse.WHOLLY_OFFICIAL) {
                requireOfficialRecords(vehicle);
                continue;
            }
            int sequence = firstSequence + lines.size();
            lines.add(switch (vehicle.owner()) {
                case EMPLOYER_CAR -> employerCar(ctx, vehicles, i, sequence);
                case EMPLOYEE_CAR -> ownVehicle(ctx, vehicle, ctx.ruleIdOf(RuleSetIds.RS_PERQ_CAR_EMPLOYEE),
                        tableOfficialUse(ctx, vehicle), sequence);
                case EMPLOYEE_OTHER_CONVEYANCE -> ownVehicle(ctx, vehicle, ctx.ruleIdOf(RuleSetIds.RS_PERQ_CAR_CONVEYANCE), Money.ZERO,
                        sequence);
            });
        }
        return lines;
    }

    /** Sl. 1: nil for an employee who is not a specified one; else by use, or by the several-cars proviso. */
    private static TraceLine employerCar(NodeContext ctx, List<EmployerVehicle> vehicles, int index, int sequence) {
        EmployerVehicle car = vehicles.get(index);
        if (!SpecifiedEmployee.holdsFor(ctx, car.employment())) {
            return SpecifiedEmployee.notSpecifiedLine(ctx, sequence);
        }
        List<Integer> sharing = carsNotWhollyOfficial(vehicles, car.employment());
        if (sharing.size() > 1) {
            return sharing.getFirst() == index ? partly(ctx, car, RuleSetIds.RS_PERQ_CAR_PARTLY, sequence)
                    : whollyPrivate(ctx, car, sequence);
        }
        if (car.use() == VehicleUse.WHOLLY_PRIVATE) {
            return whollyPrivate(ctx, car, sequence);
        }
        return partly(ctx, car, car.employeeMeetsPrivateRunning() ? RuleSetIds.RS_PERQ_CAR_OWNRUN
                : RuleSetIds.RS_PERQ_CAR_PARTLY, sequence);
    }

    /** Sl. 1(c)(i) or (ii) for the months, plus the chauffeur's amount. */
    private static TraceLine partly(NodeContext ctx, EmployerVehicle car, RuleSetId column, int sequence) {
        requireNoCharge(car);
        RuleId ruleId = rowFor(ctx, column, car);
        Money value = perMonth(ctx, ruleId, car.months());
        String bound = ruleId + " for " + car.months() + " months";
        if (car.chauffeur()) {
            value = value.plus(perMonth(ctx, ctx.ruleIdOf(RuleSetIds.RS_PERQ_CAR_CHAUFFEUR), car.months()));
            bound += ", plus the chauffeur's amount";
        }
        return ctx.traceLine(sequence, ruleId, StepKind.INCOME, car.runningExpenditure(), value).withLimitedBy(bound);
    }

    /** Sl. 1(b): the running, plus wear and tear on the cost for the months, less the charge. */
    private static TraceLine whollyPrivate(NodeContext ctx, EmployerVehicle car, int sequence) {
        Money cost = car.cost().orElseThrow(() -> new IllegalArgumentException("An employer's car valued at its"
                + " running and wear and tear (Table II Sl. 1(b)) needs its actual cost"));
        TemporalContext t = ctx.inputs().temporal();
        CapResult wearAndTear = TemporallyApportionedCap.apply(ctx.ruleOf(RuleSetIds.RS_PERQ_CAR_PRIVATE), t,
                CapContext.of(CAR_COST, cost.apportioned(car.months(), 12)));
        Money value = car.runningExpenditure().plus(wearAndTear.value()).minus(car.chargedToEmployee()).floorAtZero();
        return ctx.traceLine(sequence, ctx.ruleIdOf(RuleSetIds.RS_PERQ_CAR_PRIVATE), StepKind.INCOME,
                        car.runningExpenditure(), value)
                .withLimitedBy("running plus wear and tear " + wearAndTear.value() + " (" + wearAndTear.limitedBy()
                        + " for "
                        + car.months() + " months), less the charge " + car.chargedToEmployee());
    }

    /** Sl. 2(b) or 3(b): the expenditure less the Table's official use, or less a higher amount claimed. */
    private static TraceLine ownVehicle(NodeContext ctx, EmployerVehicle vehicle, RuleId ruleId,
            Money tableOfficialUse, int sequence) {
        requireNoCharge(vehicle);
        CapContext bases = new CapContext(Map.of(EXPENDITURE, vehicle.runningExpenditure(), TABLE_OFFICIAL_USE,
                tableOfficialUse, CLAIMED_OFFICIAL_USE, vehicle.officialUse().higherOfficialUse().orElse(Money.ZERO)),
                Map.of(), Map.of(MONTH, vehicle.months()));
        CapResult valued = TemporallyApportionedCap.apply(ctx.rule(ruleId), ctx.inputs().temporal(), bases);
        return ctx.traceLine(sequence, ruleId, StepKind.INCOME, vehicle.runningExpenditure(), valued.value())
                .withLimitedBy(valued.limitedBy());
    }

    /** The Sl. 1(c)(i) amount an employee's own car's expenditure is reduced by, with a chauffeur's if paid. */
    private static Money tableOfficialUse(NodeContext ctx, EmployerVehicle car) {
        Money official = perMonth(ctx, rowFor(ctx, RuleSetIds.RS_PERQ_CAR_PARTLY, car), car.months());
        return car.chauffeur() ? official.plus(perMonth(ctx, ctx.ruleIdOf(RuleSetIds.RS_PERQ_CAR_CHAUFFEUR), car.months())) : official;
    }

    /** The positions of the employer's cars one employment lets the employee use otherwise than wholly officially. */
    private static List<Integer> carsNotWhollyOfficial(List<EmployerVehicle> vehicles, int employment) {
        List<Integer> positions = new ArrayList<>();
        for (int i = 0; i < vehicles.size(); i++) {
            EmployerVehicle vehicle = vehicles.get(i);
            if (vehicle.owner() == VehicleOwner.EMPLOYER_CAR && vehicle.employment() == employment
                    && vehicle.use() != VehicleUse.WHOLLY_OFFICIAL) {
                positions.add(i);
            }
        }
        return positions;
    }

    /**
     * The column's row for the car's engine, as the rows' conditions say (D-253); an electric car has one only in a year
     * whose Table names it (D-113 C5a).
     */
    private static RuleId rowFor(NodeContext ctx, RuleSetId column, EmployerVehicle car) {
        CarEngine engine = car.engine().orElseThrow();
        String year = ctx.inputs().temporal().taxYear().label();
        return ctx.optionalRuleOf(column, engineFacts(engine)).map(RuleRow::id)
                .orElseThrow(() -> new IllegalArgumentException(engine.electric()
                        ? "Table II for " + year + " has no column for an electric car, which has no cubic capacity"
                                + " (Rules 1962 r.3(2)); it cannot be valued for this year (D-113)"
                        : "No row of " + column + " for " + year + " covers a car of " + engine.cubicCapacityCc().get()
                                + " cc; the year's columns are a pack defect (D-257)"));
    }

    /** The engine's facts as the rows' conditions read them; an electric car has no capacity to state. */
    private static ItemFacts engineFacts(CarEngine engine) {
        Map<ItemFactId, Long> capacity = engine.cubicCapacityCc()
                .map(cc -> Map.of(CUBIC_CAPACITY_CC, (long) cc))
                .orElse(Map.of());
        return new ItemFacts(Map.of(ELECTRIC, Boolean.toString(engine.electric())), capacity, Map.of());
    }

    private static Money perMonth(NodeContext ctx, RuleId ruleId, int months) {
        return TemporallyApportionedCap.apply(ctx.rule(ruleId), ctx.inputs().temporal(),
                new CapContext(Map.of(), Map.of(), Map.of(MONTH, months))).value();
    }

    private static void requireOfficialRecords(EmployerVehicle vehicle) {
        if (!vehicle.officialUse().journeysAndCertificateKept()) {
            throw new IllegalArgumentException("A vehicle used wholly officially has no value only where the employer"
                    + " keeps the journey details and gives its certificate (S4 r.15(3)(c); Rules 1962 r.3(2)(B));"
                    + " without them Table II states no value, so it cannot be valued (D-113)");
        }
    }

    private static void requireNoCharge(EmployerVehicle vehicle) {
        if (vehicle.chargedToEmployee().isGreaterThan(Money.ZERO)) {
            throw new IllegalArgumentException("Table II takes an amount charged to the employee off only an employer's"
                    + " car used exclusively privately (Sl. 1(b)); " + vehicle.chargedToEmployee()
                    + " was entered for a row that does not read it");
        }
    }

    private static void requireEnteredEmployment(NodeContext ctx, EmployerVehicle vehicle) {
        int entered = ctx.inputs().entered().employments().size();
        if (vehicle.employment() >= entered) {
            throw new IllegalArgumentException("The vehicle names employment " + vehicle.employment() + ", but "
                    + entered + " were entered");
        }
    }
}
