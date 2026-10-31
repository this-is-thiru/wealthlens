package com.thiru.wealthlens.taxengine.node;

import com.thiru.wealthlens.taxengine.input.EmployerVehicle;
import com.thiru.wealthlens.taxengine.pipeline.NodeContext;
import com.thiru.wealthlens.taxengine.rules.condition.ItemFacts;
import com.thiru.wealthlens.taxengine.rules.definition.VehicleComputation;
import com.thiru.wealthlens.taxengine.rules.generated.RuleSetIds;
import com.thiru.wealthlens.taxengine.rules.id.ItemFactId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.RuleRow;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Which facts a vehicle carries beyond its owner, use, months and running, as its Table II path reads them (W-158c,
 * D-359): one the path does not read would be ignored, so it is refused.
 *
 * <p>Who meets the private running is read where its value routes the vehicle to another {@code RS-PERQ-CAR-PATH}
 * row — the rows partition the vehicles, so the flip of {@code EntryFacts}' arrears relief is exact here. A chauffeur,
 * a cost, a charge and a higher official use are read by the arithmetic of the computation the path names, which is
 * {@link MotorCarPerquisite}'s, not the law's: which vehicle reaches which computation stays the rows' (W-154e2).
 */
final class VehicleFacts {

    /** A fact a computation's arithmetic may read, as a refusal names it. */
    private enum Read {
        CHAUFFEUR("a chauffeur"),
        COST("a cost"),
        CHARGE("an amount charged to the employee"),
        HIGHER_OFFICIAL_USE("a higher official use");

        private final String named;

        Read(String named) {
            this.named = named;
        }
    }

    private VehicleFacts() {
    }

    /**
     * Refuses a vehicle carrying a fact its path does not read.
     *
     * @param position the vehicle's place among those entered, from one, as a refusal names it
     * @param path the vehicle's {@code RS-PERQ-CAR-PATH} row
     * @param computation the computation that row names
     * @param facts the vehicle's facts as the path rows read them
     */
    static void requireWhatThePathReads(NodeContext ctx, EmployerVehicle vehicle, int position, RuleRow path,
            VehicleComputation computation, ItemFacts facts) {
        String what = "Vehicle " + position + ": " + path.id();
        if (vehicle.employeeMeetsPrivateRunning() && !routes(ctx, path, facts)) {
            throw new IllegalArgumentException(what + " is reached whoever meets the private running; who meets it"
                    + " would be ignored, so it is refused");
        }
        Set<Read> read = readBy(computation);
        refuseUnread(what, read, Read.CHAUFFEUR, vehicle.chauffeur());
        refuseUnread(what, read, Read.COST, vehicle.cost().isPresent());
        refuseUnread(what, read, Read.CHARGE, vehicle.chargedToEmployee().isGreaterThan(Money.ZERO));
        refuseUnread(what, read, Read.HIGHER_OFFICIAL_USE, vehicle.officialUse().higherOfficialUse().isPresent());
    }

    /** What each computation's arithmetic reads (MotorCarPerquisite.valued and the methods it calls). */
    private static Set<Read> readBy(VehicleComputation computation) {
        return switch (computation) {
            case NOT_A_PERQUISITE -> Set.of();
            case PER_MONTH_EMPLOYER_RUNNING, PER_MONTH_EMPLOYEE_RUNNING -> Set.of(Read.CHAUFFEUR);
            case RUNNING_AND_WEAR_LESS_CHARGE -> Set.of(Read.COST, Read.CHARGE);
            case OWN_CAR_EXPENDITURE_LESS_TABLE_AMOUNT -> Set.of(Read.CHAUFFEUR, Read.HIGHER_OFFICIAL_USE);
            case OWN_CONVEYANCE_EXPENDITURE -> Set.of(Read.HIGHER_OFFICIAL_USE);
        };
    }

    private static void refuseUnread(String what, Set<Read> read, Read fact, boolean given) {
        if (given && !read.contains(fact)) {
            throw new IllegalArgumentException(what + " does not read " + fact.named + "; it would be ignored, so it is"
                    + " refused");
        }
    }

    /** Whether the vehicle would reach another path, or none, had the employee not met the private running. */
    private static boolean routes(NodeContext ctx, RuleRow path, ItemFacts facts) {
        Map<ItemFactId, String> codes = new HashMap<>(facts.codes());
        codes.put(MotorCarPerquisite.EMPLOYEE_MEETS_PRIVATE_RUNNING, Boolean.toString(false));
        Optional<RuleRow> otherwise = ctx.optionalRuleOf(RuleSetIds.RS_PERQ_CAR_PATH,
                new ItemFacts(codes, facts.quantities(), facts.dates()));
        return !otherwise.map(RuleRow::id).equals(Optional.of(path.id()));
    }
}
