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
 * theirs (D-118), the cost-based benefits and assets with theirs (D-119), household services, utilities and
 * education with theirs (D-120), share allotments with theirs (D-121), employer
 * retirement contributions with the aggregate charge (D-126), the funds' history with the accretion on the
 * excess (D-127), a recognised fund's interest with its charge above the notified rate (D-151), the Chapter VIII
 * payments with the deductions (D-132), life policies with their premiums' share (D-134), health payments with
 * 80D (D-136), disabled dependants' support and specified-disease treatment with 80DD and 80DDB (D-138), loan
 * interest with 80E–80EEB (D-139), the Agniveer Corpus Fund with s.16(l) and s.125 (D-140), the rent declaration
 * with s.134 (D-141), donations with s.133 (D-142), the houses owned with income from house property (D-155), the items of income from other
 * sources with that head (D-194), the person's own provident fund accounts with their taxable interest
 * (D-198), the gifts received with the charge on them (D-201), business-trust holdings with their specified sums
 * (D-203), life-policy receipts with their exclusion and charge (D-205). Every
 * later income item adds the field it reads, and the fingerprint sees it unasked.
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
 * @param householdBenefits each service, utility, education fee or carrier's journey, in the order entered
 * @param childEducation each child's education in the employer's institution or free by employment, in order
 * @param shareAllotments each allotment of specified securities or sweat equity, in the order entered
 * @param employerContributions each employer contribution to a retirement fund in the year, in the order entered
 * @param retirementFundHistory the earlier years' excess and the funds' income and balances, for the accretion on
 *     the excess, if entered
 * @param recognisedFundInterest each recognised provident fund's interest credited in the year and its rate, in
 *     the order entered
 * @param qualifyingPayments each sum paid that Chapter VIII lets the payer deduct, other than pay lines, in order
 * @param lifeInsurancePolicies each life policy whose premium was paid in the year, in the order entered
 * @param healthPayments each payment for health under s.126, in the order entered
 * @param disabledDependantSupport each disabled dependant supported under s.127, in the order entered
 * @param specifiedDiseaseTreatment each specified-disease treatment paid for under s.128, in the order entered
 * @param educationLoanInterest each education loan's interest paid under s.129, in the order entered
 * @param purposeLoanInterest each house or electric-vehicle loan's interest under s.130–132, in the order entered
 * @param agniveerCorpusFund the Agniveer Corpus Fund account, if the assessee is an Agniveer
 * @param rentDeclaration the Form No. 31 declaration claiming s.134 on the tenancies' rent, if made
 * @param donations each sum donated under s.133, in the order entered
 * @param houseProperties each building or appurtenant land owned, for income from house property, in the order
 *     entered
 * @param otherSourceItems each item of income from other sources, in the order entered
 * @param providentFundAccounts each provident fund the person contributed to, for its taxable interest, in the order
 *     entered
 * @param giftsReceived each sum or property received without or for inadequate consideration, in the order entered
 * @param businessTrustUnits each holding of a business trust's units, for its specified sum, in the order entered
 * @param lifePolicyReceipts each sum received under a life insurance policy, in the order entered
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
        List<EmployerAssetTransfer> assetsTransferred,
        List<EmployerHouseholdBenefit> householdBenefits,
        List<EmployerChildEducation> childEducation,
        List<EmployerShareAllotment> shareAllotments,
        List<EmployerFundContribution> employerContributions,
        Optional<RetirementFundHistory> retirementFundHistory,
        List<RecognisedFundInterest> recognisedFundInterest,
        List<QualifyingPayment> qualifyingPayments,
        List<LifeInsurancePolicy> lifeInsurancePolicies,
        List<HealthPayment> healthPayments,
        List<DisabledDependantSupport> disabledDependantSupport,
        List<SpecifiedDiseaseTreatment> specifiedDiseaseTreatment,
        List<EducationLoanInterest> educationLoanInterest,
        List<PurposeLoanInterest> purposeLoanInterest,
        Optional<AgniveerCorpusFund> agniveerCorpusFund,
        Optional<RentDeclaration> rentDeclaration,
        List<Donation> donations,
        List<HouseProperty> houseProperties,
        List<OtherSourceItem> otherSourceItems,
        List<ProvidentFundAccount> providentFundAccounts,
        List<GiftReceived> giftsReceived,
        List<BusinessTrustUnits> businessTrustUnits,
        List<LifePolicyReceipt> lifePolicyReceipts) {

    public EnteredFacts {
        if (disabledTransport == null) {
            throw new IllegalArgumentException(
                    "EnteredFacts.disabledTransport may not be null; pass Optional.empty() where there is none");
        }
        if (agniveerCorpusFund == null) {
            throw new IllegalArgumentException(
                    "EnteredFacts.agniveerCorpusFund may not be null; pass Optional.empty() where there is none");
        }
        if (rentDeclaration == null) {
            throw new IllegalArgumentException(
                    "EnteredFacts.rentDeclaration may not be null; pass Optional.empty() where there is none");
        }
        if (retirementFundHistory == null) {
            throw new IllegalArgumentException(
                    "EnteredFacts.retirementFundHistory may not be null; pass Optional.empty() where there is none");
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
        householdBenefits = copied(householdBenefits, "householdBenefits");
        childEducation = copied(childEducation, "childEducation");
        shareAllotments = copied(shareAllotments, "shareAllotments");
        employerContributions = copied(employerContributions, "employerContributions");
        recognisedFundInterest = copied(recognisedFundInterest, "recognisedFundInterest");
        qualifyingPayments = copied(qualifyingPayments, "qualifyingPayments");
        lifeInsurancePolicies = copied(lifeInsurancePolicies, "lifeInsurancePolicies");
        healthPayments = copied(healthPayments, "healthPayments");
        disabledDependantSupport = copied(disabledDependantSupport, "disabledDependantSupport");
        specifiedDiseaseTreatment = copied(specifiedDiseaseTreatment, "specifiedDiseaseTreatment");
        educationLoanInterest = copied(educationLoanInterest, "educationLoanInterest");
        purposeLoanInterest = copied(purposeLoanInterest, "purposeLoanInterest");
        donations = copied(donations, "donations");
        houseProperties = copied(houseProperties, "houseProperties");
        otherSourceItems = copied(otherSourceItems, "otherSourceItems");
        providentFundAccounts = copied(providentFundAccounts, "providentFundAccounts");
        giftsReceived = copied(giftsReceived, "giftsReceived");
        businessTrustUnits = copied(businessTrustUnits, "businessTrustUnits");
        lifePolicyReceipts = copied(lifePolicyReceipts, "lifePolicyReceipts");
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
                List.of(), List.of(), List.of(), List.of(), List.of(), Optional.empty(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), Optional.empty(), Optional.empty(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }
}
