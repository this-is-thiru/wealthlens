package com.thiru.wealthlens.taxengine.input;

import java.util.List;
import java.util.Optional;

/**
 * What the taxpayer entered, as the engine will read it.
 *
 * <h2>A declared seam, filled one income item at a time</h2>
 *
 * <p>Decision <b>D-026</b>, and this needs stating plainly rather than being discovered later.
 * {@code DR 23} A3 gives {@code EngineInputs} a {@code DraftInputs entered} component.
 * {@code DraftInputs} ({@code 11-filing-drafts.md} F4) is {@code taxplanning}'s, so the engine may
 * not hold it (decision <b>D-024</b>) — and its own components, {@code Employment},
 * {@code HouseProperty}, {@code OtherSourceItem}, {@code DeclaredClaim}, {@code ProofOfClaim} and
 * {@code EntryProvenance}, are named in that one code block and <b>nowhere else in the design or
 * the backlog</b>. No item produces them. {@code Employment} alone — "incl. periods and revisions"
 * — is the whole per-period machinery {@code 13-edge-cases.md} R-11 describes.
 *
 * <p>A faithful projection is therefore the entire income-side domain model, which Phases 5 to 9
 * build. This record is the <b>seam it grows into</b>: each field arrives with the income item that
 * needs it, the way {@code CapShape} and {@code NodeValue} grew (rule B2).
 *
 * <h2>Why declare it empty rather than leave it out</h2>
 *
 * <p>Because the two are not the same, and the difference is a wrong number. Blocker <b>B-003</b>
 * turned on a component being <i>absent</i> from {@code EngineInputs}: the fingerprint
 * ({@code W-038}) is the cache key, so an absent component is an input the key cannot see, and two
 * computations differing only in it would collide. A component that is declared and thin is in the
 * key, and A3's own inference requires the fingerprint to be derived structurally — <i>"a field
 * added to {@code EngineInputs} is in the key automatically, which is the only way that invariant
 * survives"</i> — so every field added here enters the key without anyone remembering to add it.
 *
 * <p>The first field arrived with the component partition (D-090): the employments, each with its
 * pay lines. The homes occupied arrived with HRA (D-099), the journeys on leave with LTA (D-100), how each
 * allowance was used with the allowance exemptions (D-101), a disabled employee's transport claim with its
 * exemption (D-102), the retirement and termination payments with their deductions (D-103), the accommodation
 * employers provide with its perquisite (D-111), medical treatment abroad with its exclusion (D-112), the vehicles
 * with the car perquisite (D-113), meals and gifts with theirs (D-117), loans with
 * theirs (D-118), the cost-based benefits and assets with theirs (D-119). Every later
 * income item adds the field it reads, and the fingerprint sees it unasked.
 *
 * @param employments each employment in the tax year, in the order entered
 * @param tenancies each home occupied in the tax year, in the order entered
 * @param leaveTravel each journey on leave claimed in the tax year, in the order entered
 * @param allowanceUses how each allowance was used, for its exemption, in the order entered
 * @param disabledTransport the transport allowance exemption claimed for a disability, if any
 * @param retirementPayments each retirement or termination payment and the entry it falls under, in the order
 *     entered
 * @param accommodations each residential accommodation an employer provided, in the order entered
 * @param medicalTreatmentAbroad each claim of medical treatment abroad an employer paid for, in the order entered
 * @param vehicles each vehicle an employer provided or ran at its cost, in the order entered
 * @param meals each set of meals of one value an employer provided, in the order entered
 * @param gifts each gift, voucher or token an employer gave, in the order entered
 * @param loans each interest-free or concessional loan from an employer, in the order entered
 * @param expenseBenefits each expenditure an employer met that Table IV values at its amount, in the order entered
 * @param assetsLent each movable asset of an employer the employee used, in the order entered
 * @param assetsTransferred each movable asset an employer transferred to the employee, in the order entered
 */
public record EnteredFacts(
        List<Employment> employments,
        List<Tenancy> tenancies,
        List<Journey> leaveTravel,
        List<AllowanceUse> allowanceUses,
        Optional<DisabledTransportClaim> disabledTransport,
        List<RetirementPayment> retirementPayments,
        List<EmployerAccommodation> accommodations,
        List<MedicalTreatmentAbroad> medicalTreatmentAbroad,
        List<EmployerVehicle> vehicles,
        List<EmployerMeals> meals,
        List<EmployerGift> gifts,
        List<EmployerLoan> loans,
        List<EmployerExpenseBenefit> expenseBenefits,
        List<EmployerAssetUse> assetsLent,
        List<EmployerAssetTransfer> assetsTransferred) {

    public EnteredFacts {
        if (disabledTransport == null) {
            throw new IllegalArgumentException(
                    "EnteredFacts.disabledTransport may not be null; pass Optional.empty() where there is none");
        }
        employments = copied(employments, "employments");
        tenancies = copied(tenancies, "tenancies");
        leaveTravel = copied(leaveTravel, "leaveTravel");
        allowanceUses = copied(allowanceUses, "allowanceUses");
        retirementPayments = copied(retirementPayments, "retirementPayments");
        accommodations = copied(accommodations, "accommodations");
        medicalTreatmentAbroad = copied(medicalTreatmentAbroad, "medicalTreatmentAbroad");
        vehicles = copied(vehicles, "vehicles");
        meals = copied(meals, "meals");
        gifts = copied(gifts, "gifts");
        loans = copied(loans, "loans");
        expenseBenefits = copied(expenseBenefits, "expenseBenefits");
        assetsLent = copied(assetsLent, "assetsLent");
        assetsTransferred = copied(assetsTransferred, "assetsTransferred");
    }

    /** An immutable copy of an entered list, refusing a missing one: an absent list is entered as an empty one. */
    private static <T> List<T> copied(List<T> entered, String field) {
        if (entered == null) {
            throw new IllegalArgumentException(
                    "EnteredFacts." + field + " may not be null; pass an empty list where there are none");
        }
        return List.copyOf(entered);
    }

    /** Nothing entered. */
    public static EnteredFacts none() {
        return new EnteredFacts(List.of(), List.of(), List.of(), List.of(), Optional.empty(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of());
    }
}
