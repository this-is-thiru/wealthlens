package com.thiru.wealthlens.taxengine.input;

import com.thiru.wealthlens.taxrules.condition.DisabilitySeverity;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * The people other than the taxpayer whose facts a rule reads.
 *
 * <h2>Why this exists at all</h2>
 *
 * <p>{@code 15-review-findings.md} F2 (defect D-6). Six rules need per-person facts that were
 * scattered across prose, and one of them is not expressible without this record at all:
 * {@code DED-80D-008} allows medical expenditure on a senior citizen <i>only if no amount has been
 * paid to keep health insurance in force <b>for that person</b></i>. It is a per-person rule, and
 * there were no persons.
 *
 * <p>A projection of {@code taxplanning}'s {@code FamilyMember} ({@code W-097}), for the reasons in
 * {@link TaxpayerFacts} — decision <b>D-024</b>.
 *
 * @param members may be empty; never null. Order is not significant to any rule, but it is
 *     preserved, because a report that lists dependants in a different order each run looks broken
 */
public record FamilySnapshot(List<Member> members) {

    /**
     * One person, and the facts a rule may read about them.
     *
     * <p>Fields are {@code 15-review-findings.md} F2's, less the identifiers and the PAN, which are
     * {@code taxplanning}'s and which no rule computes on. Two of them look oddly specific and are
     * not: {@code bornBeforeOct1998} and {@code isMultipleBirthAfterFirstChild} are the two written
     * exceptions to {@code SAL-LTA-CHILD-001}'s two-child limit, and neither is derivable from a
     * date of birth alone — the second is a fact about a birth, not about a person.
     *
     * @param relationship self/family versus parents are separate ceilings under
     *     {@code DED-80D-001} and {@code -002}
     * @param dateOfBirth drives the senior substitution in {@code DED-80D-007}; a date and not an
     *     age, for the reason given in {@link TaxpayerFacts}
     * @param dependant {@code DED-80DD-001}'s "dependant wholly or mainly"
     * @param disability {@code DED-80DD-001} reads the severity — the ₹75,000 / ₹1,25,000 split
     * @param dateOfDeath {@code SAL-LTA-CHILD-001}'s "surviving children"
     * @param bornBeforeOct1998 the first exception to the two-child limit
     * @param multipleBirthAfterFirstChild the second exception
     */
    public record Member(
            Relationship relationship,
            LocalDate dateOfBirth,
            boolean dependant,
            Optional<DisabilitySeverity> disability,
            Optional<LocalDate> dateOfDeath,
            boolean bornBeforeOct1998,
            boolean multipleBirthAfterFirstChild) {

        public Member {
            if (relationship == null) {
                throw new IllegalArgumentException("A family member must state a relationship");
            }
            if (dateOfBirth == null) {
                throw new IllegalArgumentException(
                        "A family member must carry a date of birth; the senior-citizen"
                                + " substitution in DED-80D-007 reads it");
            }
            if (disability == null || dateOfDeath == null) {
                throw new IllegalArgumentException(
                        "Family member optionals may not be null; pass Optional.empty() where the"
                                + " fact is absent");
            }
            if (dateOfDeath.isPresent() && dateOfDeath.get().isBefore(dateOfBirth)) {
                throw new IllegalArgumentException(
                        "A family member's dateOfDeath " + dateOfDeath.get()
                                + " precedes their dateOfBirth " + dateOfBirth);
            }
        }
    }

    /** {@code 15-review-findings.md} F2's five relationships, unchanged. */
    public enum Relationship {
        SPOUSE,
        CHILD,
        PARENT,
        SIBLING,
        DEPENDANT_OTHER
    }

    public FamilySnapshot {
        if (members == null) {
            throw new IllegalArgumentException(
                    "FamilySnapshot.members may not be null; pass an empty list where there are no"
                            + " family members, because 'no family' and 'nobody asked' claim"
                            + " different deductions");
        }
        members = List.copyOf(members);
    }

    /** Nobody to consider. */
    public static FamilySnapshot none() {
        return new FamilySnapshot(List.of());
    }

    /** The members in one relationship — the shape {@code DED-80D-001} versus {@code -002} needs. */
    public List<Member> withRelationship(Relationship relationship) {
        return members.stream().filter(m -> m.relationship() == relationship).toList();
    }
}
