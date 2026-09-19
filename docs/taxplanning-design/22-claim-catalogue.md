# 22 — The claim catalogue

**Extends `01-rulebook.md` and `12-claim-integrity.md`.**

## C1 — What was missing

The rulebook says **what the law allows**. `12-claim-integrity.md` says **how much you can prove**.
Neither says **what the user actually does** — the steps, whom to ask, what happens if it is not on the
Form 16, and whether a small claim is worth the effort at all.

That is the gap between a calculator and something useful. A user told *"you have ₹47,600 of unused
HRA"* still does not know whether to email HR, wait for January, or just claim it in the return.

---

## C2 — The claim channel: five, and they behave differently

`FACT (S2, s.392(4)(a))`: the employee **"may, at his option"** furnish particulars to the employer.
The employer route is **optional**, not the only route.

`FACT (S2, s.122(5))`: the late-return bar — *"Deduction under the provisions of **Part C** of this
Chapter shall not be allowed to an assessee who fails to (a) furnish a return … on or before the due
date; or (b) make a claim of deduction in a return"*.

`FACT (S2, Chapter VIII part headings, read this session)`: **Part B** is *"Deductions in respect of
certain payments"* (ss.123–137 — 80C, 80D, 80DD, 80DDB, 80E, 80EE/EEA/EEB, 80G, 80GG); **Part C** is
*"Deductions in respect of certain incomes"* (ss.138–152, including 80RRB); **Part D** is s.153
(80TTA/80TTB); **Part E** is s.154 (80U).

`INFERENCE`: **essentially every deduction a salaried individual claims is in Part B, D or E, and is
therefore outside the s.122(5) late-return bar.** Filing late costs interest and a fee
(`INT-LATEFILE-001`, `FEE-LATEFILE-001`) — it does **not** forfeit 80C, 80D or 80U. That is materially
important and widely misunderstood.

`FACT (S4, r.164(11))`: the return *"shall not be accompanied by … **any document** or copy of any
account or form or report of audit required to be attached with the return of income under any of the
provisions of the Act"*.

`INFERENCE`: this resolves what looks like a conflict. `DED-80DD-001` and `DED-80U-001` say a
certificate is furnished *"along with the return"* (ss.127(6), 154(2)(c)), while r.164(11) says nothing
accompanies the return. The reconciliation is that **nothing is filed; everything is retained and
produced on demand** — which is exactly the case for `20-product-gaps.md` F39's document vault.

### The five channels

| Channel | Meaning | Examples | Verified basis |
|---|---|---|---|
| **E — Employer, optional** | May be routed through the employer to reduce TDS; **fully claimable in the return if not** | 80C, 80D, 80E, HRA, LTA, house-property interest | s.392(4)(a) *"at his option"*; s.392(5)(b) + r.205 |
| **R — Return only** | The employer cannot apply it | **Regime election**; s.157 arrears relief; 80TTA/80TTB; any deduction the employer declined | r.136; s.157(1) *"on an application made to him by the assessee"* |
| **R+ — Return, with a document retained** | Claimable in the return; a certificate must exist and be producible | 80DD, 80U, 80DDB, 80RRB | ss.127(6), 154(2)(c), 152(5); retained per r.164(11) |
| **T — Third-party reported** | Allowable **only** if the counterparty reported it | **80G donations** | **s.133(6)** — *"only on the basis of the information … furnished by such institution or fund"* |
| **P — Employer-provided only** | Not a claim at all; the employer must actually do it | Employer NPS (s.124(1)); employer paying tax on a non-monetary perquisite (s.392(2)) | ss.124(1), 392(2) |

`INFERENCE`: only channels **T** and **P** can be *lost*. **T** because the deduction is conditioned on
someone else's filing; **P** because there is nothing to claim unless the employer acted. Everything
else survives a missing Form 16 entry.

---

## C3 — "It's not in my Form 16 — can I still claim it?"

The headline answer, as a procedure:

```
notInForm16(rule):

  channel = catalogue[rule].channel

  E   → YES. Claim it in the return. Your refund grows by the tax on it.
        The employer merely over-deducted; nothing is lost.            s.392(4)(a)

  R   → YES — it was never the employer's to apply.                    r.136, s.157(1)

  R+  → YES, provided the certificate exists and you can produce it.
        It is NOT filed with the return.                               r.164(11)

  T   → ONLY IF the counterparty reported it. If your 80G donation does
        not appear in your pre-filled data, the deduction is not
        allowable until the institution reports it — ask them.          s.133(6)

  P   → NO. There is nothing to claim: the employer did not contribute.
        This is a conversation for next year's FBP window, not this
        year's return.                                                  s.124(1)
```

`RECOMMENDATION`: show this verdict **next to every claim** that the reconciliation
(`M4.2`) found in the computation but not in the document. `12-claim-integrity.md` C3.3 already
detects the case — *"deduction not declared to the employer, refund due"* — and this turns the
detection into an instruction.

---

## C4 — The catalogue entry

```java
public record ClaimCatalogueEntry(
        RuleId          rule,
        String          plainName,          // "House Rent Allowance"
        String          whatItIs,           // two sentences, no section numbers
        ClaimChannel    channel,            // E | R | R_PLUS | T | P
        List<ClaimStep> steps,              // ordered, imperative, each with a deadline
        List<Counterparty> askWhom,         // C7
        List<EvidenceRequirement> evidence, // from 12 C3.2, with what closes each
        AmountGuidance  amounts,            // C6 — min, max, typical basis
        List<CommonMistake> mistakes,
        MissingFromForm16 ifMissing,        // C3's verdict, per rule
        Materiality     materiality,        // C6
        Provenance      provenance) {}

public record ClaimStep(
        int      order,
        String   instruction,               // imperative: "Send HR your rent receipts"
        Actor    who,                       // YOU | EMPLOYER | INSTITUTION | LENDER | LANDLORD
        Optional<DeadlineSpec> by,          // resolves against the tax calendar, F30
        Optional<ArtefactRef> produces) {}  // what exists afterwards
```

`RECOMMENDATION`: the catalogue lives in the **rule pack**, beside the rule, because the procedure
changes when the rule does — r.136 removing the separate opt-out form is a *procedure* change with no
change in the underlying entitlement, and it must ship the same way.

---

## C5 — Worked entries

Abridged to the fields that carry the answer. Status is `VERIFIED` for the legal basis;
procedural steps are `RECOMMENDATION` grounded in it.

### `SAL-HRA-001` — House Rent Allowance · channel **E**

| | |
|---|---|
| **What it is** | If your salary includes an HRA component and you actually pay rent for a home you do not own, part of that HRA is not taxed. The exempt part is the **least of three** limits, so raising one alone often changes nothing. |
| **How much** | **Min ₹0** (no rent paid, or you own the home). **Max** = least of: HRA received · rent paid − 10% of (basic + DA) · 50% of that salary in Mumbai, Kolkata, Delhi, Chennai, Hyderabad, Pune, Ahmedabad, Bengaluru; 40% elsewhere. |
| **Steps** | 1. Keep rent receipts for every month (**you**). 2. Get the landlord's PAN if annual rent exceeds ₹1,00,000 (**landlord**). 3. Submit both to HR by the proof cut-off, in Form No. 124 (**you** → **employer**). 4. If you miss the cut-off, claim it in the return instead (**you**). |
| **Ask whom** | HR for the submission window and format; your landlord for PAN and a rent agreement. |
| **Evidence** | Rent receipts; landlord name, address, PAN and your relationship where rent > ₹1,00,000 (r.205(2) Sl.1); a rent agreement is not required by the Rules but is the strongest corroboration. |
| **If missing from Form 16** | **Claimable in the return.** Your refund grows by the tax on the exempt amount. |
| **Common mistakes** | Excluding DA from the salary base (`H-4`) · assuming the metro list is the same in both tax years (`H-2`) · claiming for the whole year when you occupied the home for part of it (`SAL-HRA-003`) · claiming while owning the home you live in (`SAL-HRA-002`). |
| **Not available** | In the default regime (`SAL-HRA-005`). |

### `DED-80C-001` — the ₹1,50,000 bucket · channel **E**

| | |
|---|---|
| **What it is** | One ceiling of ₹1,50,000 covering many different payments, **including the PF already deducted from your salary** — which is why your real headroom is usually far smaller than you think. |
| **How much** | **Min ₹0. Max ₹1,50,000**, and your employee PF has already used part of it. Employee NPS inside this bucket is separately capped at 10% of (basic + DA). |
| **Steps** | 1. Start from your **employee PF for the year** — that is already used (`PF-EEDED-001`). 2. Add children's tuition fees, life-insurance premiums, housing-loan **principal**, ELSS, PPF, 5-year deposits. 3. Invest the shortfall **by 31 March**. 4. Submit proofs to HR by the cut-off, or claim in the return. |
| **Ask whom** | HR for your annual PF figure; your insurer for a premium certificate; your school for a fee receipt (**tuition only** — development fees and donations do not count). |
| **If missing from Form 16** | **Claimable in the return.** |
| **Common mistakes** | Forgetting employee PF is inside the bucket (`H-7`) · counting the housing-loan **interest** here (it is not — that is house property) · counting development fees. |
| **Not available** | In the default regime (`DED-NEWREGIME-001`). |

### `DED-NPS-SELF-001` — the extra ₹50,000 · channel **E**

| | |
|---|---|
| **How much** | **Max ₹50,000**, and **only on amounts not already counted in the ₹1,50,000 bucket** (s.124(5), s.124(10)). |
| **Steps** | 1. Check how much of your NPS is already inside 80C. 2. Contribute the difference to your own Tier-I account by 31 March. 3. Submit the transaction statement. |
| **Common mistakes** | Double-counting the same rupees in both places — the single most common error in this area (`T5-3`: getting the order wrong is ₹60,000 too generous). |

### `DED-NPS-EMP-001`/`-002` — employer NPS · channel **P**

| | |
|---|---|
| **What it is** | Your **employer's** contribution to NPS, deductible up to 14% of (basic + DA) in the default regime and 10% in the other. **You cannot claim this yourself** — the employer must actually contribute. |
| **Steps** | 1. Ask HR whether the FBP offers employer NPS. 2. Elect it in the **FBP window**, not at proof time. 3. Check the aggregate: employer PF + NPS + superannuation above **₹7,50,000** becomes taxable income (`PF-AGGCAP-001`). |
| **If missing from Form 16** | **Not claimable.** There is nothing to claim. This is next year's FBP conversation. |
| **Notable** | This is the **one large deduction that survives the default regime**, and it is worth *more* there (14% vs 10%). |

### `DED-80D-001`…`-011` — health insurance · channel **E**

| | |
|---|---|
| **How much** | Two independent ₹50,000 ceilings — one for you and your family, one for your parents. Within each, insurance premium is capped at ₹25,000, or **₹50,000 if the insured is a senior citizen**. Preventive health check-ups: **₹5,000 across both**, and it is the **only** 80D payment allowed in cash. |
| **Steps** | 1. Pay premiums **other than in cash** (s.126(9)). 2. Get a premium certificate stating the period covered. 3. For parents, confirm their age — 60+ changes the ceiling. 4. If you paid a multi-year premium, claim **one fraction per year** (`DED-80D-009`). |
| **Ask whom** | Your insurer for the certificate; the hospital for a receipt where you are claiming senior-citizen medical expenditure. |
| **Common mistakes** | Paying in cash (disallowed) · claiming senior medical expenditure **and** a premium for the same person (`DED-80D-008`) · treating it as one flat cap (`H-11`). |

### `HP-CAP-SELFOCC-001` — home-loan interest, self-occupied · channel **E**

| | |
|---|---|
| **How much** | **Max ₹2,00,000** — but only if construction completed within five years of the year of borrowing **and** you hold a lender's certificate. Otherwise **₹30,000**. The cap is **aggregate across all your self-occupied houses**. |
| **Steps** | 1. Get the lender's interest certificate for the year — it must also show interest on any refinancing loan (s.22(4)(b)). 2. Declare the **loss** to your employer — this is the only thing that can *reduce* your salary TDS (`TDS-SAL-REDUCE-001`). 3. Track pre-construction interest separately: it is claimed in **five equal instalments**, and from TY 2026-27 it sits **inside** the ₹2,00,000 cap. |
| **Ask whom** | Your lender, for the certificate split into principal and interest. |
| **If missing from Form 16** | **Claimable in the return.** |
| **Not available** | For a self-occupied house in the default regime (`HP-NEW-001`). |

### `SAL-LTA-001` — leave travel · channel **E**

| | |
|---|---|
| **How much** | **Min ₹0. Max** = the **lower** of the LTA you received and what you actually spent on travel, capped by fare class and by two journeys per four-**calendar**-year block (current block **2026-2029**). |
| **Steps** | 1. Travel **within India**, and keep tickets. 2. Apply within your employer's LTA process. 3. Check your block: journeys used and whether a carry-over from 2022-2025 is available. |
| **Common mistakes** | Assuming it is exempt in full (`H-8`) · claiming with no travel · forgetting the block is **calendar** years while everything else is tax years (`13` R-12). |

### `DED-80G-001` — donations · channel **T** ⚠

| | |
|---|---|
| **Critical** | `FACT (S2, s.133(6))`: the deduction is allowed *"**only** on the basis of the information relating to such donation furnished by such institution or fund to the prescribed authority"*. **Your receipt is not enough.** |
| **Steps** | 1. Donate **other than in cash** if above ₹2,000 (s.133(5)) — and it must be **money**, not goods (s.133(4)). 2. Give the institution your PAN. 3. **Check your pre-filled data.** 4. If it is absent, **contact the institution** — the deduction is not allowable until they report it. |
| **If missing from Form 16** | Form 16 is irrelevant here. What matters is whether the **institution** reported it. |

### `DED-80TTA-001` / `DED-80TTB-001` — deposit interest · channel **R**

| | |
|---|---|
| **How much** | Under 60: **₹10,000**, savings accounts only, **excluding** time deposits. 60 or over: **₹50,000**, any account **including** time deposits. |
| **Steps** | 1. Get an interest certificate from each bank. 2. **Report the interest as income** — it is reported to the department for **all transactions** (r.237(6)). 3. Claim the deduction in the return. |
| **Note** | Employers generally do not apply this, so it is normally a **return-time** claim and a normal source of refund. |

### `DED-80U-001` / `DED-80DD-001` — disability · channel **R+**

| | |
|---|---|
| **How much** | **₹75,000**, or **₹1,25,000** for severe disability (80% or more). 80U is for **yourself**; 80DD is for a **dependant**. |
| **Steps** | 1. Obtain a certificate from a prescribed medical authority. 2. Note its expiry — a lapsed certificate ends the deduction until renewed. 3. **Retain** it; nothing is attached to the return (r.164(11)). |
| **Mutual exclusion** | A dependant who claims 80U for themselves cannot also be your 80DD dependant (s.127(8)). |

### `SAL-PTAX-001` — professional tax · channel **E**

| | |
|---|---|
| **How much** | The **entire amount** paid, typically ₹2,400 a year. |
| **Steps** | Usually already deducted and reflected. If not, claim it in the return with your payslips. |
| **Not available** | In the default regime (`SAL-PTAX-002`, `H-13`). |

### `REG-OPTOUT-SAL-001` — the regime election · channel **R**

| | |
|---|---|
| **What it is** | The default regime applies unless you opt out. For a salaried person **there is no separate form for TY 2026-27** — you do it **in the return itself** (r.136). |
| **Steps** | 1. Tell your employer which regime to deduct on, so your monthly TDS is roughly right. 2. **Decide again in the return** — you are not bound by what you told your employer. 3. Re-decide every year. |
| **Warning** | If you have **any** business or professional income, the lock-in in s.202(4)(a) applies and this stops being a free annual choice (`17` L3). |

---

## C6 — How much, and when a claim is not worth making

### The three amounts, restated per claim

`12-claim-integrity.md` C2 defines statutory maximum `S`, evidenced maximum `E`, declared `D`. The
catalogue adds the **minimum** — which is almost always ₹0, and where it is not, that is itself
information:

| Rule | Minimum | Why |
|---|---|---|
| Most deductions | ₹0 | nothing compels a claim |
| `DED-80GG-001` | effectively ₹0 | rent must exceed 10% of total income before anything arises |
| `SAL-HRA-001` | ₹0 | limb (b) is negative until rent exceeds 10% of salary |
| `PF-AGGCAP-001` | **not a claim** | the excess is *income*; it has no minimum, it is compulsory |

### Materiality — the "small claims" question

`INFERENCE`: two thresholds make a small claim genuinely invisible, and both are computable.

```java
public record Materiality(Money taxEffect, MaterialityBand band) {

    public static MaterialityBand classify(Money taxEffect) {
        // s.516 rounds the final payable to the nearest ₹10. A claim whose entire tax
        // effect is under ₹5 cannot change the rounded figure at all.
        if (taxEffect.isLessThan(Money.of("5")))   return INVISIBLE_AFTER_ROUNDING;
        if (taxEffect.isLessThan(Money.of("100"))) return BELOW_TYPICAL_EFFORT;
        return MATERIAL;
    }
}
```

| Band | Presentation |
|---|---|
| `INVISIBLE_AFTER_ROUNDING` | *"₹40 of interest income changes your tax by ₹2 — below the ₹10 rounding under s.516, so your final figure is unchanged. **Report it anyway**: it is reported to the department by your bank (r.237(6)), and a mismatch is a mismatch regardless of size."* |
| `BELOW_TYPICAL_EFFORT` | *"Worth ₹78. Claiming it needs a certificate from your insurer. Your call."* |
| `MATERIAL` | ranked normally in `A3` and `F33` |

`RECOMMENDATION`: **materiality never suppresses an item that increases income.** A small *deduction*
may be skipped on a cost-benefit basis; small *income* must still be reported, because C1's
reconciliation is about matching third-party data, not about the rupees. That asymmetry is the honest
line, and it is the same one `12-claim-integrity.md` C7 draws.

`INFERENCE`: `taxEffect` is **measured** by recomputation (`D-MARGINAL`), not estimated at a notional
marginal rate — which matters, because a claim worth ₹78 at 30% may be worth ₹0 inside a rebate
plateau (`REBATE-NEW-MR-001`) or ₹78 × 1.04 with cess at the top.

---

## C7 — Whom to ask

```java
public enum Counterparty {
    EMPLOYER_HR,        // FBP window, proof cut-off, Form No. 122/124, regime instruction
    EMPLOYER_PAYROLL,   // Form No. 130, Form No. 123, the annual PF figure
    INSURER,            // premium certificate, policy period
    BANK,               // interest certificate, TDS certificate, account kind
    LENDER,             // interest certificate split principal/interest, refinancing
    LANDLORD,           // PAN, rent receipts, agreement
    INSTITUTION,        // 80G donee — and whether they have REPORTED it
    MEDICAL_AUTHORITY,  // disability certificate and its validity
    SCHOOL,             // tuition fee receipt, excluding development fees
    NPS_POP,            // NPS transaction statement
    NOBODY              // you already have everything; claim it in the return
}
```

`RECOMMENDATION`: every catalogue entry names the counterparty **and the exact artefact to ask for**,
because *"ask your bank"* is not actionable and *"ask your bank for an interest certificate for
FY 2026-27 showing savings and time-deposit interest separately"* is. The separation matters:
`DED-80TTA-001` covers savings only, `DED-80TTB-001` covers both.

---

## C8 — Effects and tests

| Document | Change |
|---|---|
| `01-rulebook.md` | unchanged — it stays the legal record. The catalogue is a **sibling**, keyed by the same rule IDs |
| `05-domain-model.md` M1 | packs gain `claim-catalogue.json` |
| `12-claim-integrity.md` C3.2 | the documentation checklist becomes the catalogue's `evidence` list, so there is one source |
| `20-product-gaps.md` F33, F37 | completeness and explanations read `plainName`, `whatItIs` and `steps` from the catalogue |
| `04-product-spec.md` | **new `F40` — Claim playbooks**. R 1.0 · I 3 · C 0.9 · **E 3** → **1.00**. **MVP** |

### Invariants

| | |
|---|---|
| **I23** | every rule that can appear as a deduction or exemption in any pipeline has a catalogue entry — otherwise the product can compute a number it cannot explain how to claim |
| **I24** | every entry's `channel` is consistent with its rule's Chapter VIII Part: Part C rules **must** carry the s.122(5) late-return warning |
| **I25** | every `EvidenceRequirement` names what closes it |
| **I26** | every `ClaimStep` with a deadline resolves against the tax calendar (`F30`) |

### Tests

| ID | Case | Expected |
|---|---|---|
| **T22-1** | 80C present in the computation, absent from Form 16 | **"claimable in the return"**, refund impact shown |
| **T22-2** | 80G present, absent from pre-filled data | **"not allowable until the institution reports it"**, citing s.133(6) — **not** "claim it in the return" |
| **T22-3** | Employer NPS absent from Form 16 | **"not claimable — nothing to claim"**, routed to next year's FBP window |
| **T22-4** | 80U with an expired certificate | claim blocked; step 2 says renew |
| **T22-5** | Return filed after the due date, with 80C and 80U | both **still allowed** — Part B and Part E are outside the s.122(5) bar; interest and fee shown separately |
| **T22-6** | Return filed late with an 80RRB claim | **disallowed** — s.152 is in **Part C** |
| **T22-7** | Interest income of ₹40 | `INVISIBLE_AFTER_ROUNDING`, **and still reported** |
| **T22-8** | A deduction worth ₹78 | `BELOW_TYPICAL_EFFORT`, shown with its cost |
| **T22-9** | A deduction worth ₹78 nominally, inside a rebate plateau | `taxEffect` measured as **₹0**, not ₹78 × 30% |
| **T22-10** | Rent ₹96,000 a year | no landlord-PAN step (below the r.205(2) threshold) |
| **T22-11** | Rent ₹1,20,000 a year | landlord-PAN step present, counterparty `LANDLORD`, deadline = the employer's proof cut-off |
| **T22-12** | A pack rule with no catalogue entry | **load error**, invariant I23 |

---

## C9 — The short version

**Yes, and it turned up one thing worth stating plainly.** Nearly every deduction a salaried person
claims sits in Chapter VIII **Part B, D or E**, and `FACT (S2, s.122(5))`'s late-return bar applies
**only to Part C**. Filing late costs interest and a fee — it does **not** forfeit 80C, 80D or 80U.

Claims fall into **five channels**, and only two can be lost: **T** (80G, allowable only if the
institution reported it — s.133(6), your receipt is not enough) and **P** (employer NPS — there is
nothing to claim if the employer did not contribute). Everything else, including HRA and LTA, is
**fully claimable in the return** even when it is absent from Form 16.

Nothing is attached to the return (r.164(11)) — certificates are **retained and produced on demand**,
which is what the document vault is for.

And on small claims: a claim whose whole tax effect is under ₹5 cannot move the final figure, because
s.516 rounds to ₹10. But that only ever justifies skipping a **deduction**. Small **income** is still
reported, because it is matched against third-party data regardless of size.
