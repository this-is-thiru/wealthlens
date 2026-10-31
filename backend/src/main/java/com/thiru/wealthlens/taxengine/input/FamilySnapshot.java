package com.thiru.wealthlens.taxengine.input;

import com.thiru.wealthlens.taxengine.rules.condition.DisabilitySeverity;
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
 * {@link Assessee} — decision <b>D-024</b>.
 *
 * @param members may be empty; never null. Order is not significant to any rule, but it is
 *     preserved, because a report that lists dependants in a different order each run looks broken
 */
public record FamilySnapshot(List<Member> members) {

    /**
     * One person, and the facts a rule may read about them.
     *
     * <p>Fields are {@code 15-review-findings.md} F2's, less the identifiers and the PAN, which are
     * {@code taxplanning}'s and which no rule computes on. {@code multipleBirthAfterFirstChild} looks
     * oddly specific and is not: it is the second written exception to the leave travel concession's
     * two-children limit ({@code SAL-LEAVE-TRAVEL-CHILDREN}), a fact about a birth that no date of birth
     * gives. The first exception, a child born before the rule's date, is read from {@code dateOfBirth}
     * against that date, so the date stays in the pack (D-212).
     *
     * @param relationship self/family versus parents are separate ceilings under
     *     {@code DED-80D-001} and {@code -002}
     * @param dateOfBirth drives the senior substitution in {@code DED-80D-007}; a date and not an
     *     age, for the reason given in {@link Individual}
     * @param dependant {@code DED-80DD-001}'s "dependant wholly or mainly"
     * @param disability {@code DED-80DD-001} reads the severity — the ₹75,000 / ₹1,25,000 split
     * @param dateOfDeath the leave travel concession's "surviving children" (D-212)
     * @param multipleBirthAfterFirstChild a child of a multiple birth after one child, which the two-children
     *     limit does not count
     * @param residentInIndia whether the person is resident in India in the tax year — a senior citizen is "an
     *     individual resident in India" (S2 s.2(100)), which {@code DED-80D-007}'s substitution reads (D-136)
     */
    public record Member(
            Relationship relationship,
            LocalDate dateOfBirth,
            boolean dependant,
            Optional<DisabilitySeverity> disability,
            Optional<LocalDate> dateOfDeath,
            boolean multipleBirthAfterFirstChild,
            boolean residentInIndia) {

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
