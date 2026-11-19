package com.thiru.wealthlens.taxplanning.compute;

import com.thiru.wealthlens.taxengine.input.EnteredFacts;
import com.thiru.wealthlens.taxengine.input.Journey;
import com.thiru.wealthlens.taxengine.input.JourneyTraveller;
import com.thiru.wealthlens.taxengine.input.QualifyingPayment;
import com.thiru.wealthlens.taxengine.input.Tenancy;
import com.thiru.wealthlens.taxengine.rules.id.ComponentCode;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxplanning.workspace.EvidenceHeld;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A draft's entries as far as the documents held support them (W-119c C2a): each evidenced figure cut to what is held,
 * built from the entries with {@code EnteredFacts}' own {@code with…} methods, so the engine is asked an ordinary
 * question and never learns what evidence is. A figure nothing is recorded for is held to nil.
 *
 * @param entered the draft's entries
 * @param held what the person can show for them
 * @param taxYear the year, which bounds a tenancy's months
 */
record EvidencedFacts(EnteredFacts entered, EvidenceHeld held, TaxYearId taxYear) {

    /** The kinds the entries hold something of: a kind with no entry has nothing to evidence. */
    Set<EvidenceKind> kindsEntered() {
        Set<EvidenceKind> kinds = EnumSet.noneOf(EvidenceKind.class);
        if (!entered.tenancies().isEmpty()) {
            kinds.add(EvidenceKind.RENT_RECEIPTS);
        }
        if (!entered.leaveTravel().isEmpty()) {
            kinds.add(EvidenceKind.JOURNEY_EXPENDITURE);
        }
        if (!entered.qualifyingPayments().isEmpty()) {
            kinds.add(EvidenceKind.QUALIFYING_PAYMENT);
        }
        ChapterViiiCuts.kindsEntered(entered, kinds);
        return kinds;
    }

    /** Every kind cut to what is held. */
    EnteredFacts supported() {
        return ChapterViiiCuts.cut(withLeaveTravel(withPayments(withRent(entered, held.receiptedMonths()),
                held.qualifyingPayments()), held.journeyExpenditure()), held.chapterViii());
    }

    /** One kind cut to nothing held, the rest as entered: what that kind's evidence bears on. */
    EnteredFacts nothingHeldFor(EvidenceKind kind) {
        return switch (kind) {
            case RENT_RECEIPTS -> withRent(entered, List.of());
            case JOURNEY_EXPENDITURE -> withLeaveTravel(entered, List.of());
            case QUALIFYING_PAYMENT -> withPayments(entered, Map.of());
            default -> ChapterViiiCuts.nothingHeldFor(entered, kind);
        };
    }

    /** The months of rent not receipted, across the tenancies. */
    long monthsNotReceipted() {
        long months = 0;
        for (int i = 0; i < entered.tenancies().size(); i++) {
            months += entered.tenancies().get(i).monthsOccupiedIn(taxYear) - receipted(i);
        }
        return months;
    }

    /** What the journeys cost beyond what is evidenced. */
    Money travelNotEvidenced() {
        Money outstanding = Money.ZERO;
        for (int i = 0; i < entered.leaveTravel().size(); i++) {
            outstanding = outstanding.plus(entered.leaveTravel().get(i).amountIncurred().minus(journeyEvidenced(i)));
        }
        return outstanding;
    }

    /** What a Chapter VIII family's entries came to beyond what is evidenced (W-119b1). */
    Money chapterViiiNotEvidenced(EvidenceKind kind) {
        return ChapterViiiCuts.notEvidenced(entered, held.chapterViii(), kind);
    }

    /** What the Schedule XV payments came to beyond what is evidenced. */
    Money paymentsNotEvidenced() {
        Money paid = entered.qualifyingPayments().stream().map(QualifyingPayment::amount).reduce(Money.ZERO, Money::plus);
        return paid.minus(held.qualifyingPayments().values().stream().reduce(Money.ZERO, Money::plus));
    }

    private int receipted(int tenancy) {
        return held.receiptedMonths().isEmpty() ? 0 : held.receiptedMonths().get(tenancy);
    }

    private Money journeyEvidenced(int journey) {
        return held.journeyExpenditure().isEmpty() ? Money.ZERO : held.journeyExpenditure().get(journey);
    }

    /** C3a: a tenancy's rent in proportion to its receipted months, rent taken as equal each month. */
    private EnteredFacts withRent(EnteredFacts facts, List<Integer> receiptedMonths) {
        List<Tenancy> cut = new ArrayList<>();
        for (int i = 0; i < facts.tenancies().size(); i++) {
            Tenancy tenancy = facts.tenancies().get(i);
            long months = tenancy.monthsOccupiedIn(taxYear);
            int receipted = receiptedMonths.isEmpty() ? 0 : receiptedMonths.get(i);
            cut.add(months == 0 ? tenancy
                    : tenancy.withRentPaid(tenancy.rentPaid().apportioned(receipted, Math.toIntExact(months))));
        }
        return facts.withTenancies(cut);
    }

    /** A journey's cost cut to what is evidenced, its travellers' shares in proportion, the last taking what is left. */
    private static EnteredFacts withLeaveTravel(EnteredFacts facts, List<Money> evidenced) {
        List<Journey> cut = new ArrayList<>();
        for (int i = 0; i < facts.leaveTravel().size(); i++) {
            Journey journey = facts.leaveTravel().get(i);
            Money amount = evidenced.isEmpty() ? Money.ZERO : evidenced.get(i);
            cut.add(new Journey(journey.startedOn(), journey.mode(), amount, journey.fareAdmissible(), journey.distanceKm(),
                    travellers(journey, amount)));
        }
        return facts.withLeaveTravel(cut);
    }

    private static List<JourneyTraveller> travellers(Journey journey, Money amount) {
        List<JourneyTraveller> shares = new ArrayList<>();
        Money given = Money.ZERO;
        for (int i = 0; i < journey.travellers().size(); i++) {
            JourneyTraveller traveller = journey.travellers().get(i);
            Money share = i == journey.travellers().size() - 1 ? amount.minus(given)
                    : traveller.amountIncurred().apportioned(paise(amount), paise(journey.amountIncurred()));
            given = given.plus(share);
            shares.add(new JourneyTraveller(traveller.familyMember(), share, traveller.fareAdmissible()));
        }
        return shares;
    }

    private static int paise(Money amount) {
        return amount.amountInRupees().movePointRight(2).intValueExact();
    }

    /** Each code's payments filled in their order up to the amount evidenced for the code. */
    private static EnteredFacts withPayments(EnteredFacts facts, Map<ComponentCode, Money> evidenced) {
        Map<ComponentCode, Money> left = new HashMap<>(evidenced);
        List<QualifyingPayment> cut = new ArrayList<>();
        for (QualifyingPayment payment : facts.qualifyingPayments()) {
            Money available = left.getOrDefault(payment.code(), Money.ZERO);
            Money share = Money.min(available, payment.amount());
            left.put(payment.code(), available.minus(share));
            cut.add(new QualifyingPayment(payment.code(), share));
        }
        return facts.withQualifyingPayments(cut);
    }
}
