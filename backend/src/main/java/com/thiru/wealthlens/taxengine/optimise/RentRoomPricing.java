package com.thiru.wealthlens.taxengine.optimise;

import com.thiru.wealthlens.taxengine.api.RentRoom;
import com.thiru.wealthlens.taxengine.input.Employment;
import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.input.EnteredFacts;
import com.thiru.wealthlens.taxengine.input.RentDeclaration;
import com.thiru.wealthlens.taxengine.input.SalaryComponent;
import com.thiru.wealthlens.taxengine.input.Tenancy;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeRegistry;
import com.thiru.wealthlens.taxengine.pipeline.PipelineRunner;
import com.thiru.wealthlens.taxengine.pipeline.SalaryBases;
import com.thiru.wealthlens.taxengine.rules.cap.AmountInExcess;
import com.thiru.wealthlens.taxengine.rules.cap.CapContext;
import com.thiru.wealthlens.taxengine.rules.cap.CapShape;
import com.thiru.wealthlens.taxengine.rules.cap.LeastOf;
import com.thiru.wealthlens.taxengine.rules.cap.PercentOfBaseByLocation;
import com.thiru.wealthlens.taxengine.rules.generated.ComponentCodes;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.generated.RuleSetIds;
import com.thiru.wealthlens.taxengine.rules.id.BaseId;
import com.thiru.wealthlens.taxengine.rules.id.NodeId;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.id.RuleSetId;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.RulePack;
import com.thiru.wealthlens.taxengine.rules.pack.RuleRow;
import com.thiru.wealthlens.taxengine.rules.pack.TemporallyApportionedCap;
import com.thiru.wealthlens.taxengine.rules.regime.RegimeSpec;
import com.thiru.wealthlens.taxengine.rules.temporal.DateRange;
import com.thiru.wealthlens.taxengine.rules.temporal.proration.Days;
import com.thiru.wealthlens.taxengine.trace.StepKind;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * What rent is worth where the draft does not yet reflect all it could earn (W-115, D-670 C1a, C3a, C4a): house rent
 * allowance where the assessee receives it, else rent paid (s.134; 80GG), which s.134(4)(b) bars to an HRA payee.
 *
 * <p>The rent is filled in, not assumed: a second run with every rented home's rent raised to the filling rent, so no
 * limb about rent binds and the row's other limbs — the allowance received, a share of salary by location, a share of
 * total income, a sum per month — set the figure, as the pack holds them. With no home entered, one home is assumed for
 * the whole year: for HRA once per location band the row holds and once at {@link PercentOfBaseByLocation#ANY_OTHER_PLACE},
 * the location saving most reported; for s.134 with a declaration of the year's twelve months and no ownership bar.
 *
 * <p>The filling rent is gross total income plus the pay entered: more than any fraction of salary or total income a
 * threshold takes, plus the allowance. The rent needed is the figure plus the row's own threshold, read from the row's
 * rent limb, so no percentage is held here; a rent needed that reaches the filling rent means the rent limb bound and the
 * pack has changed under this class, which is refused.
 */
public final class RentRoomPricing {

    private static final BaseId SALARY = new BaseId("SALARY");
    private static final BaseId TOTAL_INCOME = new BaseId("TOTAL_INCOME");
    private static final Days BY_DAYS_OCCUPIED = new Days();

    private RentRoomPricing() {
    }

    /** One measurement: the run with the rent filled in, the homes it assumed, and the location it reports. */
    private record Filled(PipelineRunner.Result run, List<Tenancy> tenancies, Optional<String> location) {
    }

    /**
     * The rent room for the inputs, or empty where rent would earn nothing more — the regime allows the row nothing, a
     * home is owned, the declaration states a bar, or there is no income.
     *
     * @throws IllegalStateException where the row's cap is not the least-of with a rent limb this reads, or the rent limb
     *     still bound at the filling rent
     */
    public static Optional<RentRoom> price(NodeRegistry registry, RulePack pack, RegimeSpec regime, EngineInputs inputs) {
        PipelineRunner.Result base = PipelineRunner.run(registry, pack, regime, inputs);
        Money fillingRent = money(base, NodeIds.GTI).floorAtZero().plus(payEntered(inputs.entered().employments()));
        if (!fillingRent.isGreaterThan(Money.ZERO)) {
            return Optional.empty();
        }
        return receivesHouseRentAllowance(inputs.entered().employments())
                ? houseRentAllowance(registry, pack, regime, inputs, base, fillingRent)
                : rentPaid(registry, pack, regime, inputs, base, fillingRent);
    }

    private static Optional<RentRoom> houseRentAllowance(NodeRegistry registry, RulePack pack, RegimeSpec regime,
            EngineInputs inputs, PipelineRunner.Result base, Money fillingRent) {
        RuleRow row = only(pack, RuleSetIds.RS_SAL_HOUSE_RENT_ALLOWANCE);
        LeastOf cap = leastOf(row, inputs);
        List<Filled> candidates = new ArrayList<>();
        if (inputs.entered().tenancies().isEmpty()) {
            for (String location : locations(cap)) {
                List<Tenancy> home = List.of(new Tenancy(wholeYear(inputs.taxYear()), location, false, fillingRent));
                candidates.add(new Filled(run(registry, pack, regime, inputs, inputs.entered().withTenancies(home)), home,
                        Optional.of(location)));
            }
        } else {
            List<Tenancy> raised = raised(inputs.entered().tenancies(), fillingRent);
            candidates.add(new Filled(run(registry, pack, regime, inputs, inputs.entered().withTenancies(raised)), raised,
                    Optional.empty()));
        }
        Money billBefore = DeductionRoomPricing.bill(base);
        Filled best = candidates.getFirst();
        for (Filled candidate : candidates) {
            if (DeductionRoomPricing.bill(candidate.run()).isLessThan(DeductionRoomPricing.bill(best.run()))) {
                best = candidate;
            }
        }
        Money counted = exempted(base, row);
        Money room = exempted(best.run(), row).minus(counted);
        if (!room.isGreaterThan(Money.ZERO)) {
            return Optional.empty();
        }
        Money salaryForYear = ((SalaryBases) best.run().valueOf(NodeIds.SALARY_BASE))
                .of(pack.ruleSet(RuleSetIds.RS_SAL_SALARY_FOR_HRA), 0);
        CapShape threshold = rentLimb(cap, row.id()).threshold();
        List<TraceLine> lines = lines(best.run(), row, StepKind.EXEMPTION);
        Money rentNeeded = Money.ZERO;
        for (int home = 0; home < best.tenancies().size(); home++) {
            Tenancy tenancy = best.tenancies().get(home);
            Money exempt = amount(lines.get(home));
            if (exempt.isGreaterThan(Money.ZERO)) {
                Money salary = BY_DAYS_OCCUPIED.apportion(salaryForYear, inputs.taxYear(), tenancy.occupied());
                rentNeeded = rentNeeded.plus(exempt).plus(threshold.apply(CapContext.of(SALARY, salary)).value());
            }
        }
        return Optional.of(new RentRoom(row.id().value(), best.location(), counted, room,
                belowFillingRent(rentNeeded, fillingRent, row.id()), billBefore.minus(DeductionRoomPricing.bill(best.run()))
                        .floorAtZero()));
    }

    private static Optional<RentRoom> rentPaid(NodeRegistry registry, RulePack pack, RegimeSpec regime,
            EngineInputs inputs, PipelineRunner.Result base, Money fillingRent) {
        RuleRow row = only(pack, RuleSetIds.RS_DED_RENT_PAID);
        EnteredFacts entered = inputs.entered();
        List<Tenancy> homes = entered.tenancies().isEmpty()
                ? List.of(new Tenancy(wholeYear(inputs.taxYear()), PercentOfBaseByLocation.ANY_OTHER_PLACE, false,
                        fillingRent))
                : raised(entered.tenancies(), fillingRent);
        RentDeclaration declaration = entered.rentDeclaration()
                .orElseGet(() -> new RentDeclaration(monthsOccupied(homes, inputs.taxYear()), false, false));
        PipelineRunner.Result filled = run(registry, pack, regime, inputs,
                entered.withTenancies(homes).withRentDeclaration(Optional.of(declaration)));
        Money counted = deducted(base, row);
        Money allowed = deducted(filled, row);
        Money room = allowed.minus(counted);
        if (!room.isGreaterThan(Money.ZERO)) {
            return Optional.empty();
        }
        Money totalIncomeBefore = money(filled, NodeIds.TOTALINCOME).plus(allowed);
        Money threshold = rentLimb(leastOf(row, inputs), row.id()).threshold()
                .apply(CapContext.of(TOTAL_INCOME, totalIncomeBefore)).value();
        return Optional.of(new RentRoom(row.id().value(), Optional.empty(), counted, room,
                belowFillingRent(allowed.plus(threshold), fillingRent, row.id()),
                DeductionRoomPricing.bill(base).minus(DeductionRoomPricing.bill(filled)).floorAtZero()));
    }

    /** ANY_OTHER_PLACE first, so a tie reports the location that claims least; then one place from each band. */
    static List<String> locations(LeastOf cap) {
        PercentOfBaseByLocation byLocation = cap.limbs().stream()
                .map(LeastOf.Limb::shape)
                .filter(PercentOfBaseByLocation.class::isInstance)
                .map(PercentOfBaseByLocation.class::cast)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("HRA's cap has no limb by location to assume a place from"));
        List<String> locations = new ArrayList<>();
        locations.add(PercentOfBaseByLocation.ANY_OTHER_PLACE);
        byLocation.bands().forEach(band -> locations.add(new TreeSet<>(band.locations()).first()));
        return locations;
    }

    static AmountInExcess rentLimb(LeastOf cap, RuleId row) {
        return cap.limbs().stream()
                .map(LeastOf.Limb::shape)
                .filter(AmountInExcess.class::isInstance)
                .map(AmountInExcess.class::cast)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(row.value() + " has no limb on rent in excess of a threshold"));
    }

    private static LeastOf leastOf(RuleRow row, EngineInputs inputs) {
        CapShape cap = TemporallyApportionedCap.governingVersion(row, inputs.temporal()).cap()
                .orElseThrow(() -> new IllegalStateException(row.id() + " has no cap in the year"));
        if (!(cap instanceof LeastOf leastOf)) {
            throw new IllegalStateException(row.id() + "'s cap is " + cap.shapeName() + ", not the least-of this reads");
        }
        return leastOf;
    }

    static Money belowFillingRent(Money rentNeeded, Money fillingRent, RuleId row) {
        if (!rentNeeded.isLessThan(fillingRent)) {
            throw new IllegalStateException(row.value() + "'s rent limb still bound at a rent of " + fillingRent);
        }
        return rentNeeded;
    }

    private static List<Tenancy> raised(List<Tenancy> tenancies, Money fillingRent) {
        return tenancies.stream()
                .map(home -> home.ownedByAssessee() ? home
                        : new Tenancy(home.occupied(), home.location(), false, fillingRent))
                .toList();
    }

    /** The calendar months of the year the rented homes occupy: what Form No. 31 states as the period of stay. */
    private static int monthsOccupied(List<Tenancy> homes, TaxYearId year) {
        Set<YearMonth> months = new TreeSet<>();
        for (Tenancy home : homes) {
            if (home.ownedByAssessee() || !home.occupied().overlapsTaxYear(year)) {
                continue;
            }
            LocalDate from = home.occupied().from().isBefore(year.startsOn()) ? year.startsOn() : home.occupied().from();
            LocalDate to = home.occupied().to().filter(end -> end.isBefore(year.endsOn())).orElse(year.endsOn());
            for (YearMonth month = YearMonth.from(from); !month.isAfter(YearMonth.from(to)); month = month.plusMonths(1)) {
                months.add(month);
            }
        }
        return Math.max(1, months.size());
    }

    private static boolean receivesHouseRentAllowance(List<Employment> employments) {
        return employments.stream().flatMap(employment -> employment.components().stream())
                .anyMatch(component -> component.code().equals(ComponentCodes.HRA)
                        && component.annualAmount().isGreaterThan(Money.ZERO));
    }

    private static Money payEntered(List<Employment> employments) {
        return employments.stream().flatMap(employment -> employment.components().stream())
                .map(SalaryComponent::annualAmount)
                .reduce(Money.ZERO, Money::plus);
    }

    private static PipelineRunner.Result run(NodeRegistry registry, RulePack pack, RegimeSpec regime, EngineInputs inputs,
            EnteredFacts entered) {
        return PipelineRunner.run(registry, pack, regime, new EngineInputs(inputs.taxYear(), inputs.regime(),
                inputs.temporal(), entered, inputs.assessee(), inputs.family(), inputs.ledgers(), inputs.elections(),
                inputs.capitalGains(), inputs.filing()));
    }

    private static RuleRow only(RulePack pack, RuleSetId set) {
        List<RuleId> members = pack.ruleSet(set).members();
        if (members.size() != 1) {
            throw new IllegalStateException(set + " holds " + members + "; one row was expected to price rent by");
        }
        return pack.rule(members.getFirst());
    }

    private static DateRange wholeYear(TaxYearId year) {
        return DateRange.between(year.startsOn(), year.endsOn());
    }

    private static List<TraceLine> lines(PipelineRunner.Result run, RuleRow row, StepKind kind) {
        return run.trace().stream().filter(line -> line.kind() == kind && line.rule().equals(row.id())).toList();
    }

    private static Money exempted(PipelineRunner.Result run, RuleRow row) {
        return lines(run, row, StepKind.EXEMPTION).stream().map(RentRoomPricing::amount).reduce(Money.ZERO, Money::plus);
    }

    private static Money deducted(PipelineRunner.Result run, RuleRow row) {
        return lines(run, row, StepKind.DEDUCTION).stream().map(RentRoomPricing::amount).reduce(Money.ZERO, Money::plus);
    }

    private static Money amount(TraceLine line) {
        return ((MoneyValue) line.out()).amount();
    }

    private static Money money(PipelineRunner.Result run, NodeId node) {
        return ((MoneyValue) run.valueOf(node)).amount();
    }
}
