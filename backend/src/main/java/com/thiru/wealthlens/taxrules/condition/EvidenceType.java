package com.thiru.wealthlens.taxrules.condition;

/**
 * An artefact a claim requires, as {@code 12-claim-integrity.md} C3.2 lists them.
 *
 * <p>Each value is one row of that table, and each row was read from the gazette this session. These
 * are not categories invented for tidiness — a claim either has the specific document the rule names
 * or it does not.
 */
public enum EvidenceType {

    /** Landlord name, address and PAN, where aggregate rent exceeds ₹1,00,000 — r.205(2) Sl. 1. */
    LANDLORD_PAN,

    /** Evidence of expenditure for leave travel — {@code SAL-LTA-002}, r.205(2) Sl. 2. */
    LTA_EXPENDITURE_PROOF,

    /** Lender name, address and PAN for house-property interest — r.205(2) Sl. 3. */
    LENDER_PAN,

    /** Evidence of investment or expenditure for Chapter VIII — r.205(2) Sl. 4. */
    CHAPTER_VIII_PAYMENT_PROOF,

    /** Disability certificate, re-certified on expiry — ss.127(6)–(7), 154(2). */
    DISABILITY_CERTIFICATE,

    /**
     * The lender's certificate specifying interest, and interest on any refinancing loan, for the
     * self-occupied ₹2,00,000 limb — {@code HP-DED-CERT-001}, s.22(2), 22(4).
     */
    LENDER_INTEREST_CERTIFICATE,

    /**
     * The institution's report of an 80G donation — s.133(6).
     *
     * <p>The sharpest of these and the least known. An 80G claim is <b>not</b> allowed because the
     * taxpayer holds a receipt; it is allowed on the basis of what the institution reported. So the
     * question this product asks is not &ldquo;do you have a receipt&rdquo; but &ldquo;does this
     * appear in your pre-filled data, and if not, have you asked the institution why&rdquo;.
     */
    INSTITUTION_REPORT_80G
}
