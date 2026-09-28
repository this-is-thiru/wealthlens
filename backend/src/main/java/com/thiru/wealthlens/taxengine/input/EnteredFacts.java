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
 * employers provide with its perquisite (D-111), medical treatment abroad with its exclusion (D-112). Every later
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
 */
public record EnteredFacts(
        List<Employment> employments,
        List<Tenancy> tenancies,
        List<Journey> leaveTravel,
        List<AllowanceUse> allowanceUses,
        Optional<DisabledTransportClaim> disabledTransport,
        List<RetirementPayment> retirementPayments,
        List<EmployerAccommodation> accommodations,
        List<MedicalTreatmentAbroad> medicalTreatmentAbroad) {

    public EnteredFacts {
        if (employments == null) {
            throw new IllegalArgumentException(
                    "EnteredFacts.employments may not be null; pass an empty list where there are none");
        }
        if (tenancies == null) {
            throw new IllegalArgumentException(
                    "EnteredFacts.tenancies may not be null; pass an empty list where there are none");
        }
        if (leaveTravel == null) {
            throw new IllegalArgumentException(
                    "EnteredFacts.leaveTravel may not be null; pass an empty list where there are none");
        }
        if (allowanceUses == null) {
            throw new IllegalArgumentException(
                    "EnteredFacts.allowanceUses may not be null; pass an empty list where there are none");
        }
        if (disabledTransport == null) {
            throw new IllegalArgumentException(
                    "EnteredFacts.disabledTransport may not be null; pass Optional.empty() where there is none");
        }
        if (accommodations == null) {
            throw new IllegalArgumentException(
                    "EnteredFacts.accommodations may not be null; pass an empty list where there are none");
        }
        if (retirementPayments == null) {
            throw new IllegalArgumentException(
                    "EnteredFacts.retirementPayments may not be null; pass an empty list where there are none");
        }
        if (medicalTreatmentAbroad == null) {
            throw new IllegalArgumentException(
                    "EnteredFacts.medicalTreatmentAbroad may not be null; pass an empty list where there is none");
        }
        employments = List.copyOf(employments);
        tenancies = List.copyOf(tenancies);
        leaveTravel = List.copyOf(leaveTravel);
        allowanceUses = List.copyOf(allowanceUses);
        retirementPayments = List.copyOf(retirementPayments);
        accommodations = List.copyOf(accommodations);
        medicalTreatmentAbroad = List.copyOf(medicalTreatmentAbroad);
    }

    /** Nothing entered. */
    public static EnteredFacts none() {
        return new EnteredFacts(List.of(), List.of(), List.of(), List.of(), Optional.empty(), List.of(), List.of(),
                List.of());
    }
}
