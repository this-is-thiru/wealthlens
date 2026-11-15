# Sourcing for W-119b — the evidence of claims an employee furnishes (2026-11-15)

**READ** = read in the primary document by this session, page given; **NOT SEARCHED** = stated as such. Pages are the
Gazette's printed page numbers, which here equal the PDF's page index.

| # | Finding | Status | Source |
|---|---|---|---|
| E1 | **Income-tax Rules, 2026 r.205**, heading: *"Furnishing of evidence of claims by employee under section 392(5)(b) for deduction of tax from income under head 'Salaries'."* (1): *"The assessee shall furnish to the person responsible for making payment under section 392(1), the evidence or the particulars of the claims referred to in sub-rule (2) in Form No. 124, for the purpose of estimating his income or for computing the tax required to be deducted at source."* | **READ** | egazette 2026/271092 (CG-DL-E-20032026-271092, G.S.R. 198(E), 20-3-2026) p.1687 |
| E2 | r.205(2) Table, column B "Nature of claims", column C "Evidence or particulars". **Sl. 1** *"House rent allowance."* — *"Name, address and Permanent Account Number of the landlord or landlords, where the aggregate rent paid during the tax year exceeds ₹ 100000 and relationship with the landlord, if any."* **Sl. 2** *"Leave travel concession or assistance."* — *"Evidence of expenditure."* **Sl. 3** *"Deduction of interest under the head 'Income from house property'."* — *"Name, address and Permanent Account Number of the lender."* **Sl. 4** *"Deduction under Chapter VIII."* — *"Evidence of investment or expenditure."* | **READ** | same, p.1687 |
| E3 | The table has four rows and no other. It names no rent receipt, no count of months, and no lender's certificate. | **READ** | same, p.1687 |
| E4 | Any amendment to r.205 after 20-3-2026 | **NOT SEARCHED** — the 2026-27 lock notes the Rules' amendment chain to G.S.R. 656(E) of 24-7-2026 as not traced | `rulepacks/2026-27.lock.json`, `instruments.rules.note` |
| E5 | The 1961 Rules' counterpart for TY 2025-26 (r.26C, Form No. 12BB) Sl. 2 – 4 | **NOT READ here** — Sl. 1 was read as enacted in 2016 (`2026-10-09-landlord-pan.md` L2); later amendments not searched (its L4) | — |

## Effect on W-119b

- What r.205(2) asks for is **particulars or evidence furnished to the employer** for deduction at source. It is not a
  condition of any exemption or deduction, which agrees with the owner's C6a (missing evidence never changes the tax).
- Months of rent receipted (W-119 C4a; `DR 12` T12-1) are the product's record of what a person holds — not a
  requirement of this table (E3). They must not be presented as one.
- The lender's certificate is a condition of the higher own-residence interest limit (s.22(2)(a)(ii), (4)) and is
  already an engine fact (`HousingLoan.lenderCertificateHeld`); Sl. 3 asks for the lender's name, address and PAN.
