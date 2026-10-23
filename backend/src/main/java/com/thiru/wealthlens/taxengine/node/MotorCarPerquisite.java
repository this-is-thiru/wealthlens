package com.thiru.wealthlens.taxengine.node;

import com.thiru.wealthlens.taxengine.input.CarEngine;
import com.thiru.wealthlens.taxengine.input.EmployerVehicle;
import com.thiru.wealthlens.taxengine.input.VehicleOwner;
import com.thiru.wealthlens.taxengine.input.VehicleUse;
import com.thiru.wealthlens.taxengine.pipeline.NodeContext;
import com.thiru.wealthlens.taxengine.rules.cap.CapContext;
import com.thiru.wealthlens.taxengine.rules.cap.CapResult;
import com.thiru.wealthlens.taxengine.rules.condition.ItemFacts;
import com.thiru.wealthlens.taxengine.rules.definition.RuleDefinitions;
import com.thiru.wealthlens.taxengine.rules.definition.VehicleComputation;
import com.thiru.wealthlens.taxengine.rules.definition.VehiclePath;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The value of a vehicle an employer provides or runs, per calendar month (S4 r.15(3) Table II; Rules 1962 r.3(2);
 * D-113), for {@link PerquisiteNode}.
 *
 * <p>Whom it is a perquisite for is the Act's, not the Table's (OQ-10): the employer's own or hired car is a "benefit
 * or amenity" — salary only for a director or substantial-interest holder of a company employer, or an employee whose
 * salary by way of monetary payment exceeds the prescribed amount (S2 s.17(1)(c); 1961 s.17(2)(iii)); the employer
 * running the employee's own vehicle meets the employee's obligation, salary for everyone (s.17(1)(f); 1961
 * s.17(2)(iv)) — {@code PERQ-SPECIFIED-EMPLOYEE}'s condition on the vehicle's arrangement (W-154e1). Which Table II
 * computation values a vehicle is the {@link VehiclePath} of the row of {@code RS-PERQ-CAR-PATH} its facts select
 * (W-154e2): a wholly official vehicle is nil only with the employer's records, and with none no row selects it, so it
 * is refused (D-113 C7). Where the
 * employer lets one employee use several of its cars otherwise than wholly officially, the first entered is valued at
 * Sl. 1(c)(i) and the rest at Sl. 1(b) (r.15(3)(b); r.3(2) proviso).
 */
final class MotorCarPerquisite {

    static final Set<RuleSetId> RULES = Set.of(RuleSetIds.RS_PERQ_CAR_PATH, RuleSetIds.RS_PERQ_CAR_PARTLY,
            RuleSetIds.RS_PERQ_CAR_OWNRUN, RuleSetIds.RS_PERQ_CAR_CHAUFFEUR, RuleSetIds.RS_PERQ_CAR_PRIVATE,
            RuleSetIds.RS_PERQ_CAR_EMPLOYEE, RuleSetIds.RS_PERQ_CAR_CONVEYANCE);

    /** What a {@code RS-PERQ-CAR-PATH} row is chosen by (D-330, W-154e2). */
    private static final ItemFactId OWNER = new ItemFactId("vehicle.owner");
    private static final ItemFactId USE = new ItemFactId("vehicle.use");
    private static final ItemFactId EMPLOYEE_MEETS_PRIVATE_RUNNING = new ItemFactId(
            "vehicle.employee.meets.private.running");
    private static final ItemFactId JOURNEYS_AND_CERTIFICATE_KEPT = new ItemFactId(
            "vehicle.journeys.and.certificate.kept");

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

    /** One line per vehicle, numbered from {@code firstSequence}: valued on its path, or nil where the path says so. */
    static List<TraceLine> lines(NodeContext ctx, int firstSequence) {
        List<EmployerVehicle> vehicles = ctx.inputs().entered().vehicles();
        List<TraceLine> lines = new ArrayList<>();
        for (int i = 0; i < vehicles.size(); i++) {
            EmployerVehicle vehicle = vehicles.get(i);
            requireEnteredEmployment(ctx, vehicle);
            int sequence = firstSequence + lines.size();
            int position = i + 1;
            ItemFacts facts = factsOf(vehicle);
            RuleRow path = ctx.optionalRuleOf(RuleSetIds.RS_PERQ_CAR_PATH, facts)
                    .orElseThrow(() -> unvalued(ctx, position, facts));
            VehicleComputation computation = RuleDefinitions.require(TemporallyApportionedCap.governingVersion(path,
                    ctx.inputs().temporal()).definition(), VehiclePath.class, path.id()).computation();
            if (computation == VehicleComputation.NOT_A_PERQUISITE) {
                lines.add(ctx.traceLine(sequence, path.id(), StepKind.INCOME, vehicle.runningExpenditure(), Money.ZERO)
                        .withLimitedBy("not a perquisite: excluded by " + path.id()));
                continue;
            }
            if (SpecifiedEmployee.gates(ctx, ItemFacts.of(SpecifiedEmployee.ARRANGEMENT,
                            vehicle.owner().arrangement().name()))
                    && !SpecifiedEmployee.holdsFor(ctx, vehicle.employment())) {
                lines.add(SpecifiedEmployee.notSpecifiedLine(ctx, sequence));
                continue;
            }
            lines.add(valued(ctx, vehicle, severalCars(vehicles, i, computation), sequence));
        }
        return lines;
    }

    /** The arithmetic each computation names, on the valuing rows it reads. */
    private static TraceLine valued(NodeContext ctx, EmployerVehicle vehicle, VehicleComputation computation,
            int sequence) {
        return switch (computation) {
            case NOT_A_PERQUISITE -> throw new IllegalStateException(
                    "A vehicle on " + computation + " has no value to compute; its nil line is written first");
            case PER_MONTH_EMPLOYER_RUNNING -> partly(ctx, vehicle, RuleSetIds.RS_PERQ_CAR_PARTLY, sequence);
            case PER_MONTH_EMPLOYEE_RUNNING -> partly(ctx, vehicle, RuleSetIds.RS_PERQ_CAR_OWNRUN, sequence);
            case RUNNING_AND_WEAR_LESS_CHARGE -> whollyPrivate(ctx, vehicle, sequence);
            case OWN_CAR_EXPENDITURE_LESS_TABLE_AMOUNT -> ownVehicle(ctx, vehicle,
                    ctx.ruleIdOf(RuleSetIds.RS_PERQ_CAR_EMPLOYEE), tableOfficialUse(ctx, vehicle), sequence);
            case OWN_CONVEYANCE_EXPENDITURE -> ownVehicle(ctx, vehicle,
                    ctx.ruleIdOf(RuleSetIds.RS_PERQ_CAR_CONVEYANCE), Money.ZERO, sequence);
        };
    }

    /**
     * The several-cars proviso, until W-154e3 states it as data (D-330 C2a of W-154e2): where more than one of an
     * employment's employer cars is used otherwise than wholly officially, the first entered is valued per month with
     * the employer running it and the rest at running and wear and tear, whatever their own path.
     */
    private static VehicleComputation severalCars(List<EmployerVehicle> vehicles, int index,
            VehicleComputation computation) {
        boolean employerComputation = computation == VehicleComputation.PER_MONTH_EMPLOYER_RUNNING
                || computation == VehicleComputation.PER_MONTH_EMPLOYEE_RUNNING
                || computation == VehicleComputation.RUNNING_AND_WEAR_LESS_CHARGE;
        List<Integer> sharing = carsNotWhollyOfficial(vehicles, vehicles.get(index).employment());
        if (!employerComputation || sharing.size() <= 1) {
            return computation;
        }
        return sharing.getFirst() == index ? VehicleComputation.PER_MONTH_EMPLOYER_RUNNING
                : VehicleComputation.RUNNING_AND_WEAR_LESS_CHARGE;
    }

    /** The vehicle's facts as the path rows and the engine columns read them. */
    private static ItemFacts factsOf(EmployerVehicle vehicle) {
        Map<ItemFactId, String> codes = new HashMap<>(Map.of(OWNER, vehicle.owner().name(), USE, vehicle.use().name(),
                EMPLOYEE_MEETS_PRIVATE_RUNNING, Boolean.toString(vehicle.employeeMeetsPrivateRunning()),
                JOURNEYS_AND_CERTIFICATE_KEPT, Boolean.toString(vehicle.officialUse().journeysAndCertificateKept())));
        vehicle.engine().ifPresent(engine -> codes.putAll(engineFacts(engine).codes()));
        Map<ItemFactId, Long> quantities = vehicle.engine().map(engine -> engineFacts(engine).quantities())
                .orElse(Map.of());
        return new ItemFacts(codes, quantities, Map.of());
    }

    /** A vehicle no path row of the year reaches — wholly official without the employer's records among them. */
    private static IllegalArgumentException unvalued(NodeContext ctx, int position, ItemFacts facts) {
        String described = facts.codes().entrySet().stream()
                .map(fact -> fact.getKey() + "=" + fact.getValue())
                .sorted()
                .collect(Collectors.joining(", ", "{", "}"));
        return new IllegalArgumentException("Vehicle " + position + ": no row of " + RuleSetIds.RS_PERQ_CAR_PATH
                + " values it in TY " + ctx.inputs().taxYear().label() + " — its facts " + described);
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
