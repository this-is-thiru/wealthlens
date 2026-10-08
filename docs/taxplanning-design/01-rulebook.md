# 01 — Rulebook

**Scope:** every legal rule the `taxplanning` module needs, as a row with a stable ID.
**Years:** TY 2025-26 (Income-tax Act 1961) and TY 2026-27 (Income-tax Act 2025).
**Contract:** every later document — algorithms, tests, build plan — refers to these IDs and never
restates a number. If a number changes, exactly one row changes.

## How to read a row

| Column | Meaning |
|---|---|
| **Rule ID** | Stable. Never reused, never renumbered. |
| **1961-Act provision** | The provision that governs TY 2025-26. |
| **2025-Act provision** | The provision that governs TY 2026-27. |
| **TY 2025-26** / **TY 2026-27** | The value in force for that year. |
| **Regime** | `OLD` / `NEW` / `BOTH`. |
| **Status** | `VERIFIED` = primary gazette text opened this session. `NEEDS VERIFICATION` = not opened; what to check is stated. `STATUS UNCLEAR` = the source is ambiguous or self-inconsistent. |

Claim tags used in the prose: `FACT (evidence)`, `INFERENCE (from what)`, `RECOMMENDATION`.

## Sources actually opened (see PROGRESS.md verification log for the full table)

- **S2** — THE INCOME-TAX ACT, 2025 (No. 30 of 2025), gazette CG-DL-E-22082025-265620, **as enacted**. Not the FA-2026-amended reprint.
- **S3** — THE FINANCE ACT, 2026 (No. 4 of 2026), gazette CG-DL-E-31032026-271439, **final**.
- **S4** — Income-tax Rules, 2026, G.S.R. 198(E) dated 20-03-2026, gazette CG-DL-E-20032026-271092, **final as notified**.
- **S5** — Corrigendum G.S.R. 286(E) dated 16-04-2026 to S4.
- **S6** — Income-tax (Fourth Amendment) Rules, 2026, G.S.R. 822(E) dated 17-09-2026. Its foot-note records that S4 was "last amended by G.S.R. 656(E), dated 24 July 2026" — **that intermediate chain was not opened**, so every Rules-2026 row below is "as notified, amendment chain not exhaustively traced".
- **S7** — THE FINANCE ACT, 2025 (No. 7 of 2025), gazette CG-DL-E-29032025-262125, **final**.
- **S1** — PIB release 2248005 dated 01-04-2026.

**The systematic gap in this rulebook:** `incometaxindia.gov.in` refused every request this session
(HTTP 403 via both `curl` and the fetch tool), and `indiacode.nic.in` returned 404 for the Act
handle. So the **base text of the Income-tax Act 1961 and the whole of the Income-tax Rules 1962
were never opened**. Everything below that depends on them is `NEEDS VERIFICATION`. That is most of
the TY 2025-26 *detail* rows (HRA percentages, allowance caps, perquisite valuations); it is **not**
the TY 2025-26 *rate* rows, which come from the Finance Acts and are `VERIFIED`.

---

## R1 — Tax-year model

| Rule ID | Statement | 1961-Act provision | 2025-Act provision | Status |
|---|---|---|---|---|
| `YR-MODEL-001` | Under the 1961 Act income of a **previous year** is assessed in the following **assessment year**. TY 2025-26 = PY/FY 2025-26 = AY 2026-27. | s.2(9), s.3 — *not opened* | — | NEEDS VERIFICATION — open 1961 Act ss.2(9), 3 |
| `YR-MODEL-002` | Under the 2025 Act there is one period only: `"tax year" means the twelve months period of the financial year commencing on the 1st April`. No assessment year. | — | **s.3(1)** | VERIFIED (S2, s.3(1)) |
| `YR-MODEL-003` | A newly set-up business or a newly arising source has a short first tax year: from the date of setting up / arising to the end of that financial year. Irrelevant to a salaried individual but must not be modelled away. | — | **s.3(2)** | VERIFIED (S2, s.3(2)) |
| `YR-ACT-001` | The Income-tax Act 2025 (No. 30 of 2025) received assent on **21 August 2025** and, save as otherwise provided, **came into force on 1 April 2026**. | — | **s.1(3)** | VERIFIED (S2, s.1(3); S1) |
| `YR-ACT-002` | **TY 2025-26 is governed by the Income-tax Act 1961**, at the rates in Finance Act 2026 s.2 read with **Part I-A of its First Schedule** ("for the assessment year commencing on the 1st day of April, 2026"). | FA 2026 s.2 | — | VERIFIED (S3, s.2(1)) |
| `YR-ACT-003` | **TY 2026-27 is governed by the Income-tax Act 2025**, at the rates in Finance Act 2026 s.3 read with **Part I-B of its First Schedule** ("for the tax year commencing on the 1st day of April, 2026"). | — | FA 2026 s.3 | VERIFIED (S3, s.3(1)) |
| `YR-ACT-004` | The Income-tax **Rules, 2026** (G.S.R. 198(E), 20-03-2026) operationalise the 2025 Act from 1 April 2026 and therefore apply to TY 2026-27 and later. The Income-tax Rules 1962 continue to govern TY 2025-26. | Rules 1962 | Rules 2026 | VERIFIED for Rules 2026 (S4, S1); Rules-1962 continuation is INFERENCE from S1 + S4 preamble |
| `YR-ACT-005` | The charge of income-tax is imposed by a Central Act for each tax year at the rate(s) that Act specifies. The Act supplies the machinery; the **Finance Act supplies most rates** — but **not all**: the new-regime slab table lives in the Act itself for both years (`RATE-SLAB-NEW-*`). | s.4 — *not opened* | **s.4(1)** | VERIFIED for 2025 Act (S2, s.4(1)); 1961 Act s.4 NEEDS VERIFICATION |
| `YR-TRANSIT-001` | A user in September 2026 is simultaneously **filing** TY 2025-26 (1961 Act) and **planning** TY 2026-27 (2025 Act). Both must be computable at the same instant, with no shared mutable state. | — | — | INFERENCE (from `YR-ACT-002`, `YR-ACT-003`, `ITR-DUE-001`, `ITR-DUE-002`) |

**Design consequence.** `INFERENCE`: the unit of versioning is the **(tax year → governing Act →
rule pack)** triple, not the tax year alone. Two rule packs must be loadable and evaluable
concurrently in one JVM, because `YR-TRANSIT-001` is the normal case, not an edge case.

---

## R2 — Regime framework

| Rule ID | Statement | 1961-Act provision | 2025-Act provision | Regime | Status |
|---|---|---|---|---|---|
| `REG-DEFAULT-001` | The **new regime is the default**. Tax "shall, **unless the person exercises the option** in the manner provided under sub-section (4), be computed at the rate of tax given in the following Table". | s.115BAC(1A) | **s.202(1)** | — | VERIFIED for TY 2026-27 (S2, s.202(1)); for TY 2025-26, FA 2025 s.25 amends 115BAC(1A) by inserting the AY-2026-27-onwards table, which presupposes the same default — VERIFIED as to the table (S7 s.25), default mechanics NEEDS VERIFICATION (open 1961 Act s.115BAC(1A) opening words and s.115BAC(6)) |
| `REG-OPTOUT-SAL-001` | For a person **without** business or professional income (the D1 primary user), the opt-out option is exercised **"along with the return of income to be furnished under section 263(1) for the tax year"**. | s.115BAC(6) — *not opened* | **s.202(4)(b)** | OLD | VERIFIED for TY 2026-27 (S2, s.202(4)(b)); TY 2025-26 NEEDS VERIFICATION (open 1961 Act s.115BAC(6) and Rule 21AGA / Form 10-IEA of Rules 1962) |
| `REG-OPTOUT-FORM-001` | For TY 2026-27 there is **no separate option form**: `rule 136` — *"The option to be exercised or withdrawn ... shall be **in the return of income** to be furnished under section 263(1) for such tax year"*, Table Sl. No. 4 being s.202(4) for an individual/HUF/AOP/BOI/AJP. | — | **Rules 2026, rule 136** | OLD | VERIFIED (S4, rule 136 + Table Sl. 4) |
| `REG-OPTOUT-YEARLY-001` | Because the option rides on the return and is expressed "for any tax year", a salaried person **may switch regime every year**. The lock-in and one-time-withdrawal rules in s.202(4)(a) apply only to a person **having** business or professional income. | s.115BAC(6) | **s.202(4)(a) vs (4)(b)** | — | VERIFIED for TY 2026-27 (S2, s.202(4)(a)(ii)-(iv) vs (4)(b)); TY 2025-26 NEEDS VERIFICATION |
| `REG-OPTOUT-LATE-001` | The option is tied to *the* return under s.263(1). Whether a **belated or updated** return can still carry a valid opt-out is not resolved by the text opened. | — | s.202(4)(b) r/w s.263 | OLD | **STATUS UNCLEAR** — open CBDT guidance / s.263(4) and (6) interplay before relying on it |
| `REG-NEW-DISALLOW-001` | Computing under the new regime disallows, exhaustively: Schedule III Table Sl. Nos. **5, 6, 7, 8, 11, 17**; Sl. Nos. **12, 13** other than as prescribed; s.**19(1) Table Sl. No. 1** (professional tax); s.**22(1)(b)** for s.21(6) (self-occupied) property; ss.33(8), 48, 49, 45(3)(a)/(b)/(c), 46, 47(1)(a); and the **whole of Chapter VIII except ss.124(1), 124(2), 125(2) and 146**. | s.115BAC(2) — *not opened* | **s.202(2)(a)** | NEW | VERIFIED for TY 2026-27 (S2, s.202(2)(a)), with `s.202(2)(a)(iii)` (which had disallowed s.144, the SEZ deduction) **omitted by FA 2026 s.56** — VERIFIED (S3, Ch. III Part B s.56) |
| `REG-NEW-DISALLOW-002` | Under the new regime, **no set-off** of (i) brought-forward loss or depreciation attributable to a disallowed deduction, and (ii) **any loss under the head "Income from house property" against any other head**. Such loss is deemed fully given effect to and is **not** carried forward. | s.115BAC(2) | **s.202(2)(b), 202(3)** | NEW | VERIFIED (S2, s.202(2)(b), (3)) |
| `REG-NEW-DISALLOW-003` | Under the new regime, no exemption or deduction for allowances or perquisites **granted under any other law in force**. | s.115BAC(2) | **s.202(2)(c)** | NEW | VERIFIED (S2, s.202(2)(c)) |

> **Addendum (W-052d, 2026-10-06, D-133):** TY 2025-26's election is now read in the Gazette — 1961 s.115BAC(6) as
> inserted by the **Finance Act, 2023 s.52(C)(e)** from 1-4-2024 (CG-DL-E-31032023-244830, p.35): without business or
> profession income, the option is exercised *"along with the return of income to be furnished under sub-section (1) of
> section 139"*; with it, *"on or before the due date specified under sub-section (1) of section 139"*, binding later
> years, withdrawable once. Neither the Finance (No. 2) Act, 2024 s.37 nor the Finance Act, 2025 s.25 touches (6). The
> *"manner as may be prescribed"* (Rules 1962 r.21AGA / Form 10-IEA) and the interim Finance Act, 2024 remain unread, so
> the TY 2025-26 election versions are `UNVERIFIED`.

**H-16 verdict (regime-choice rules for salaried users): CONFIRMED as a real risk area.** The
salaried opt-out is a *return-time* act tied to a *specific* tax year, and for TY 2026-27 it is not
even a separate form. Any model that stores "the user's regime" as a profile attribute rather than
as a **per-tax-year, per-computation input** is wrong. See `M5` and `X3`.

---

## R3 — Slabs

### `RATE-SLAB-NEW-2026` — New regime, TY 2026-27 (Act 2025 s.202(1))

| Sl. | Total income | Rate |
|---|---|---|
| 1 | Up to ₹4,00,000 | Nil |
| 2 | ₹4,00,001 to ₹8,00,000 | 5% |
| 3 | ₹8,00,001 to ₹12,00,000 | 10% |
| 4 | ₹12,00,001 to ₹16,00,000 | 15% |
| 5 | ₹16,00,001 to ₹20,00,000 | 20% |
| 6 | ₹20,00,001 to ₹24,00,000 | 25% |
| 7 | Above ₹24,00,000 | 30% |

**Status: VERIFIED** (S2, s.202(1) Table). **FA 2026 did not change it** — FA 2026 s.56 touches only
s.202(2)(a)(iii). VERIFIED (S3, Ch. III Part B s.56).

### `RATE-SLAB-NEW-2025` — New regime, TY 2025-26 (1961 Act s.115BAC(1A)(iii))

Identical bands and rates to `RATE-SLAB-NEW-2026`: Nil / 5 / 10 / 15 / 20 / 25 / 30 at
₹4L / ₹8L / ₹12L / ₹16L / ₹20L / ₹24L.

**Status: VERIFIED** (S7, Finance Act 2025 s.25, inserting s.115BAC(1A)(iii) *"for any previous year
relevant to the assessment year beginning on or after the 1st April, 2026"*).

### `RATE-SLAB-OLD-2025` — Old regime, TY 2025-26 (FA 2026 First Schedule Part I-A Paragraph A)

| Age band | Bands |
|---|---|
| `RATE-SLAB-OLD-2025-A` — under 60 | Nil to ₹2,50,000; 5% of excess over ₹2,50,000 up to ₹5,00,000; **₹12,500 + 20%** of excess over ₹5,00,000 up to ₹10,00,000; **₹1,12,500 + 30%** of excess over ₹10,00,000 |
| `RATE-SLAB-OLD-2025-B` — resident, 60 to under 80 | Nil to ₹3,00,000; 5% over ₹3,00,000 up to ₹5,00,000; **₹10,000 + 20%** over ₹5,00,000 up to ₹10,00,000; **₹1,10,000 + 30%** over ₹10,00,000 |
| `RATE-SLAB-OLD-2025-C` — resident, 80 or more | Nil to ₹5,00,000; **20%** over ₹5,00,000 up to ₹10,00,000; **₹1,00,000 + 30%** over ₹10,00,000 |

**Status: VERIFIED** (S3, First Schedule Part I, A, Paragraph A, items (I), (II), (III)).

### `RATE-SLAB-OLD-2026` — Old regime, TY 2026-27 (FA 2026 First Schedule Part I-B Paragraph A)

Identical to `RATE-SLAB-OLD-2025` in every band and every rate, restated in the 2025-Act idiom
("tax year" instead of "previous year", `₹250000` instead of `Rs. 2,50,000`).

**Status: VERIFIED** (S3, First Schedule Part I, B, Paragraph A, items (I), (II), (III)).

> **Erratum (W-045d, 2026-09-24):** the R3 rule IDs put the year and "OLD"/"NEW" in the identity,
> which D-035 forbids — they are renamed to concepts chosen by the owner from the Acts' wording
> (D-051). The year lives in the version; one rule spans every year its bands are unchanged:
>
> | Old ID | New ID | Wording it is taken from |
> |---|---|---|
> | `RATE-SLAB-NEW-2025` | `RATE-SLAB-NEW-TAX-REGIME` | S2 s.202 heading: "New tax regime for individuals, Hindu undivided family and others." |
> | `RATE-SLAB-NEW-2026` | `RATE-SLAB-NEW-TAX-REGIME` | same |
> | `RATE-SLAB-OLD-2025` | `RATE-SLAB-FIRST-SCHEDULE` | S3 First Schedule Part I, Paragraph A — "Rates of income-tax" |
> | `RATE-SLAB-OLD-2025-A` | `RATE-SLAB-FIRST-SCHEDULE` | Para A item (I) |
> | `RATE-SLAB-OLD-2025-B` | `RATE-SLAB-FIRST-SCHEDULE-AGE-60TO79` | item (II): "of the age of sixty years or more but less than eighty years" |
> | `RATE-SLAB-OLD-2025-C` | `RATE-SLAB-FIRST-SCHEDULE-AGE-80` | item (III): "of the age of eighty years or more" |
> | `RATE-SLAB-OLD-2026` | `RATE-SLAB-FIRST-SCHEDULE` | Part I-B Paragraph A |
>
> The regime codes follow the same decision: default regime `NEW_TAX_REGIME`, opted-out
> `FIRST_SCHEDULE_RATES`. The other year- or regime-suffixed IDs in this rulebook are mapped by
> `W-045d2`. Verified by: S2 p.226 (s.202 marginal heading), S3 p.84–85 (Part I-B Paragraph A),
> read 2026-09-24; `RuleIdNamingTest`.
> **Erratum (W-045d2, 2026-09-25):** every other rulebook ID that carries a year, "OLD"/"NEW" or a
> section number breaks D-035 — each maps to one concept name, taken from its own provision and chosen by
> the owner (D-069). A year pair becomes one ID with a version per year. The old IDs below stay in the
> rows above as written; read them through this table.
>
> | Old ID | New ID | Provision it is named from |
> |---|---|---|
> | `REG-NEW-DISALLOW-001` | `REG-NEW-TAX-REGIME-NO-EXEMPTION` | Income-tax Act, 2025 (S2) s.202(2)(a), p.227 |
> | `REG-NEW-DISALLOW-002` | `REG-NEW-TAX-REGIME-NO-SETOFF` | Income-tax Act, 2025 (S2) s.202(2)(b), (3), p.227 |
> | `REG-NEW-DISALLOW-003` | `REG-NEW-TAX-REGIME-OTHER-LAW` | Income-tax Act, 2025 (S2) s.202(2)(c), p.228 |
> | `SAL-LTA-NEW-001` | `SAL-LTA-NEW-TAX-REGIME` | Income-tax Act, 2025 (S2) s.202(2)(a)(i) |
> | `SAL-ALW-NEW-001` | `SAL-ALW-NEW-TAX-REGIME` | Income-tax Act, 2025 (S2) s.202(2)(a)(ii) |
> | `HP-NEW-001` | `HP-NEW-TAX-REGIME-INTEREST` | Income-tax Act, 2025 (S2) s.202(2)(a)(v); s.22(1)(b) |
> | `HP-NEW-002` | `HP-NEW-TAX-REGIME-LOSS` | Income-tax Act, 2025 (S2) s.202(2)(b)(ii), (3) |
> | `SETOFF-NEW-001` | `SETOFF-NEW-TAX-REGIME` | Income-tax Act, 2025 (S2) s.202(2)(b), (3) |
> | `SETOFF-NEW-002` | `SETOFF-NEW-TAX-REGIME-HP-LOSS` | Income-tax Act, 2025 (S2) s.202(3) |
> | `DED-NEWREGIME-001` | `DED-NEW-TAX-REGIME` | Income-tax Act, 2025 (S2) s.202(2)(a)(xii) |
> | `REBATE-OLD-001` | `REBATE-RESIDENT-INDIVIDUAL` | Income-tax Act, 2025 (S2) s.156(1) |
> | `REBATE-NEW-001` | `REBATE-NEW-TAX-REGIME` | Income-tax Act, 2025 (S2) s.156(2)(a) |
> | `REBATE-NEW-MR-001` | `REBATE-NEW-TAX-REGIME-EXCESS` | Income-tax Act, 2025 (S2) s.156(2)(b) |
> | `REBATE-NEW-MR-002` | `REBATE-NEW-TAX-REGIME-LIMIT` | Income-tax Act, 2025 (S2) s.156(3) |
> | `RATE-SUR-OLD-2025` | `RATE-SUR-FIRST-SCHEDULE` | Finance Act, 2026 (S3) s.2(1); First Schedule Part I-A |
> | `RATE-SUR-OLD-2026` | `RATE-SUR-FIRST-SCHEDULE` | Finance Act, 2026 (S3) s.3(1); First Schedule Part I-B |
> | `RATE-SUR-NEW-2025` | `RATE-SUR-NEW-TAX-REGIME` | Finance Act, 2026 (S3) s.2(4)(b) Table Sl. 10 |
> | `RATE-SUR-NEW-2026` | `RATE-SUR-NEW-TAX-REGIME` | Finance Act, 2026 (S3) s.3(4)(b) Table Sl. 10 |
> | `SAL-HRA-METRO-2025` | `SAL-HRA-LOCATION` | Income-tax Rules, 2026 (S4) r.279(1)(c) Table |
> | `SAL-HRA-METRO-2026` | `SAL-HRA-LOCATION` | Income-tax Rules, 2026 (S4) r.279(1)(c) Table |
> | `SAL-ALW-DUTY-LIST-2026` | `SAL-ALW-DUTY-LIST` | Income-tax Rules, 2026 (S4) r.280(1) |
> | `SAL-ALW-NEW-SUBSET-001` | `SAL-ALW-OPTION-SUBSET` | Income-tax Rules, 2026 (S4) r.280(3) |
> | `SAL-ALW-LIMITS-2025` | `SAL-ALW-LIMITS` | Income-tax Rules, 2026 (S4) r.280(2) |
> | `SAL-ALW-LIMITS-2026` | `SAL-ALW-LIMITS` | Income-tax Rules, 2026 (S4) r.280(2) |
> | `SAL-ALW-TRANSPORTSYS-2026` | `SAL-ALW-TRANSPORT-SYSTEM` | Income-tax Rules, 2026 (S4) r.280(2) Table Sl. 6 |
> | `SAL-ALW-CEA-2026` | `SAL-ALW-CHILDREN-EDUCATION` | Income-tax Rules, 2026 (S4) r.280(2) Table Sl. 7 |
> | `SAL-ALW-HOSTEL-2026` | `SAL-ALW-HOSTEL` | Income-tax Rules, 2026 (S4) r.280(2) Table Sl. 8 |
> | `SAL-ALW-TRANSPORT-DIS-2026` | `SAL-ALW-TRANSPORT-HANDICAP` | Income-tax Rules, 2026 (S4) r.280(2) Table Sl. 10 |
> | `SAL-ALW-ARMED-2026` | `SAL-ALW-ARMED-FORCES` | Income-tax Rules, 2026 (S4) r.280(2) Table Sl. 9, 11, 13–15 |
> | `PERQ-CAR-2025` | `PERQ-MOTOR-CAR` | Income-tax Rules, 2026 (S4) r.15(3)(a) Table II |
> | `PERQ-CAR-2026` | `PERQ-MOTOR-CAR` | Income-tax Rules, 2026 (S4) r.15(3)(a) Table II |
> | `PERQ-OTHER-2026` | `PERQ-OTHER-BENEFITS` | Income-tax Rules, 2026 (S4) r.15(5)(a) Table IV |
> | `PERQ-MEAL-2026` | `PERQ-FOOD-BEVERAGES` | Income-tax Rules, 2026 (S4) r.15 Table IV |
> | `PERQ-GIFT-2026` | `PERQ-GIFT` | Income-tax Rules, 2026 (S4) r.15 Table IV |
> | `PERQ-CREDITCARD-2026` | `PERQ-CREDIT-CARD` | Income-tax Rules, 2026 (S4) r.15 Table IV Sl. 5 |
> | `PERQ-CLUB-2026` | `PERQ-CLUB` | Income-tax Rules, 2026 (S4) r.15 Table IV |
> | `PERQ-MOVABLE-2026` | `PERQ-MOVABLE-ASSET` | Income-tax Rules, 2026 (S4) r.15 Table IV |
> | `PERQ-SERVANT-2026` | `PERQ-SERVICES` | Income-tax Rules, 2026 (S4) r.15 Table III Sl. 1 |
> | `PERQ-UTIL-2026` | `PERQ-GAS-ELECTRICITY-WATER` | Income-tax Rules, 2026 (S4) r.15 Table III Sl. 2 |
> | `PERQ-EDU-2026` | `PERQ-EDUCATION` | Income-tax Rules, 2026 (S4) r.15 Table III Sl. 3 |
> | `PERQ-ACCOM-2026` | `PERQ-ACCOM` | Income-tax Rules, 2026 (S4) r.15(1) Table I |
> | `PERQ-ACCOM-LEASE-2026` | `PERQ-ACCOM-LEASE` | Income-tax Rules, 2026 (S4) r.15 Table I (2)(b) |
> | `PERQ-ACCOM-HOTEL-2026` | `PERQ-ACCOM-HOTEL` | Income-tax Rules, 2026 (S4) r.15 Table I (3) |
> | `PERQ-ACCOM-CAP-2026` | `PERQ-ACCOM-CONTINUED` | Income-tax Rules, 2026 (S4) r.15(1)(d) |
> | `PERQ-ACCOM-FURN-2026` | `PERQ-ACCOM-FURNISHED` | Income-tax Rules, 2026 (S4) r.15 Table I column D |
> | `PERQ-ACCOM-TRANSFER-2026` | `PERQ-ACCOM-TRANSFER` | Income-tax Rules, 2026 (S4) r.15(1) |
> | `PERQ-ACCOM-SITE-2026` | `PERQ-ACCOM-SITE` | Income-tax Rules, 2026 (S4) r.15(1) |
> | `PERQ-ACCOM-OWNED-2026` | `PERQ-ACCOM-OWNED` | Income-tax Rules, 2026 (S4) r.15 Table I (2)(a) |
> | `PERQ-LOAN-2026` | `PERQ-LOAN` | Income-tax Rules, 2026 (S4) r.15(5)(a) Table IV Sl. 1 |
> | `PERQ-PHONE-2026` | `PERQ-TELEPHONE` | Income-tax Rules, 2026 (S4) r.15 Table IV |
> | `OS-DIV-DED-2025` | `OS-DIVIDEND-DEDUCTION` | Income-tax Act, 2025 (S2) s.93(2)(b) as enacted |
> | `OS-DIV-DED-2026` | `OS-DIVIDEND-DEDUCTION` | Income-tax Act, 2025 (S2) s.93(2) as substituted by Finance Act, 2026 (S3) s.44(b) |
>
> Verified by: `docs/taxplanning-build/tools/check_rule_id_mapping.py` (every old ID exactly once; every new ID
> passes `RuleIdNamingTest`); the provisions' wording is quoted per row in `docs/taxplanning-build/plans/W-045d2.md`.

### Notes that the code must honour

- `RATE-SLAB-BAND-001` — **FACT (S2 s.202(1); S3 Part I-A/I-B)**: statutory bands are written as
  *"From ₹400001 to ₹800000"* (new regime) and *"exceeds ₹2,50,000 but does not exceed ₹5,00,000"*
  (old regime). Both describe the **same half-open partition of the rupee line**. A band's taxable
  width is `min(TI, upper) − lower` where `lower` is the **previous band's upper**, i.e. ₹4,00,000,
  not ₹4,00,001. Reading `400001` as the arithmetic lower bound loses one rupee per band.
  **This refutes nothing about the law and confirms H-5's first limb as an implementation defect.**
- `RATE-SLAB-BAND-002` — **RECOMMENDATION**: model a band as `(lowerExclusiveRupees, upperInclusiveRupees, ratePercent)`
  and derive the arithmetic lower bound as `lowerExclusive`. Never store `lower + 1`.
- `RATE-SLAB-AGE-001` — **FACT (S3 Part I-A Para A (II)/(III), Part I-B Para A (II)/(III))**: the
  higher old-regime basic exemption applies only to a **resident** individual, and the age test is
  *"at any time during the previous year / tax year"* — not age on the last day.
- `RATE-SLAB-AGE-002` — **FACT (S2 s.202(1))**: the new-regime table has **no age variants**. A
  senior citizen in the new regime gets ₹4,00,000, the same as everyone else.

---

## R4 — Rebate and rebate marginal relief

| Rule ID | Statement | 1961-Act | 2025-Act | Regime | TY 2025-26 | TY 2026-27 | Status |
|---|---|---|---|---|---|---|---|
| `REBATE-OLD-001` | Resident individual: deduction of **100% of income-tax or ₹12,500, whichever is less**, if total income does not exceed **₹5,00,000**. | s.87A main limb | **s.156(1)** | OLD (see note) | ₹12,500 / ₹5,00,000 | ₹12,500 / ₹5,00,000 | VERIFIED for TY 2026-27 (S2, s.156(1)); TY 2025-26 NEEDS VERIFICATION (FA 2025 s.20 amends only the *proviso*, so the main limb is unamended — open 1961 Act s.87A base text to confirm the ₹12,500/₹5,00,000 figures) |
| `REBATE-NEW-001` | Where total income is chargeable under the new regime and **does not exceed ₹12,00,000**: deduction of **100% of income-tax or ₹60,000, whichever is less**. | s.87A first proviso (a) | **s.156(2)(a)** | NEW | ₹60,000 / ₹12,00,000 | ₹60,000 / ₹12,00,000 | VERIFIED both years (S2 s.156(2)(a); S7 FA 2025 s.20(a)(i) substituting "twelve hundred thousand rupees" and "sixty thousand rupees") |
| `REBATE-NEW-MR-001` | **Rebate marginal relief.** Where total income **exceeds ₹12,00,000** and the income-tax payable on it **exceeds the amount by which total income exceeds ₹12,00,000**, the deduction is `tax − (TI − 12,00,000)`. | s.87A first proviso (b) | **s.156(2)(b)** | **NEW only** | applies | applies | VERIFIED both years (S2 s.156(2)(b); S7 FA 2025 s.20(a)(ii)) |
| `REBATE-NEW-MR-002` | The deduction under `REBATE-NEW-MR-001` **shall not exceed the income-tax payable at the new-regime rates**. | s.87A second proviso (inserted by FA 2025 s.20(b)) | **s.156(3)** | NEW | applies | applies | VERIFIED both years (S2 s.156(3); S7 FA 2025 s.20(b)) |
| `REBATE-CAP-001` | The deduction under s.156 shall not exceed income-tax computed before allowing it. | s.87A | **s.155(2)** | BOTH | applies | applies | VERIFIED (S2, s.155(2)) |

> **Erratum (W-045n, 2026-09-25):** `REBATE-OLD-001`'s TY 2025-26 figures, marked NEEDS VERIFICATION,
> are confirmed from sources — the ₹12,500 / ₹5,00,000 of the 1961 Act s.87A main limb were set by
> the Finance Act, 2019 s.8 (w.e.f. 1-4-2020), and no Finance Act since changed the main limb: the
> Finance Act, 2023 s.44 and the Finance Act, 2025 s.20 amend only the proviso, and the Finance
> (No. 2) Act 2019, Finance Acts 2020, 2021, 2022, 2024 and Finance (No. 2) Act 2024 do not amend
> s.87A. The two figures are `SOURCE_CHECKED` for TY 2025-26; the main limb's wording ("resident",
> "individual", "whichever is less") was not read in the base text, and non-Finance amending Acts
> were not searched — that residual is the version's `whatToCheck`.
> Verified by: FA 2019 s.8 (p.9, gstcouncil.gov.in copy); FA 2023 s.44 (egazette 2023/244830, p.32);
> FA 2025 s.20 (egazette 2025/262125, p.23); the others searched in full — `plans/W-045n.md` Findings Q1.

### **H-3 verdict: CONFIRMED — applying rebate marginal relief in the old regime is wrong.**

`FACT (S2, s.156)`: s.156 has two sub-sections. Sub-section (1) is the ₹12,500/₹5,00,000 rebate and
contains **no** marginal-relief limb. Sub-section (2) opens *"Where the total income of a resident
individual assessee for any tax year **is chargeable to tax under section 202(1)**"* — the new
regime — and only **its** clause (b) carries marginal relief. `INFERENCE`: therefore rebate marginal
relief exists **only** in the new regime, for both years (the 1961-Act text has the same two-tier
shape: FA 2025 amended only the *proviso*, and inserted a further proviso capping the deduction at
tax computed under 115BAC(1A)). A shared `AbstractTaxEngine` that keys marginal relief off a generic
`rebate87aLimit` field will run it for the old regime as well, which the statute does not permit.

### `REBATE-ORDER-001` — where rebate sits

`FACT (S2, s.155(1))`: the deduction is allowed **"from income-tax (as computed before allowing the
deductions under this Part)"**. `FACT (S3, s.3(15))`: cess is 4% of "such income-tax **and
surcharge**". `INFERENCE (from both)`: the order is
`slab tax → less rebate → plus surcharge → plus cess`. Form No. 130 Annexure-I row 17 states
`Tax payable (13+15+16-14)` where 13 = tax, 14 = rebate, 15 = surcharge, 16 = cess — that is an
arithmetic sum of already-computed rows, **not** a statement about the base of rows 15 and 16, and
must not be read as one. `FACT`: the question is moot for rebate + surcharge together, because
rebate needs TI ≤ ₹12,00,000 (new) or ≤ ₹5,00,000 (old) and surcharge starts at ₹50,00,000 — the two
can never co-apply. It is **not** moot for cess, and cess must be charged on post-rebate tax.

### Worked example — `REBATE-NEW-MR-001`, TY 2026-27, new regime, total income ₹12,10,000

```
Slab tax (RATE-SLAB-NEW-2026):
  0        – 4,00,000   @ Nil                                   =        0
  4,00,000 – 8,00,000   @ 5%   → 4,00,000 × 0.05                =   20,000
  8,00,000 – 12,00,000  @ 10%  → 4,00,000 × 0.10                =   40,000
  12,00,000– 12,10,000  @ 15%  →   10,000 × 0.15                =    1,500
                                                        tax     =   61,500

REBATE-NEW-001:  TI 12,10,000 > 12,00,000  → not available.
REBATE-NEW-MR-001:
  excess over 12,00,000 = 12,10,000 − 12,00,000                 =   10,000
  tax (61,500) > excess (10,000)?  yes → relief applies
  rebate = 61,500 − 10,000                                      =   51,500
REBATE-NEW-MR-002 cap: 51,500 ≤ 61,500                          →  ok

Tax after rebate = 61,500 − 51,500                              =   10,000
Surcharge (RATE-SUR-NEW-2026): TI ≤ 50,00,000                   =        0
Cess (RATE-CESS-001): 4% × 10,000                               =      400
Total                                                           =   10,400
Round (RATE-ROUND-002) to nearest ₹10                            =   10,400
```

`INFERENCE`: the relief makes the effective marginal rate in the relief band exactly 100% — every
extra rupee of income above ₹12,00,000 is taken as tax — until tax computed normally falls to or
below the excess. That crossover is where `REBATE-NEW-MR-001` stops binding; see `T1`.

---

## R5 — Surcharge

### `RATE-SUR-OLD-2025` — TY 2025-26, old regime (FA 2026 First Schedule Part I-A Paragraph F Table 1 Sl. No. 1)

| Total income | Rate |
|---|---|
| > ₹50,00,000 and ≤ ₹1,00,00,000 (**including** dividend and ss.111A/112/112A gains) | 10% |
| > ₹1,00,00,000 and ≤ ₹2,00,00,000 (**including** those) | 15% |
| > ₹2,00,00,000 and ≤ ₹5,00,00,000 (**excluding** those) | 25% |
| > ₹5,00,00,000 (**excluding** those) | 37% |
| > ₹2,00,00,000 (including those) but not covered by the 25% or 37% rows | 15% |
| On the tax attributable to dividend / 111A / 112 / 112A income | **never more than 15%** |

**Status: VERIFIED** (S3, Part I-A Para F Table 1 Sl. 1, clauses (i)–(vi)).

### `RATE-SUR-NEW-2025` — TY 2025-26, new regime (FA 2026 s.2(4)(b) Table Sl. No. 10)

| Total income | Rate |
|---|---|
| > ₹50,00,000 and ≤ ₹1,00,00,000 (including dividend/111A/112/112A) | 10% |
| > ₹1,00,00,000 and ≤ ₹2,00,00,000 (including those) | 15% |
| > ₹2,00,00,000 (**excluding** those) | **25% — there is no 37% band** |
| > ₹2,00,00,000 (including those) but not covered by the 25% row | 15% |
| On the tax attributable to dividend / 111A / 112 / 112A income | never more than 15% |

**Status: VERIFIED** (S3, s.2(4)(b) Table Sl. No. 10, clauses (i)–(v)).

### `RATE-SUR-OLD-2026` — TY 2026-27, old regime (FA 2026 First Schedule Part I-B Paragraph F Table 1 Sl. No. 1)

Same shape as `RATE-SUR-OLD-2025`: 10% / 15% / 25% / 37% at ₹50L / ₹1Cr / ₹2Cr / ₹5Cr, with the
include/exclude split and the 15% cap on the dividend-and-special-gains slice. The cross-references
are to 2025-Act ss.**196, 197, 198** in place of 1961-Act ss.111A, 112, 112A.

**Status: VERIFIED** (S3, Part I-B Para F Table 1 Sl. 1).

### `RATE-SUR-NEW-2026` — TY 2026-27, new regime (FA 2026 s.3(4)(b) Table Sl. No. 10)

| Total income | Rate |
|---|---|
| > ₹50,00,000 and ≤ ₹1,00,00,000 | 10% |
| > ₹1,00,00,000 and ≤ ₹2,00,00,000 | 15% |
| > ₹2,00,00,000 (excluding dividend / ss.196,197,198 gains) | **25% — no 37% band** |
| > ₹2,00,00,000 (including those), not covered above | 15% |
| On the dividend / ss.196,197,198 slice | never more than 15% |

**Status: VERIFIED** (S3, s.3(4)(b) Table Sl. No. 10).

> **Erratum (W-054, 2026-09-25):** each table above becomes **two** pack rows, because the Act tests two
> incomes. `RATE-SUR-FIRST-SCHEDULE` / `RATE-SUR-NEW-TAX-REGIME` hold the clauses on total income
> *including* dividends and the special gains (10%, 15%, and the "not covered → 15%" clause).
> `RATE-SUR-FIRST-SCHEDULE-EXCLUDING` / `RATE-SUR-NEW-TAX-REGIME-EXCLUDING` hold the clauses on total
> income *excluding* them (25%; 37% in the first schedule only). The rate is the greater of the two (D-084).
> Verified by: FA 2026 pp.7, 13–14, 82–83, 86–87 (re-read).

### `RATE-SUR-MR-001` — Surcharge marginal relief

`FACT (S3)`: the formula is stated four times, identically in shape:

```
W = U + V
  W = the ceiling on (income-tax + surcharge)
  U = income-tax + surcharge, if applicable, on the threshold amount in column C
  V = total income − that threshold amount
```

- TY 2025-26 old regime: `Wo = Uo + Vo`, Part I-A Para F **Table 2** — thresholds for an individual:
  ₹50,00,000 → ₹1,00,00,000 → ₹2,00,00,000 → ₹5,00,00,000 → (open-ended).
- TY 2025-26 new regime: `To = Ro + So`, FA 2026 **s.2(5)** Table — for the s.115BAC(1A) rows, the
  bands are ₹50,00,000→₹1Cr, ₹1Cr→₹2Cr, ₹2Cr→(open-ended). **No ₹5Cr band**, matching the absent 37%.
- TY 2026-27 old regime: `Wn = Un + Vn`, Part I-B Para F Table 2, same four thresholds.
- TY 2026-27 new regime: `Tn = Rn + Sn`, FA 2026 **s.3(5)** Table Sl. No. 6 (persons at Table Sl.
  Nos. 10 and 11), bands ₹50L→₹1Cr, ₹1Cr→₹2Cr, ₹2Cr→(open-ended).

**Status: VERIFIED** (S3: Part I-A Para F Table 2; s.2(5) Table; Part I-B Para F Table 2; s.3(5) Table Sl. 6).

> **Erratum (W-055, 2026-09-26):** the TY 2025-26 new-regime row is s.2(5) Table **Sl. 6** (persons at
> s.2(4) Table Sl. Nos. 10 and 11), as in s.3(5). The four statements become two pack rows, since each
> regime's two years are identical, named by the Act's words ("the total amount beyond which … shall
> not exceed"):
>
> | Concept | Pack row | Thresholds |
> |---|---|---|
> | `RATE-SUR-MR-001` (first schedule) | `RATE-SUR-FIRST-SCHEDULE-CEILING` | 50L, 1Cr, 2Cr, 5Cr |
> | `RATE-SUR-MR-001` (new regime) | `RATE-SUR-NEW-TAX-REGIME-CEILING` | 50L, 1Cr, 2Cr |
>
> Verified by: FA 2026 pp.8–9, 15, 84, 88 (re-read).

### **H-1 verdict: CONFIRMED on both limbs.**

- **Bands seeded at ₹5 Cr+ only:** wrong for **every** combination. The **entry** threshold is
  ₹50,00,000 in all four of `RATE-SUR-OLD-2025`, `RATE-SUR-NEW-2025`, `RATE-SUR-OLD-2026`,
  `RATE-SUR-NEW-2026`. ₹5,00,00,000 is only the **37%** band, and that band **does not exist in the
  new regime in either year**.
- **Marginal relief absent:** the statute states it four separate times. Omitting it produces a
  cliff at exactly ₹50,00,001 where a ₹1 rise in income raises tax by tens of thousands of rupees.

### Worked example — `RATE-SUR-MR-001`, TY 2026-27, new regime, total income ₹50,10,000, no special-rate income

```
Slab tax (RATE-SLAB-NEW-2026):
  0        – 4,00,000   Nil                                     =        0
  4,00,000 – 8,00,000   @ 5%   →  4,00,000 × 0.05               =   20,000
  8,00,000 – 12,00,000  @ 10%  →  4,00,000 × 0.10               =   40,000
  12,00,000– 16,00,000  @ 15%  →  4,00,000 × 0.15               =   60,000
  16,00,000– 20,00,000  @ 20%  →  4,00,000 × 0.20               =   80,000
  20,00,000– 24,00,000  @ 25%  →  4,00,000 × 0.25               = 1,00,000
                                  cumulative at 24,00,000       = 3,00,000
  24,00,000– 50,10,000  @ 30%  → 26,10,000 × 0.30               = 7,83,000
                                                     tax T      =10,83,000
Surcharge @10% (RATE-SUR-NEW-2026, TI > 50,00,000)              = 1,08,300
Unrelieved tax + surcharge                                      =11,91,300

RATE-SUR-MR-001,  threshold C = 50,00,000:
  R = tax + surcharge on exactly 50,00,000
      slab tax on 50,00,000 = 10,83,000 − (10,000 × 0.30)       =10,80,000
      surcharge at 50,00,000: not exceeding 50,00,000 → 0       =        0
      R                                                         =10,80,000
  S = 50,10,000 − 50,00,000                                     =   10,000
  ceiling  T = R + S                                            =10,90,000

11,91,300 > 10,90,000  →  relief = 11,91,300 − 10,90,000        = 1,01,300
tax + surcharge after relief                                    =10,90,000
Cess (RATE-CESS-001) 4% × 10,90,000                             =   43,600
Total                                                           =11,33,600
Round (RATE-ROUND-002)                                          =11,33,600
```

### `RATE-SUR-SPLIT-001` — the two-rate surcharge

`FACT (S3, Part I-A Para F Table 1 Sl. 1 clause (vi); Part I-B same; s.2(4)(b)/s.3(4)(b) Table Sl. 10
clause (v))`: where total income includes dividend income or gains under ss.111A/112/112A
(1961 Act) / ss.196/197/198 (2025 Act), **the surcharge rate on the tax attributable to that slice is
capped at 15%** while the rest of the income may bear 25% or 37%. `INFERENCE`: surcharge is therefore
**not** a single rate applied to total tax; it is at minimum a two-bucket computation. D3 sends
capital gains in from `portfolio`, so this is live for the primary user, not theoretical.

---

## R6 — Cess and rounding

| Rule ID | Statement | 1961-Act | 2025-Act | TY 2025-26 | TY 2026-27 | Status |
|---|---|---|---|---|---|---|
| `RATE-CESS-001` | **"Health and Education Cess on income-tax" at 4%** of income-tax **and surcharge**. | FA 2026 s.2(11)/(12) *(sub-section numbering for Part I-A not opened)* | FA 2026 **s.3(15)** and **s.3(16)** | 4% | 4% | VERIFIED for the 2025-Act charge (S3, s.3(15)–(16)); the parallel 1961-Act sub-section was located by the same wording in S3 but its sub-section number was not read — treat the **number** as NEEDS VERIFICATION, the **4% rate** as VERIFIED |
| `RATE-CESS-002` | Cess applies to the tax computed under s.3(1)–(5) (assessment) *and* separately to the TDS/advance-tax amounts under s.3(6)–(14), with carve-outs in s.3(17) for resident payees. | — | FA 2026 s.3(15)–(17) | — | — | VERIFIED (S3, s.3(15)–(17)) |
| `RATE-ROUND-001` | Under the 1961 Act, total income is rounded to the nearest ₹10 (s.288A) and tax payable/refundable to the nearest ₹10 (s.288B). | ss.288A, 288B — *not opened* | — | — | — | NEEDS VERIFICATION — open 1961 Act ss.288A, 288B |
| `RATE-ROUND-002` | Under the 2025 Act **one section covers both**: *"The amount of total income computed **or any amount payable or refundable** under this Act, shall be rounded off to the nearest multiple of ₹10, ignoring any part of a rupee consisting of paise"*, rounding **up** if the last figure is 5 or more and **down** if less. | — | **s.516** | — | applies | VERIFIED (S2, s.516) |

> **Erratum (W-045n, 2026-09-25):** `RATE-CESS-001`'s 1961-Act provision "FA 2026 s.2(11)/(12)" is
> wrong — the Health and Education Cess on the income-tax charged for AY 2026-27 under the 1961 Act
> is **FA 2026 s.2(6)**, "at the rate of four per cent. of such income-tax and surcharge"; its
> sub-section (7) applies "for the purposes of this section and Parts I-A and IV-A of the First
> Schedule", which places it on the 1961 side. The 4% rate stands, now `SOURCE_CHECKED` for both years.
> Verified by: S3 (egazette 2026/271439) p.9 (s.2(6)), p.2 (s.2(1)); `plans/W-045n.md` Findings Q2.

> **Erratum (W-051, 2026-09-25):** `RATE-CESS-001`'s pack ID is **`RATE-HEALTH-EDUCATION-CESS`**, the
> concept from the statute's own name, "Health and Education Cess on income-tax" (D-079). s.3(15) re-read:
> *"The amount of income-tax as specified in sub-sections (1) to (5) and as increased by the applicable
> surcharge … shall be further increased by an additional surcharge … to be called the "Health and
> Education Cess on income-tax", calculated at the rate of 4% of such income-tax and surcharge"*.
> Verified by: S3 p.29 (s.3(15)).

> **Erratum (W-050, 2026-09-25):** `RATE-ROUND-001` is **sourced**. 1961 s.288A: *"The amount of
> total income computed in accordance with the foregoing provisions of this Act shall be rounded off to
> the nearest multiple of ten rupees and for this purpose any part of a rupee consisting of paise shall
> be ignored and thereafter if such amount is not a multiple of ten, then, if the last figure in that
> amount is five or more, the amount shall be increased to the next higher amount which is a multiple
> of ten and if the last figure is less than five, the amount shall be reduced to the next lower amount
> which is a multiple of ten"*. s.288B applies the same words to *"Any amount payable, and the amount of
> refund due"*. The method is the same as s.516's. No amending Act from F(No.2)A 2019 to FA 2026 (nor
> TLA 2019/2021, TOLA 2020) amends either section; the adaptation Orders and the Jan Vishwas Act, 2023
> were not read. `SOURCE_CHECKED` for TY 2025-26. The IDs become `RATE-ROUND-TOTAL-INCOME` (ss.288A,
> 516) and `RATE-ROUND-PAYABLE` (ss.288B, 516); `RATE-ROUND-003` is a design rule, not a row (D-076).
> Verified by: https://thc.nic.in/Central%20Governmental%20Acts/Income%20Tax%20Act%201961_.pdf p.822
> (current to Act 7 of 2019); the `W-045o2` amending-Act chain.

### **H-5 second limb verdict: CONFIRMED.** There is a statutory ₹10 rounding and it is mandatory.

`RATE-ROUND-003` — **RECOMMENDATION**: round in exactly **two** places and nowhere else —
(1) total income, before the slab walk; (2) the final amount payable or refundable. Rounding each
slab band's tax, as `Math.round` per band would, is not a rounding rule the statute contains; it
introduces up to ₹3 of drift on a seven-band walk and makes golden tests un-derivable from the Act.

---

## R7 — What "salary" means, and its three different meanings

| Rule ID | Statement | 1961-Act | 2025-Act | Status |
|---|---|---|---|---|
| `SAL-CHARGE-001` | Chargeable under "Salaries": salary **due** in the tax year whether paid or not; salary **paid or allowed** though not due; **arrears** paid if not charged in an earlier year. "Employer" includes a **former** employer. | s.15 | **s.15(1), (2)** | VERIFIED for TY 2026-27 (S2, s.15); TY 2025-26 NEEDS VERIFICATION |
| `SAL-CHARGE-002` | Salary paid in advance and taxed once is not taxed again when it becomes due. | s.15 proviso | **s.15(3)** | VERIFIED (S2, s.15(3)) |
| `SAL-DEF-001` | "Salary" **includes**: wages; annuity or pension; gratuity; fees or commission; **perquisites**; profits in lieu of salary; advance of salary; leave encashment; the taxable annual accretion to an RPF balance; the taxable transferred balance; employer contribution to the s.124 pension scheme; Central Government contribution to the Agniveer Corpus Fund. | s.17(1) | **s.16** | VERIFIED (S2, s.16(a)–(l)) |
| `SAL-DEF-HRA-001` | For **HRA**: *"'salary' includes dearness allowance, **if provided for under the terms of employment**, but excludes all other allowances and perquisites."* | Rules 1962, r.2A | **Rules 2026, r.279(2)(b)** | VERIFIED for TY 2026-27 (S4, r.279(2)(b)); TY 2025-26 NEEDS VERIFICATION — open Rules 1962 r.2A |
| `SAL-DEF-GRAT-001` | For **gratuity** (s.19(1) Table Sl. 6) and **leave encashment** (Sl. 14): *"'Salary' includes dearness allowance, **if the terms of employment so provide**, but excludes all other allowances and perquisites."* | s.17(1) Expl. / r.2BA | **s.19(2)(b)** | VERIFIED (S2, s.19(2)(b)) |
| `SAL-DEF-NPS-001` | For **employer NPS** (s.124): *"'salary' includes dearness allowance, if the terms of employment so provide, but excludes all other allowances and perquisites."* | s.80CCD Expl. | **s.124(13)(b)** | VERIFIED (S2, s.124(13)(b)) |
| `SAL-DEF-80C-NPS-001` | For **employee NPS inside the ₹1.5L cap**: 10% of *"salary, including dearness allowance, if the terms of employment so provide, but excluding all other allowances and perquisites"* for an employee; 20% of **gross total income** for anyone else. | s.80CCD(1) | **Sch. XV para 1(y)** | VERIFIED (S2, Schedule XV para 1(y)) |
| `SAL-DEF-RPF-001` | For **employer RPF contribution over 12%**: "12% of the employee's salary" — the salary definition for Schedule XI Part A para 6 was **not read**. | Sch. IV Part A r.6 | Sch. XI Part A para 6 | VERIFIED as to the 12% (S2, Sch. XI Pt A para 6); the salary base for it is NEEDS VERIFICATION — open S2 Schedule XI Part A para 2 definitions |

> **Erratum (W-073c, 2026-10-08, D-151):** `SAL-DEF-RPF-001` is not unread and not a TY 2026-27 rule. The definition is
> 1961 Sch. IV Pt A **r.2(h)** (thc.nic.in p.853) and S2 Sch. XI Pt A **para 2(i)** (Gazette 265620, p.547), the same
> words: *"'salary' includes dearness allowance, if the terms of employment so provide, but excludes all other
> allowances and perquisites"*. But the Finance Act, 2026 s.127(a)(iii) removed the 12% limb from para 6 from
> 1-4-2026, so the base measures something only in **TY 2025-26** (`SAL-SALARY-FOR-RPF`). Verified by: Gazette
> CG-DL-E-31032026-271439, pp.70–71.

### **H-4 verdict: CONFIRMED — excluding DA from HRA salary is wrong.**

`FACT (S4, r.279(2)(b))`: DA **is** in the HRA salary base whenever the terms of employment provide
for it. `FACT (S2, s.19(2)(b), s.124(13)(b); Sch. XV para 1(y))`: the same "basic + DA-if-provided,
nothing else" definition recurs for gratuity, leave encashment, employer NPS and employee NPS.

`RECOMMENDATION`: there is **one** shape — `basic + (DA if dearnessAllowanceFormsPartOfRetirementBenefits)`
— used by at least five rules, plus commission-on-turnover which appears in the 1961-Act HRA
definition and which I could **not** confirm survives into r.279 (the text opened lists only DA).
Model it as a **named, rule-ID-tagged function** `salaryBaseFor(ruleId, period)`, not as a field on a
DTO. `SAL-DEF-COMM-001`: whether *"commission as a fixed percentage of turnover"* remains in the HRA
base for either year is **NEEDS VERIFICATION** — open Rules 1962 r.2A Explanation and re-read S4
r.279(2) in full.

---

## R8 — Standard deduction, professional tax, entertainment allowance

| Rule ID | Statement | 1961-Act | 2025-Act | Regime | TY 2025-26 | TY 2026-27 | Status |
|---|---|---|---|---|---|---|---|
| `SAL-STDDED-001` | Standard deduction: **₹75,000 or the salary, whichever is less**, where tax is computed under the new regime. | s.16(ia) | **s.19(1) Table Sl. 2(a)** | NEW | ₹75,000 | ₹75,000 | VERIFIED for TY 2026-27 (S2, s.19(1) Table Sl. 2(a)); TY 2025-26 NEEDS VERIFICATION — open 1961 Act s.16(ia) |
| `SAL-STDDED-002` | Standard deduction: **₹50,000 or the salary, whichever is less, in any other case**. | s.16(ia) | **s.19(1) Table Sl. 2(b)** | OLD | ₹50,000 | ₹50,000 | VERIFIED for TY 2026-27 (S2); TY 2025-26 NEEDS VERIFICATION |
| `SAL-STDDED-003` | The deduction is capped at **the salary itself**, so it cannot create or enlarge a loss under the head. | — | s.19(1) Table Sl. 2 | BOTH | applies | applies | VERIFIED (S2) |
| `SAL-PTAX-001` | **Professional tax**: *"Sum paid by the assessee as a tax on employment as per article 276(2) of the Constitution"* — **entire amount**, no cap. | s.16(iii) | **s.19(1) Table Sl. 1** | see `SAL-PTAX-002` | entire | entire | VERIFIED (S2, s.19(1) Table Sl. 1) |
| `SAL-PTAX-002` | **Professional tax is NOT allowed in the new regime**: s.202(2)(a)(iv) disallows *"section 19(1) (Table: Sl. No. 1)"*. | s.115BAC(2) | **s.202(2)(a)(iv)** | OLD only | — | — | VERIFIED for TY 2026-27 (S2, s.202(2)(a)(iv)); TY 2025-26 NEEDS VERIFICATION — open 1961 Act s.115BAC(2)(i) |
| `SAL-ENTMT-001` | **Entertainment allowance deduction has no counterpart in the 2025 Act.** `grep -i "entertainment allowance"` over the full gazette text of S2 returns **zero** hits, and the s.19(1) Table runs Sl. 1–14 with no such entry. | s.16(ii) | **absent** | — | available to Govt employees (unverified) | **not available** | VERIFIED as an absence for TY 2026-27 (S2, s.19(1) Table, full read); TY 2025-26 NEEDS VERIFICATION |

### **H-13 verdict: CONFIRMED and sharpened.**

Professional tax is deductible **in full** in the old regime and **not at all** in the new regime.
`INFERENCE`: a model that treats professional tax as a flat reduction of gross salary regardless of
regime overstates the new-regime deduction by the full professional-tax amount — typically ₹2,400 a
year, i.e. up to ₹720 of tax at 30%. Small, but it is wrong in the direction that makes the new
regime look better than it is, which is exactly the direction a regime-comparison feature must not
be wrong in.

---

## R9 — House Rent Allowance

| Rule ID | Statement | 1961-Act | 2025-Act | Regime | Status |
|---|---|---|---|---|---|
| `SAL-HRA-001` | HRA exemption = the **least of** (a) the allowance actually received for the relevant period; (b) **rent actually paid minus 10% of salary** for the relevant period; (c) the applicable **percentage of salary**. | s.10(13A) + r.2A | **Sch. III Table Sl. 11 + Rules 2026 r.279(1)** | OLD only | VERIFIED for TY 2026-27 (S2 Sch. III Sl. 11; S4 r.279(1)); TY 2025-26 NEEDS VERIFICATION — open Rules 1962 r.2A |
| `SAL-HRA-002` | Statutory conditions: the accommodation is **not owned by** the assessee, **and** rent has been **actually incurred**. | s.10(13A) provisos | **Sch. III Table Sl. 11(c), (d)** | OLD | VERIFIED (S2, Sch. III Sl. 11) |
| `SAL-HRA-003` | **"Relevant period" = the period during which the accommodation was occupied by the assessee during the tax year.** Not the whole year. | r.2A | **Rules 2026 r.279(2)(a)** | OLD | VERIFIED (S4, r.279(2)(a)) |
| `SAL-HRA-004` | Salary base: basic + DA if the terms of employment so provide, excluding all other allowances and perquisites. | r.2A | **r.279(2)(b)** | OLD | VERIFIED (S4) — see `SAL-DEF-HRA-001`, `SAL-DEF-COMM-001` |
| `SAL-HRA-005` | **HRA is not available in the new regime** — s.202(2)(a)(i) disallows Schedule III Table Sl. No. **11**. | s.115BAC(2) | **s.202(2)(a)(i)** | — | VERIFIED for TY 2026-27 (S2); TY 2025-26 NEEDS VERIFICATION |
| `SAL-HRA-006` | Landlord PAN: name, address and **PAN of the landlord(s)** must be furnished to the employer where **aggregate rent paid during the tax year exceeds ₹1,00,000**, together with the relationship with the landlord if any. | r.2BB / Form 12BB | **Rules 2026 r.205(2) Table Sl. 1, Form No. 124** | OLD | VERIFIED for TY 2026-27 (S4, r.205(2) Table Sl. 1); TY 2025-26 NEEDS VERIFICATION |

### `SAL-HRA-METRO-2026` — metro list, TY 2026-27 (Rules 2026 r.279(1)(c) Table)

| Location of residential accommodation | Percentage of salary |
|---|---|
| **Mumbai, Kolkata, Delhi, Chennai, Hyderabad, Pune, Ahmedabad and Bengaluru** | **50%** |
| Any other place | 40% |

**Status: VERIFIED** (S4, r.279(1)(c) Table Sl. 1 and 2).

### `SAL-HRA-METRO-2025` — metro list, TY 2025-26

**Status: NEEDS VERIFICATION.** What to check: **Income-tax Rules 1962, rule 2A**, clause (a) —
specifically which cities carry 50%. `FACT (S4)`: the Rules 2026 list is eight cities.
`FACT (EY technical alert, *Key changes under the Income-tax Rules, 2026*, opened this session)`: the
Rules 2026 **"expand the list of cities eligible for higher House Rent Allowance (HRA) exemption"** —
i.e. the TY 2025-26 list is **shorter**. `INFERENCE`: the four added cities are Hyderabad, Pune,
Ahmedabad and Bengaluru, leaving Mumbai/Kolkata/Delhi/Chennai at 50% for TY 2025-26 — but this is an
inference from a secondary professional source and **must not be shipped as a rule value**. Open
Rules 1962 r.2A before seeding TY 2025-26.

### **H-2 verdict on the metro list: CONFIRMED.**

The eight-city list is a **Rules 2026** value. Labelling it TY 2025-26 applies a TY 2026-27 rule to a
1961-Act year. For a Bengaluru-based employee this inflates limb (c) of `SAL-HRA-001` from 40% to
50% of salary — a direct, material overstatement of the exemption for the single largest user
segment this product will have.

### Worked example — `SAL-HRA-001`, TY 2026-27, Bengaluru, old regime

Basic ₹12,00,000; DA ₹1,20,000 (terms of employment provide for it); HRA received ₹6,00,000; rent
paid ₹30,000/month for the whole year; accommodation occupied all 12 months.

```
Salary base (SAL-HRA-004) = 12,00,000 + 1,20,000                = 13,20,000
Rent paid for the relevant period = 30,000 × 12                 =  3,60,000

limb (a)  allowance actually received                           =  6,00,000
limb (b)  rent − 10% of salary = 3,60,000 − 1,32,000            =  2,28,000
limb (c)  Bengaluru is in SAL-HRA-METRO-2026 → 50% × 13,20,000  =  6,60,000

exemption = least of the three                                  =  2,28,000
taxable HRA = 6,00,000 − 2,28,000                               =  3,72,000
```

Landlord PAN required (`SAL-HRA-006`): rent ₹3,60,000 > ₹1,00,000 → **yes**.

The same figures under `SAL-HRA-METRO-2025`'s *unverified* four-city list would leave limb (c) at
40% × 13,20,000 = ₹5,28,000 — still above limb (b), so the exemption is **unchanged at ₹2,28,000**.
`INFERENCE`: limb (c) binds only when rent is high relative to salary. That is exactly the case for a
high-rent metro tenant, which is why the list still matters — see `T3`.

---

## R10 — Leave Travel Concession / Assistance

| Rule ID | Statement | 1961-Act | 2025-Act | Regime | Status |
|---|---|---|---|---|---|
| `SAL-LTA-001` | The **value of any travel concession or assistance** received from the employer for the assessee and family, in connection with proceeding on leave **to any place in India**, or after retirement/termination, is excluded. | s.10(5) | **Sch. III Table Sl. 8(a)** | OLD only | VERIFIED (S2, Sch. III Sl. 8) |
| `SAL-LTA-002` | The exclusion *"shall in no case exceed the amount of expenses **actually incurred** for the purpose of such travel"*. | s.10(5) proviso | **Sch. III Table Sl. 8(d)** | OLD | VERIFIED (S2, Sch. III Sl. 8(d)) |
| `SAL-LTA-003` | Air: economy fare of the national carrier by the shortest route, or actual expenditure, whichever is less. *(The air limb was not in the extracted text window; see status.)* | r.2B(1) | Rules 2026 **r.278(1)(i)** | OLD | NEEDS VERIFICATION — re-read S4 r.278(1)(i) in full |
| `SAL-LTA-004` | Rail-connected, travelled otherwise than by air: **not exceeding air-conditioned first-class rail fare** by the shortest route. | r.2B(1) | **r.278(1)(ii)** | OLD | VERIFIED (S4, r.278(1)(ii)) |
| `SAL-LTA-005` | Not rail-connected: with a recognised public transport system, first-class or deluxe fare by the shortest route; **without** one, **₹30 per kilometre** by the shortest route. | r.2B(1) | **r.278(1)(iii)(A), (B)** | OLD | VERIFIED (S4, r.278(1)(iii)) |
| `SAL-LTA-BLOCK-001` | **Two journeys in a block of four calendar years, the block commencing with calendar year 2022** — so the blocks are **2022-2025**, **2026-2029**, and so on. Note: **calendar** years, not tax years. | r.2B(2) | **r.278(2)** | OLD | VERIFIED (S4, r.278(2)) |
| `SAL-LTA-CARRY-001` | **Carry-over**: if the concession is not availed during a block, the value **first availed in the first calendar year of the immediately succeeding block** is eligible, and that carried-over journey **is not counted** against the two-journey limit of the new block. | r.2B(3),(4) | **r.278(3), (4)** | OLD | VERIFIED (S4, r.278(3),(4)) |
| `SAL-LTA-CHILD-001` | Not available for **more than two surviving children**; the restriction does not apply to children born **before 1 October 1998**, nor to **multiple births after one child**. | r.2B(5),(6) | **r.278(5), (6)** | OLD | VERIFIED (S4, r.278(5),(6)) |
| `SAL-LTA-NEW-001` | **LTA is not available in the new regime** — s.202(2)(a)(i) disallows Schedule III Table Sl. No. **8**. | s.115BAC(2) | **s.202(2)(a)(i)** | — | VERIFIED for TY 2026-27 (S2) |


> **Erratum (W-068, 2026-09-27, D-100):** `SAL-LTA-003`'s statement is the **Rules 1962 r.2B(1)(i)** wording — *"air
> economy fare of the national carrier by the shortest route"* (as substituted by the IT (First Amdt.) Rules,
> 1998). For TY 2026-27, **S4 r.278(1)(i)** reads: *"where the journey is performed by air, the amount shall not
> exceed the fare admissible for the class of travel to which the employee is entitled, by the shortest route to
> the place of destination"* (p.1776). The two years also differ in (iii)(B): r.2B, *"an amount equivalent to the
> air-conditioned first class rail fare, for the distance of the journey by the shortest route, as if the journey
> had been performed by rail"*; r.278, *"₹30 per kilometre"*.
>
> Verified by: S4 p.1776 read in full; DoR compendium pp.348–349.

### **H-8 verdict: CONFIRMED — exempting LTA in full is wrong, on four independent grounds.**

(1) capped at actual travel expenditure (`SAL-LTA-002`); (2) capped at a fare class by the shortest
route (`SAL-LTA-004`, `SAL-LTA-005`); (3) capped at two journeys per four-calendar-year block
(`SAL-LTA-BLOCK-001`); (4) unavailable entirely in the new regime (`SAL-LTA-NEW-001`).
`INFERENCE`: a correct LTA rule needs **cross-year state** — which journeys were claimed in which
calendar years of which block. That is a data-model requirement (`M3`), not a calculator detail.

---

## R11 — Special allowances

| Rule ID | Statement | 2025-Act | Regime | Status |
|---|---|---|---|---|
| `SAL-ALW-DUTY-001` | Allowances granted to meet expenses **wholly, necessarily and exclusively incurred in the performance of duties**, exempt **to the extent actually incurred**, provided they are not perquisites. | **Sch. III Table Sl. 12** | see `SAL-ALW-NEW-001` | VERIFIED (S2, Sch. III Sl. 12) |
| `SAL-ALW-PERS-001` | Allowances to meet **personal expenses** at the place of duty or residence, or to compensate for **increased cost of living**, exempt **to the extent prescribed**. | **Sch. III Table Sl. 13** | see `SAL-ALW-NEW-001` | VERIFIED (S2, Sch. III Sl. 13) |
| `SAL-ALW-DUTY-LIST-2026` | The Sl.-12 allowances are, exhaustively: travel on tour or transfer; packing and transportation of personal effects on transfer; **daily allowance** on tour or journey on transfer; **conveyance** in performance of duties where no free conveyance is provided; **helper**; academic/research/training pursuits; **uniform** purchase or maintenance. | **Rules 2026 r.280(1)(a)–(g)** | | VERIFIED (S4, r.280(1)) |
| `SAL-ALW-NEW-001` | In the new regime, Sch. III Sl. 12 and Sl. 13 are disallowed **"other than those as may be prescribed for this purpose"** — so a prescribed subset survives. | **s.202(2)(a)(ii)** | NEW | VERIFIED (S2, s.202(2)(a)(ii)) |
| `SAL-ALW-NEW-SUBSET-001` | Rules 2026 r.280(3) names the surviving subset as r.280(1)**(a) to (d)** (tour/transfer travel, packing and transport on transfer, daily allowance, conveyance) **and** r.280(2) Table **Sl. No. 10** (transport allowance for a blind / deaf-and-dumb / orthopaedically handicapped employee). | **Rules 2026 r.280(3)** | NEW | **STATUS UNCLEAR** — see the defect note below |

### `SAL-ALW-LIMITS-2026` — prescribed limits, TY 2026-27 (Rules 2026 r.280(2) Table)

| Sl. | Allowance | Place | Extent exempt | Rule ID |
|---|---|---|---|---|
| 6 | Transport-system employee's personal expenditure during running duty (where no daily allowance is received) | Whole of India | **70% of the allowance, max ₹25,000 per month** | `SAL-ALW-TRANSPORTSYS-2026` |
| 7 | **Children Education Allowance** | Whole of India | **₹3,000 per month per child, max two children** | `SAL-ALW-CEA-2026` |
| 8 | Allowance to meet **hostel expenditure** on a child | Whole of India | **₹9,000 per month per child, max two children** | `SAL-ALW-HOSTEL-2026` |
| 10 | **Transport allowance** for an employee who is blind, or deaf and dumb, or orthopaedically handicapped with disability of lower or upper extremities, for commuting between residence and place of duty | Whole of India | **Metro cities: ₹15,000 + dearness allowance thereon per month; Other cities: ₹8,000 + dearness allowance thereon per month** | `SAL-ALW-TRANSPORT-DIS-2026` |
| 9, 11–15 | Counter-insurgency ₹22,000/month; high-altitude ₹4,500 / ₹7,000 / ₹30,000 per month by area; underground mines 15% of basic pay; highly-active-field-area ₹22,000/month; island duty; Siachen ₹42,500/month | various | as tabulated | `SAL-ALW-ARMED-2026` (defence-only; out of MVP scope, in the rule pack for completeness) |

**Status: VERIFIED** (S4, r.280(2) Table Sl. 6–15, read directly).

### `SAL-ALW-LIMITS-2025` — prescribed limits, TY 2025-26

**Status: NEEDS VERIFICATION** for **every** row. What to check: **Income-tax Rules 1962, rule 2BB(2)
Table**. `FACT (EY alert, opened this session)`: Rules 2026 effect an *"enhancement of exemption
limits for specified allowances"* — so the TY 2025-26 figures are **lower**, not the same.


> **Erratum (W-069, 2026-09-27, D-101):** the DoR compendium's r.2BB (current to 2010–11) is **not** a sufficient
> source for `SAL-ALW-LIMITS-2025`. Its amounts carry amendment footnotes only to 2000–2010, and r.2BB was amended
> afterwards (1961 s.115BAC(2)(i) refers to allowances *"prescribed under clause (14) (other than those as may be
> prescribed for this purpose)"*). The TY 2025-26 figures must be read in r.2BB as in force for AY 2026-27. For
> `SAL-ALW-NEW-SUBSET-001`, the build seeds r.280(3)'s list into the **new** regime, per s.202(2)(a)(ii), and marks
> that version `UNVERIFIED` pending the reading this section asks for.
>
> Verified by: S4 r.280(3) (p.1783) verbatim; DoR r.2BB footnotes.

### **H-2 verdict on ₹3,000 / ₹9,000: CONFIRMED.** These are **Rules 2026** figures (S4, r.280(2) Sl. 7, 8).

### **H-10 verdict: CONFIRMED, and the error is larger than stated.**

`FACT (S4, r.280(2) Table Sl. 10)`: the only transport allowance in the Rules-2026 table is the one
for a **blind / deaf-and-dumb / orthopaedically handicapped** employee. There is **no general
transport allowance** for an able-bodied employee in either the Sl.-12 list (`SAL-ALW-DUTY-LIST-2026`)
or the Sl.-13 table. So ₹19,200 for everyone is wrong twice over: it grants an exemption to people
who have none, and where the exemption *does* exist the amount is ₹15,000 or ₹8,000 **per month**
plus DA, not ₹1,600 per month.

### **H-9 verdict: CONFIRMED.**

`FACT (S2, Sch. III Sl. 12; S4, r.280(1))`: **uniform** is an Sl.-12 allowance, exempt only *"to the
extent to which such expenses are **actually incurred** for that purpose"* and only where granted to
meet expenses *"wholly, necessarily and exclusively incurred in the performance of the duties"*.
It is **not** a 100% exemption of whatever the employer labels "uniform allowance".
**Books and phone**: neither appears in `SAL-ALW-DUTY-LIST-2026` nor in the r.280(2) Table.
`SAL-ALW-BOOKS-001`, `SAL-ALW-PHONE-001` — **telephone reimbursement is a *perquisite* question, not
an allowance question**; see `PERQ-PHONE-001`. A "books and periodicals allowance" has no entry in
either list opened; treating it as exempt at 100% has no basis in the text read.
`NEEDS VERIFICATION`: whether any CBDT circular or judicial gloss supports a books-and-periodicals
exemption under the Sl.-12 "wholly, necessarily and exclusively" limb.

### The r.280(3) defect — `SAL-ALW-NEW-SUBSET-001`

`FACT (S2, s.202(1) and (4))`: exercising the option under s.202(4) means s.202(1) — the **new**
regime — **does not apply**. The option is the **opt-out**.
`FACT (S2, s.202(2)(a)(ii))`: the restriction on Schedule III Sl. 12 and 13 attaches to computing
under s.202(1), i.e. to the **new** regime.
`FACT (S4, r.280(3))`: *"An employee, being an assessee, **who has exercised an option under section
202(4)** shall be entitled to exemption only in respect of allowances mentioned in sub-rule (1)(a) to
(d) and in sub-rule (2) [Table: Sl. No. 10]."*

`INFERENCE`: as notified, r.280(3) attaches the restriction to the person who opted **out** — the
**old**-regime taxpayer — which inverts s.202(2)(a)(ii). One of the two must give way, and a rule
cannot override the section it is made under.

`FACT (S5)`: corrigendum G.S.R. 286(E) of 16-04-2026 lists 40+ corrections to G.S.R. 198(E) and
**none of them touches rule 279 or rule 280**. `FACT (S6)`: the Fourth Amendment Rules of 17-09-2026
amend rules 160, 176, 225, 246, 256 and Forms 169 and 171 — **not** rule 280.

**Status: STATUS UNCLEAR.** What to check: the amendment chain between
G.S.R. 198(E) and G.S.R. 656(E) of 24-07-2026, which was not opened; and any CBDT clarification.
**Design instruction (`RECOMMENDATION`)**: seed the rule pack from **s.202(2)(a)(ii)** — restrict in
the **new** regime — and record `SAL-ALW-NEW-SUBSET-001` as a **known open item with a visible
provenance flag**, so that if a clarification lands the change is one data row.

---

## R12 — Perquisites

| Rule ID | Statement | 1961-Act | 2025-Act | Status |
|---|---|---|---|---|
| `PERQ-DEF-001` | "Perquisite" **includes**: rent-free accommodation; concessional accommodation; benefit/amenity free or concessional to a director, a substantial-interest employee, or an employee whose **monetary salary exceeds a prescribed amount**; **specified security or sweat equity** (ESOP/RSU); any other prescribed benefit; employer discharge of the employee's obligation; life-assurance/annuity premia other than to RPF, approved superannuation or the listed DLI funds; the aggregate employer contribution **over ₹7,50,000**; and the **annual accretion** on that excess. | s.17(2) | **s.17(1)(a)–(i)** | VERIFIED (S2, s.17(1)) |
| `PERQ-SPECEMP-001` | The "specified employee" monetary-salary threshold is **"such amount as may be prescribed"**. It was ₹50,000 in the 1961 Act until FA 2025 s.9(a) replaced the figure with a prescription, **with effect from 1 April 2026 — i.e. from TY 2025-26**. | s.17(2)(iii)(c) as amended by FA 2025 s.9(a) | s.17(1)(c)(ii) | VERIFIED that it became prescribed (S7, FA 2025 s.9(a)); **the prescribed amount for either year is NEEDS VERIFICATION** — open Rules 1962 (the 2025 amending notification) and re-read S4 for the rule prescribing it |
| `PERQ-MEDABROAD-001` | Medical treatment abroad is excluded only to the extent RBI permits, and **travel** is excluded only where the employee's gross total income before including it does not exceed **"such amount as may be prescribed"** (₹2,00,000 until FA 2025 s.9(b) made it a prescription, w.e.f. 1 April 2026). | s.17(2) proviso (vi)(B) as amended by FA 2025 s.9(b) | **s.17(3)(b)** | VERIFIED that it became prescribed (S7, FA 2025 s.9(b)); the amount is NEEDS VERIFICATION |
| `PERQ-EXCL-001` | Not perquisites: employer-maintained hospital treatment; treatment in a Government/approved hospital; treatment of prescribed diseases in an approved hospital; employer-paid group health premium under an approved scheme; employer reimbursement of the employee's own health premium under a s.126-approved scheme; **employer expenditure on the employee's commute between residence and office**; and prescribed medical travel abroad. | s.17(2) provisos | **s.17(2)(a)–(f)** | VERIFIED (S2, s.17(2)) |
| `PERQ-ESOP-001` | The value of a specified security or sweat equity share is its **fair market value on the date the option is exercised**, less the amount actually paid by or recovered from the assessee. "Option" means a right but not an obligation to apply at a predetermined price. | s.17(2)(vi) | **s.17(4)(e), (h)** | VERIFIED (S2, s.17(4)) |
| `PERQ-ESOP-002` | An **eligible start-up** under s.140 may **defer** deduction or payment of tax on s.17(1)(d) income to the timing in s.289(3). | s.192(1C) | **s.392(3)** | VERIFIED (S2, s.392(3)) |
| `PERQ-TAXPAID-001` | Where the employer **pays the tax on a non-monetary perquisite** at its own option, that tax is **not included** in the employee's income. | s.10(10CC) | **Sch. III Table Sl. 10** | VERIFIED (S2, Sch. III Sl. 10) |

> **Addendum (W-071b, 2026-09-28, D-112):** both prescribed amounts are now read, and are the same in both years.
> `PERQ-SPECEMP-001`: TY 2026-27 S4 r.17, *"₹ 400000"* (p.1549); TY 2025-26 Rules 1962 **r.3C**, *"four lakh
> rupees"*. `PERQ-MEDABROAD-001`: TY 2026-27 S4 r.19, *"₹ 800000"* (p.1550); TY 2025-26 Rules 1962 **r.3D**,
> *"eight lakh rupees"*. r.3C and r.3D were inserted by the Income-tax (Twenty Second Amendment) Rules, 2025,
> G.S.R. 555(E) of 18-8-2025, in force on publication, before AY 2026-27 began.
>
> Verified by: Gazette CG-DL-E-18082025-265477 p.2; Gazette CG-DL-E-20032026-271092 pp.1549–1550;
> `docs/taxplanning-build/sources/2026-09-28-perquisite-thresholds.md`.

### `PERQ-CAR-2026` — motor car, TY 2026-27 (Rules 2026 r. *(perquisite valuation rule)* Table II, **per calendar month**)

| Circumstance | ≤ 1.6 litres **or electric vehicle** | > 1.6 litres |
|---|---|---|
| Employer-owned/hired, used **wholly and exclusively** for official duties | **Nil**, if the prescribed documents are maintained | **Nil**, same condition |
| Employer-owned/hired, used **exclusively** for private purposes, employer meets running and maintenance | actual running & maintenance **+ chauffeur remuneration + 10% p.a. normal wear and tear − amount charged to the employee** | same |
| Employer-owned/hired, **partly official partly private**, employer meets running and maintenance | **₹5,000** (+ **₹3,000** if a chauffeur is also provided) | **₹7,000** (+ **₹3,000** chauffeur) |
| Employer-owned/hired, **partly official partly private**, employee meets private running costs | **₹2,000** (+ **₹3,000** chauffeur) | **₹3,000** (+ **₹3,000** chauffeur) |
| **Employee-owned** car, employer reimburses, wholly official | Nil, if documents maintained | Nil |
| **Employee-owned** car, employer reimburses, partly private | actual employer expenditure **less** the partly-official figure above (₹5,000 / ₹7,000) | same |
| Employee-owned **other automotive conveyance**, employer reimburses, partly private | actual employer expenditure **less ₹3,000** | n/a |

`PERQ-CAR-WEARTEAR-001`: normal wear and tear = **10% per annum of the actual cost** of the car.
`PERQ-CAR-MULTI-001`: where more than one car is made available for non-exclusively-official use,
**one** car is valued at the partly-official figure and **every other** car at the exclusively-private
figure (actual cost basis).
`PERQ-CAR-HIGHER-001`: a higher official-use claim is allowed only on complete journey records plus
an employer certificate.

**Status: VERIFIED** (S4, Table II and its sub-rules, read directly). **The rule number was not
captured** — `NEEDS VERIFICATION` for the citation only, not the values; re-read S4 around the
Table I / Table II / Table III block to record it.

### `PERQ-CAR-2025` — motor car, TY 2025-26
**Status: NEEDS VERIFICATION.** Open **Income-tax Rules 1962, rule 3(2) Table**.

> **Addendum (W-071c, 2026-09-28, D-113):** r.3(2) is now read — in the Income Tax Department's consolidated rule 3
> (no "as amended up to" date; amendment footnotes to 2023, none on r.3(2)), not in the Gazette. Table II, per
> calendar month: Sl. (1)(c)(i) **Rs. 1,800 / Rs. 2,400**, (1)(c)(ii) **Rs. 600 / Rs. 900**, each **plus Rs. 900**
> for a chauffeur; Sl. (3)(ii) the expenditure less **Rs. 900**; wear and tear 10% a year of cost; the several-cars
> proviso and clause (B) as in 2026. **No electric-vehicle column.** Built as `UNVERIFIED` (`PERQ-CAR-*`). The
> TY 2026-27 rule number is **r.15(3)** (Gazette 271092 pp.1542–1543), which the `PERQ-CAR-2026` note asked for.
> Whom the car is a perquisite for is not in either Rule: OQ-10.
>
> Verified by: `docs/taxplanning-build/sources/2026-09-28-perquisite-thresholds.md` V1–V2.

### `PERQ-OTHER-2026` — food, gifts, credit card, club, movable assets (Rules 2026, Table III)

| Rule ID | Item | Value |
|---|---|---|
| `PERQ-MEAL-2026` | Free food and non-alcoholic beverages | Employer's expenditure less any recovery — **but nil** for (a) food and non-alcoholic beverages provided **during working hours at office/business premises or through paid vouchers usable only at eating joints, to the extent the value does not exceed ₹200 per meal**; (b) tea or snacks during working hours; (c) food during working hours in a remote area or off-shore installation |
| `PERQ-GIFT-2026` | Gift, voucher or token in lieu of gift, on ceremonial occasions or otherwise | The amount of the gift — **but nil if the value is *below* ₹15,000 in aggregate during the tax year** |
| `PERQ-CREDITCARD-2026` | Employer-provided credit-card expenses including membership and annual fees | The amount, less recovery — **nil** where wholly and exclusively for official purposes and the prescribed conditions are met |
| `PERQ-CLUB-2026` | Club expenditure including annual/periodical fees | Actual expenditure less recovery; **corporate-membership initial fee excluded**; nil where wholly and exclusively for business purposes, conditions met, and health-club/sports facilities are provided **uniformly to all employees** |
| `PERQ-MOVABLE-2026` | Use of a movable asset (other than the listed exclusions) | **10% per annum of actual cost** |
| `PERQ-SERVANT-2026` | Sweeper, gardener, watchman, personal attendant | Total salary paid by the employer, less recovery |
| `PERQ-UTIL-2026` | Gas, electricity, water | Amount paid to the outside agency, less recovery; if from the employer's own resources, **manufacturing cost per unit** |
| `PERQ-EDU-2026` | Free or concessional education for a household member | Employer's expenditure less recovery. Where the institution is **maintained and owned by the employer**, or education is free in another institution by reason of employment: cost in a similar institution nearby, less recovery, **where the cost per child exceeds ₹3,000 per month** |

**Status: VERIFIED** (S4, Table III Sl. 1–7, read directly). Rule number `NEEDS VERIFICATION`.

> **Addendum (W-071e1, 2026-09-28, D-117):** meals and gifts are built (`PERQ-MEAL`, `-AT-WORK`, `-VOUCHER`,
> `PERQ-GIFT`), both years. TY 2025-26 (Rules 1962 r.3(7)(iii)–(iv), consolidated rule 3): **fifty rupees** per meal;
> gifts nil **below five thousand rupees**; paid vouchers keep no limit in the **new regime** (second proviso, G.S.R.
> 452(E), read in the Gazette). TY 2026-27 has no voucher bar — S4 and S2 searched. The Table IV rows sit on
> **r.15(5)** (Gazette 271092, pp.1545–1546), the rule number this section asked for. Gifts are aggregated per
> employer (D-117 C4a).

> **Addendum (W-071e3, 2026-09-28, D-119):** the rest of Table IV is built — holidays, credit cards, clubs, the use
> (10% a year, apportioned to months) and the transfer (50% / 20% reducing balance, 10% straight line per completed
> year) of movable assets, other benefits at arm's-length cost, telephones excepted. Excluded from use: laptops and
> computers (TY 2025-26); laptops, computers, **tablets and mobile phones** (TY 2026-27). **Clubs differ by year:**
> Rules 1962 r.3(7)(vi) has two separate exclusions (business with records; uniform health-club facilities); S4 Table IV
> Sl. 6 joins them into one — built as each text reads, OQ-12.

> **Addendum (W-071d, 2026-09-28, D-120):** Table III is **S4 r.15(4)** (Gazette 271092, p.1544) and is built —
> services, utilities, education, carriers' journeys. TY 2025-26 (Rules 1962 r.3(3)–(6)): the education limit is
> **one thousand rupees** per child per month (r.3(5) proviso), against ₹3,000; an airline's or the railways' employee's
> journey is outside both texts and refused. Unlike Table IV, Table III names no limb of s.17: what the employer
> provides is a benefit or amenity — a specified employee's perquisite — and what meets the employee's obligation is
> everyone's (OQ-10, widened).

> **Addendum (W-071f, 2026-09-28, D-121):** `PERQ-ESOP-001` is built as `PERQ-SPECIFIED-SECURITY`, both years, for
> every employee and a former employer's allotment too: the fair market value on the exercise date — S4 **r.15(6)**
> (p.1547) and **(7)** (p.1548): the average of the day's opening and closing prices; the closest preceding close where
> untraded; a merchant banker's value on a specified date no more than **180 days** before exercise (**r.15(8)(l)**,
> p.1548; Rules 1962 r.3(8)–(9)) — less the price paid. `PERQ-ESOP-002`, the eligible start-up's deferral (s.392(3)),
> changes when the tax is paid, not the income or its year: not modelled in the income computation.

### `PERQ-ACCOM-2026` — accommodation (Rules 2026, Table I and its sub-rules)

| Rule ID | Statement |
|---|---|
| `PERQ-ACCOM-LEASE-2026` | Accommodation **taken on lease or rent** by the employer: **lower of** actual lease rental **or 10% of salary** for the period of occupation, less rent actually paid by the employee |
| `PERQ-ACCOM-HOTEL-2026` | **Hotel** accommodation: **lower of** actual charges **or 24% of salary** for the period provided, less rent paid by the employee — **except** where provided for not more than **15 days in aggregate on transfer** |
| `PERQ-ACCOM-CAP-2026` | Where the **same** accommodation is provided to the **same** employee for more than one tax year, the Table I Sl. 2(a)/(b) amount **shall not exceed** the first-year amount scaled by the ratio of the **Cost Inflation Index** for the current tax year to that of the year the accommodation was first provided. "First tax year" = **2023-24** or the year first provided, whichever is later |
| `PERQ-ACCOM-FURN-2026` | Furnished: add **10% per annum of the cost of furniture** (or actual hire charges if hired), less employee recovery |
| `PERQ-ACCOM-TRANSFER-2026` | On transfer with two accommodations retained: value **only the lower-valued one** for up to **90 days**, then both |
| `PERQ-ACCOM-SITE-2026` | Excluded: temporary accommodation at a mining / on-shore oil / project / dam / power-generation / off-shore site, with plinth area ≤ 1,000 sq ft located ≥ 8 km outside municipal limits, or in a remote area |

**Status: VERIFIED** (S4, Table I and sub-rules (2)(b)–(g)). The **employer-owned** accommodation
percentages (Table I Sl. 1 and 2(a)) were **not in the extracted window** — `PERQ-ACCOM-OWNED-2026`
is **NEEDS VERIFICATION**; re-read S4 Table I Sl. 1 and 2(a).

> **Addendum (W-071a2, 2026-09-28, D-115):** `PERQ-ACCOM-CAP-2026` is built as `PERQ-ACCOM-CONTINUED`, both years,
> for owned **and leased** accommodation (TY 2025-26 by corrigendum G.S.R. 636(E)), on the Cost Inflation Index
> read in the Gazette: 2023-24 **348** (S.O. 2571(E)), 2024-25 **363** (S.O. 2103(E)), 2025-26 **376** (S.O. 2954(E)),
> 2026-27 **384** (S.O. 3889(E), 2025 Act s.72(8)(a); the earlier years carried by s.536(2)(j)). The rule scales the
> *first tax year's* amount but divides by the index for the year the accommodation was *initially provided*; the two
> differ only for accommodation first provided before 2023-24, which stays refused (OQ-11). The Sl. amount capped is
> after rent; furniture is outside the cap.
>
> Verified by: `docs/taxplanning-build/sources/2026-09-28-perquisite-thresholds.md` T4–T6, U1–U3; S4 r.15(2)(d), (g).

### `PERQ-LOAN-2026`, `PERQ-PHONE-2026`

> **Addendum (W-071e2, 2026-09-28, D-118):** `PERQ-LOAN-2026` is now read and built (`PERQ-LOAN`,
> `PERQ-LOAN-PETTY`) — S4 **r.15(5) Table IV Sl. 1** (Gazette 271092, p.1546): interest at the State Bank of India's
> rate for the same purpose on the first day of the tax year, on the "maximum outstanding monthly balance" (each
> month-end balance, r.15(8)(e)), less interest paid; nil for loans for rule 18 diseases (save what medical insurance
> reimbursed) or where the loans do not exceed **₹2,00,000** in aggregate. TY 2025-26: Rules 1962 r.3(7)(i) — rule 3A
> and **twenty thousand rupees** (consolidated rule 3). The Bank's rate is entered, not seeded; one month's interest
> per balance and the petty test on amounts lent per employer are readings (D-118 C2a, C3a). `PERQ-PHONE-2026`:
> telephones are excluded from Table IV Sl. 9 and r.3(7)(ix) in both years — W-071e3.

**NEEDS VERIFICATION** — interest-free/concessional loan valuation and telephone treatment were not
located in the extracted text. Re-read S4's Table III in full and the perquisite rule's remaining
sub-rules. `FACT (S2, s.17(2)(e))`: employer expenditure on the employee's **commute** between
residence and office is expressly **not** a perquisite, which is a different rule from telephone.

### **H-2 verdict on ₹200 meal, ₹15,000 gift, car ₹5k/8k/7k/10k, driver ₹3,000: CONFIRMED on all four.**

Each is a **Rules 2026** value, read this session from S4. The "₹5k/8k/7k/10k" pattern is the
partly-official row of `PERQ-CAR-2026` with and without a chauffeur (₹5,000 and ₹5,000+₹3,000;
₹7,000 and ₹7,000+₹3,000) — i.e. the numbers are individually right **for TY 2026-27** and wrong for
TY 2025-26, which is exactly the failure mode H-2 describes.

`PERQ-GIFT-BOUNDARY-001` — **FACT (S4)**: the gift exemption reads *"nil if the value ... is **below**
₹15,000"*. At exactly ₹15,000 the **whole** amount is a perquisite, not the excess. An
`amount > 15000 ? amount - 15000 : 0` implementation is wrong in both directions at the boundary.
See `T9`.

---

## R13 — Employer PF, NPS, superannuation and the aggregate cap

| Rule ID | Statement | 1961-Act | 2025-Act | TY 2025-26 | TY 2026-27 | Status |
|---|---|---|---|---|---|---|
| `PF-AGGCAP-001` | **Aggregate cap.** The employer's contribution **in excess of ₹7,50,000 in a tax year**, taken across (i) a **recognised provident fund**, (ii) the **s.124(1) pension scheme (NPS)** and (iii) an **approved superannuation fund**, is a **perquisite**. | s.17(2)(vii) | **s.17(1)(h)** | ₹7,50,000 | ₹7,50,000 | VERIFIED for TY 2026-27 (S2, s.17(1)(h)); TY 2025-26 NEEDS VERIFICATION — open 1961 Act s.17(2)(vii) |
| `PF-AGGCAP-002` | The **annual accretion by way of interest, dividend or any other amount of a similar nature** on the balance, **to the extent it relates to the excess contribution**, is **also** a perquisite, computed as prescribed. | s.17(2)(viia) + r.3B | **s.17(1)(i)** | applies | applies | VERIFIED for TY 2026-27 (S2, s.17(1)(i)); the prescribed formula is NEEDS VERIFICATION in **both** years — open Rules 1962 r.3B and the corresponding Rules 2026 rule |
| `PF-RPF12-001` | Separately and additionally: the portion of the annual accretion consisting of **employer contributions exceeding 12% of the employee's salary**, and **interest credited at a rate exceeding the rate notified by the Central Government**, is deemed received by the employee and taxable. | Sch. IV Pt A r.6 | **Sch. XI Part A para 6** | applies | applies | VERIFIED for TY 2026-27 (S2, Sch. XI Pt A para 6); the **notified interest rate** is NEEDS VERIFICATION |
| `PF-EEINT-001` | Interest accrued on the **employee's own** contributions made **on or after 1 April 2021** is **not** exempt where those contributions exceed **₹5,00,000** in a tax year in a fund to which the employer makes no contribution, or **₹2,50,000** in any other case. Applies to both statutory/PPF-type funds (Sch. II Sl. 3) and recognised provident funds (Sch. II Sl. 4). | s.10(11), s.10(12) provisos + r.9D | **Sch. II Table Sl. 3 and Sl. 4** | ₹5,00,000 / ₹2,50,000 | ₹5,00,000 / ₹2,50,000 | VERIFIED for TY 2026-27 (S2, Sch. II Sl. 3(a), Sl. 4(a)); TY 2025-26 NEEDS VERIFICATION. The computation manner is prescribed — NEEDS VERIFICATION in both years |
| `PF-EEDED-001` | The employee's own RPF contribution is deductible **as determined under s.123** — i.e. inside the ₹1,50,000 cap, not outside it. | s.80C(2)(vi) | **Sch. XI Part A para 7**, r/w **Sch. XV para 1(f)** | ₹1.5L cap | ₹1.5L cap | VERIFIED (S2, Sch. XI Pt A para 7; Sch. XV para 1(f)) |
| `PF-WITHDRAW-001` | The accumulated balance is excluded where the employee rendered **five years or more** of continuous service, or service ended through ill-health, closure or a cause beyond the employee's control, or the balance is transferred to the new employer's fund. | Sch. IV Pt A r.8 | **Sch. XI Part A para 8** | applies | applies | VERIFIED (S2, Sch. XI Pt A para 8) |
| `PF-WITHDRAW-TDS-001` | Where para 8 does **not** apply, EPF trustees deduct tax at **10%** on payment of the accumulated balance where the aggregate is **₹50,000 or more**. | s.192A | **s.392(7)** | 10% / ₹50,000 | 10% / ₹50,000 | VERIFIED for TY 2026-27 (S2, s.392(7)) |

> **Erratum (W-073c, 2026-10-08, D-151):** `PF-RPF12-001` is stated as applying in TY 2026-27 under Sch. XI Part A
> para 6 with both limbs — true of the Act as enacted (Gazette 265620, p.550), not as in force: the Finance Act, 2026
> s.127(a)(iii) substituted para 6 from 1-4-2026 with only *"interest credited on the balance … at a rate exceeding
> such rate as fixed by the Central Government by notification"*. The **12% contribution limb is TY 2025-26 only**
> (1961 Sch. IV Pt A r.6(a)); the interest limb applies in both years. Verified by: Gazette CG-DL-E-31032026-271439,
> pp.70–71.

> **Addendum (W-073a, 2026-09-28, D-126):** `PF-AGGCAP-001`'s TY 2025-26 figure is now read in the Gazette —
> 1961 s.17(2)(vii) as substituted by the **Finance Act, 2020 s.13** (CG-DL-E-27032020-218938, p.21), "seven lakh and
> fifty thousand rupees", with the scheme of s.80CCD(1) — and built as `PERQ-EMPLOYER-CONTRIBUTION` (both years,
> across employments, strict). `PF-AGGCAP-002` (W-073b) waits on Rules 1962 r.3B; S4's formula is **r.16**
> (pp.1548–1549), whose "₹ 750000 lakhs" is a slip for ₹7,50,000. `PF-RPF12-001` (W-073c) waits on the notified rate.
> `PF-EEINT-001` moved to W-079 (other sources).

> **Addendum (W-073b, 2026-10-06, D-127):** `PF-AGGCAP-002` is built as `PERQ-EMPLOYER-ACCRETION` (both years, every
> regime). TY 2026-27: S2 s.17(1)(i) with S4 r.16, read in the Gazette (CG-DL-E-20032026-271092, pp.1548–1549). TY
> 2025-26: 1961 s.17(2)(viia) as substituted by the Finance Act, 2020 s.13, with Rules 1962 r.3B (G.S.R. 155(E),
> 5-3-2021, from 1-4-2021), read in a third-party-hosted scan of the Gazette pages — `UNVERIFIED` until read in an
> egazette copy. PC1 + TP1 beyond the opening balance is ignored (r.16(2)(b); r.3B's Note).

> **Addendum (W-073c, 2026-10-08, D-151):** `PF-RPF12-001` is built as two rows. `PERQ-RPF-CONTRIBUTION` — the
> employer's contribution beyond 12% of salary, per employment, **TY 2025-26 only** (1961 Sch. IV Pt A r.6(a),
> thc.nic.in p.856; no amendment in the amending Acts 2019–2025, searched), against `SAL-SALARY-FOR-RPF` (r.2(h):
> basic, and DA where the terms provide). `PERQ-RPF-INTEREST` — interest credited above the notified **9.5%**, both
> years (r.6(b); S2 Sch. XI Pt A para 6 as substituted by FA 2026 s.127(a)(iii)); the rate is S.O. 1046(E),
> 13-5-2011, read only in a third-party mirror, so `UNVERIFIED`. Both stack on `PF-AGGCAP-001` as stated above.

### `DED-NPS-EMP-001` — employer NPS, the deduction side

| Rule ID | Statement | 2025-Act | TY 2026-27 | Status |
|---|---|---|---|---|
| `DED-NPS-EMP-001` | Employer contribution to the notified pension scheme is deductible up to **14% of salary** where the employer is the Central or a State Government, and **10% of salary** for any other employer. | **s.124(1)(a), (b)** | 14% / 10% | VERIFIED (S2, s.124(1)) |
| `DED-NPS-EMP-002` | **Where total income is chargeable under s.202(1) (new regime), "10%" is read as "14%"** — so a private-sector employee gets 14% in the new regime and 10% in the old. | **s.124(2)** | 14% (new) / 10% (old) | VERIFIED (S2, s.124(2)) |
| `DED-NPS-EMP-003` | `DED-NPS-EMP-001`/`-002` survive the new regime: s.202(2)(a)(xii) disallows Chapter VIII **"other than the provisions of sections 124(1) and 124(2), or 125(2) or 146"**. | **s.202(2)(a)(xii)** | available in both regimes | VERIFIED (S2, s.202(2)(a)(xii)) |
| `DED-NPS-SELF-001` | An additional deduction of **up to ₹50,000** for the assessee's own deposit in the notified pension scheme, **not** available for an amount already claimed under s.123, and vice versa. | **s.124(3), (5), (10)** | ₹50,000 | VERIFIED (S2, s.124(3),(5),(10)) |
| `DED-NPS-MINOR-001` | The `DED-NPS-SELF-001` deduction extends to deposits into a **minor's** account by a parent or guardian, with a **combined** ₹50,000 ceiling. | **s.124(4)** | ₹50,000 combined | VERIFIED (S2, s.124(4)) |
| `DED-NPS-SELF-002` | `DED-NPS-SELF-001` is **not** in the s.202(2)(a)(xii) carve-out — s.124(3) is disallowed in the new regime while s.124(1)/(2) survive. | **s.202(2)(a)(xii)** | OLD only | VERIFIED (S2 — the carve-out names 124(1) and 124(2) only) |

### **H-6 verdict: CONFIRMED on both limbs.**

`PF-AGGCAP-001` and `PF-AGGCAP-002` are two distinct charges — the excess contribution **and** the
accretion on it — and both are perquisites, i.e. **salary income**, not deductions. `PF-RPF12-001`
adds a third, independent charge on employer RPF contributions over 12% of salary. `PF-EEINT-001`
adds a fourth, on the *employee's* side. `INFERENCE`: employer retirement contributions need
**four** rules, not one exemption, and three of them add to income rather than subtracting from it.

### Worked example — `PF-AGGCAP-001` and `DED-NPS-EMP-002`, TY 2026-27, private employer, new regime

Basic ₹24,00,000, DA nil. Employer PF 12% = ₹2,88,000. Employer NPS 14% = ₹3,36,000. Employer
superannuation ₹2,00,000. No excess RPF interest.

```
Aggregate employer contribution = 2,88,000 + 3,36,000 + 2,00,000 =  8,24,000
PF-AGGCAP-001 excess over 7,50,000                               =    74,000  → perquisite, add to salary
PF-RPF12-001: employer PF 2,88,000 = exactly 12% of 24,00,000    →         0  additional charge
DED-NPS-EMP-002 (new regime, 14% of salary = 3,36,000):
  employer NPS contributed 3,36,000, limit 3,36,000              =  3,36,000  → deduction, survives new regime
```

`INFERENCE`: the same ₹3,36,000 is **both** counted in the `PF-AGGCAP-001` aggregate **and**
deducted under `DED-NPS-EMP-002`. These are different rules on different sides of the computation and
must not be netted against each other. See `G2`.

---

## R14 — Retirement and termination

All of the following are deductions under **s.19(1)** of the 2025 Act, which places them in the
*computation of salary income* rather than as exemptions. **Status: VERIFIED** (S2, s.19(1) Table and
s.19(2)) for TY 2026-27. For TY 2025-26 the corresponding provisions are s.10(10), 10(10A),
10(10AA), 10(10B), 10(10C) — **NEEDS VERIFICATION**, not opened.

| Rule ID | Sl. | Item | Amount deductible |
|---|---|---|---|
| `RET-GRAT-GOVT-001` | 3, 4 | Death-cum-retirement gratuity under the Central Government pension rules / CCS (Pension) Rules 2021 or a similar scheme; retiring gratuity under the defence Pension Code | **Entire amount** |
| `RET-GRAT-PGA-001` | 5 | Gratuity under the **Payment of Gratuity Act 1972** | Amount received, restricted to the amount computed under **s.4(2) and (3) of that Act** |
| `RET-GRAT-OTHER-001` | 6 | Any other gratuity on retirement, incapacitation or termination | **Minimum of** (a) actual gratuity; (b) **the amount notified by the Central Government** having regard to the Central Government limit; (c) **half a month's salary for each completed year of service**, computed as `A × B` where `A` = average salary for the **ten months immediately preceding** the month of the event and `B` = number of completed years |
| `RET-GRAT-AGG-001` | s.19(2)(a) | Gratuity from **more than one** employer | Aggregate deduction ≤ `A − B` where `A` = the notified limit and `B` = gratuity already allowed in earlier tax years |
| `RET-COMMUT-GOVT-001` | 7, 9 | Commuted pension under the Civil Pensions (Commutation) Rules or a similar scheme for civil/defence/all-India/State/local-authority service; commuted pension from a Schedule VII Sl. 3 fund | **Entire amount** |
| `RET-COMMUT-OTHER-001` | 8 | Commuted pension from any other employer | Commuted value of **one-third** of the pension normally receivable **if gratuity has also been received**; **one-half** in any other case |
| `RET-RETRENCH-001` | 10 | Retrenchment compensation under the Industrial Disputes Act 1947 or otherwise | **Minimum of** (a) compensation received; (b) the amount under **s.25F(b) of the ID Act**; (c) **an amount, not less than ₹50,000, as notified** by the Central Government |
| `RET-RETRENCH-SCHEME-001` | 11 | Retrenchment compensation under a Central-Government-approved scheme | **Compensation received**, in full |
| `RET-VRS-001` | 12 | Amount on **voluntary retirement** or termination under a VRS of a listed class of employer (PSU, any other company, statutory authority, local authority, co-operative society, University, IIT, Central/State Government, notified national institution, notified IIM) | **Minimum of** (a) compensation received; (b) **₹5,00,000** |
| `RET-VRS-002` | s.19(2)(e) | The VRS deduction is **once in a lifetime** — allowed in one tax year only — and is **mutually exclusive with s.157 relief** on the same amount | — |
| `RET-LEAVE-GOVT-001` | 13 | Leave encashment on retirement for a **Central or State Government** employee | **Entire amount** |
| `RET-LEAVE-OTHER-001` | 14 | Leave encashment on retirement for **any other** employee | **Minimum of** (a) cash equivalent of earned leave at credit, **entitlement capped at 30 days per year of actual service**; (b) `10 × B` where `B` = average monthly salary for the **ten months immediately preceding** retirement; (c) **the amount notified by the Central Government**; (d) the actual payment received |
| `RET-LEAVE-AGG-001` | s.19(2)(f) | Leave encashment from **more than one** employer: aggregate ≤ `A − B` where `A` = the notified limit and `B` = amounts already allowed in earlier tax years |
| `RET-SALARY-DEF-001` | s.19(2)(b) | For Sl. 6 and Sl. 14, **"Salary" includes dearness allowance if the terms of employment so provide, but excludes all other allowances and perquisites** |
| `RET-NOTICE-001` | — | **Notice pay** — no entry in the s.19(1) Table. Notice pay *recovered* from an employee is not a deduction the Act allows; notice pay *received* from an employer on termination falls within **"profits in lieu of salary"** (s.18(1)(a)(i), compensation in connection with termination) | **Fully taxable as salary** |

### **H-14 verdict: CONFIRMED as a real risk, with the specific shape now identified.**

Four of these limits — `RET-GRAT-OTHER-001(b)`, `RET-RETRENCH-001(c)`, `RET-LEAVE-OTHER-001(c)` and
the aggregate limits `RET-GRAT-AGG-001` / `RET-LEAVE-AGG-001` — are **not in the Act at all**. They
are *"the amount specified by the Central Government, by notification"*. `INFERENCE`: they are
**notification-driven values that can change without a Finance Act**, which is precisely the case for
which the rule pack must carry a source, a status and an effective date per value. The widely-quoted
₹20,00,000 gratuity limit and ₹25,00,000 leave-encashment limit are **NEEDS VERIFICATION for both
years** — open the relevant CBDT notifications.

`RET-VRS-002` is a **cross-year, cross-rule exclusivity constraint**, and `RET-GRAT-AGG-001` /
`RET-LEAVE-AGG-001` are **cross-year, cross-employer running totals**. Neither can be evaluated from
a single year's inputs. See `M3`.

---

## R15 — Chapter VIII deductions (the 2025-Act successor to Chapter VI-A)

### The map — which 1961-Act section became which 2025-Act section

| Rule ID | Deduction | 1961-Act | **2025-Act** | Status |
|---|---|---|---|---|
| `DED-MAP-001` | Life insurance, PF, ELSS, tuition fees, housing principal, etc. (80C) | s.80C | **s.123 + Schedule XV** | VERIFIED (S2) |
| `DED-MAP-002` | Pension fund annuity (80CCC) | s.80CCC | **Schedule XV para 1(x)** | VERIFIED (S2) |
| `DED-MAP-003` | Employee NPS contribution (80CCD(1)) | s.80CCD(1) | **Schedule XV para 1(y)** | VERIFIED (S2) |
| `DED-MAP-004` | Additional NPS (80CCD(1B)) | s.80CCD(1B) | **s.124(3)** | VERIFIED (S2) |
| `DED-MAP-005` | Employer NPS (80CCD(2)) | s.80CCD(2) | **s.124(1), (2)** | VERIFIED (S2) |
| `DED-MAP-006` | Agniveer Corpus Fund (80CCH) | s.80CCH | **s.125** | VERIFIED (S2) |
| `DED-MAP-007` | Health insurance (80D) | s.80D | **s.126** | VERIFIED (S2) |
| `DED-MAP-008` | Maintenance of a disabled dependant (80DD) | s.80DD | **s.127** | VERIFIED (S2) |
| `DED-MAP-009` | Medical treatment of specified disease (80DDB) | s.80DDB | **s.128** | VERIFIED (S2) |
| `DED-MAP-010` | Education-loan interest (80E) | s.80E | **s.129** | VERIFIED (S2) |
| `DED-MAP-011` | First-time home-loan interest, FY 2016-17 sanction (80EE) | s.80EE | **s.130** | VERIFIED (S2) |
| `DED-MAP-012` | Affordable-housing loan interest, FY 2019-22 sanction (80EEA) | s.80EEA | **s.131** | VERIFIED (S2) |
| `DED-MAP-013` | Electric-vehicle loan interest (80EEB) | s.80EEB | **s.132** | VERIFIED (S2) |
| `DED-MAP-014` | Donations (80G) | s.80G | **s.133** | VERIFIED (S2) |
| `DED-MAP-015` | Rent paid where no HRA (80GG) | s.80GG | **s.134** | VERIFIED (S2) |
| `DED-MAP-016` | Scientific-research donations (80GGA) | s.80GGA | **s.135** | VERIFIED (S2) |
| `DED-MAP-017` | Political contributions (80GGB / 80GGC) | ss.80GGB, 80GGC | **ss.136, 137** | VERIFIED (S2) |
| `DED-MAP-018` | Patent royalty (80RRB) | s.80RRB | **s.152** | VERIFIED (S2) |
| `DED-MAP-019` | Interest on deposits (80TTA **and** 80TTB, merged) | ss.80TTA, 80TTB | **s.153** | VERIFIED (S2) |
| `DED-MAP-020` | Person with disability (80U) | s.80U | **s.154** | VERIFIED (S2) |
| `DED-MAP-021` | **80CCE — the aggregate ₹1,50,000 ceiling — has no separate section.** Under the 2025 Act the ceiling is **written into s.123 itself** and applies to the whole of Schedule XV, which already contains employee PF (para 1(f)), superannuation (1(g)), annuity plans (1(x)) and employee NPS (1(y)). | s.80CCE | **s.123 (integral)** | VERIFIED (S2, s.123; Schedule XV paras 1(f),(g),(x),(y)) |

### Values and conditions

| Rule ID | Statement | 2025-Act | Regime | TY 2026-27 value | Status |
|---|---|---|---|---|---|
| `DED-80C-001` | Deduction of the **whole of the amount paid or deposited in the tax year**, being the aggregate of the sums enumerated in **Schedule XV**, **as does not exceed ₹1,50,000**. | **s.123** | OLD | ₹1,50,000 | VERIFIED (S2, s.123) |
| `DED-80C-ITEMS-001` | Schedule XV includes, among others: life insurance premium on self/spouse/child; deferred annuity; Government deferred-annuity salary deduction capped at **20% of salary**; PF under the Provident Funds Act 1925; notified PF in the name of self/spouse/child; **employee contribution to a recognised provident fund**; **employee contribution to an approved superannuation fund**; notified security/deposit schemes; NSC; ULIP; Mutual Fund pension plans; LIC annuity plans; notified infrastructure/city-development instruments; **tuition fees to an Indian institution for the full-time education of any two children, excluding development fees and donations**; **purchase or construction of a residential house** (subject to Schedule XV para 3); **5-year scheduled-bank term deposit**; NABARD bonds; **Senior Citizen Savings Scheme**; **5-year Post Office Time Deposit**; Central-Government-employee Tier-II NPS with a 3-year lock-in; **annuity plan for pension (the 80CCC limb)**; **employee NPS contribution (the 80CCD(1) limb)**; approved equity/debenture and Mutual Fund issues. | **Schedule XV para 1(a)–(z)** | OLD | — | VERIFIED (S2, Schedule XV para 1) |
| `DED-80C-NPS-CAP-001` | The employee-NPS limb inside Schedule XV is itself capped at **10% of salary** (basic + DA-if-provided) for an employee, or **20% of gross total income** for anyone else — **before** the ₹1,50,000 ceiling bites. | **Schedule XV para 1(y)** | OLD | 10% / 20% | VERIFIED (S2) |
| `DED-80D-001` | Self and family: health insurance premium, CGHS or notified scheme contributions, **and** preventive health check-up, **up to ₹25,000 in aggregate**. | **s.126(2)(a)** | OLD | ₹25,000 | VERIFIED (S2) |
| `DED-80D-002` | Parents: health insurance premium **and** preventive health check-up, **up to ₹25,000 in aggregate**. | **s.126(2)(b)** | OLD | ₹25,000 | VERIFIED (S2) |
| `DED-80D-003` | **Medical expenditure** on the health of self or family: **up to ₹50,000**. | **s.126(2)(c)** | OLD | ₹50,000 | VERIFIED (S2) |
| `DED-80D-004` | **Medical expenditure** on the health of a parent: **up to ₹50,000**. | **s.126(2)(d)** | OLD | ₹50,000 | VERIFIED (S2) |
| `DED-80D-005` | **Preventive health check-up sub-limit: ₹5,000 in aggregate** across `DED-80D-001` and `DED-80D-002`. | **s.126(3)** | OLD | ₹5,000 | VERIFIED (S2) |
| `DED-80D-006` | **Two independent ₹50,000 ceilings**: `(a)+(c)` for self/family ≤ ₹50,000, and `(b)+(d)` for parents ≤ ₹50,000. | **s.126(4)** | OLD | ₹50,000 each | VERIFIED (S2) |
| `DED-80D-007` | Where the person insured is a **senior citizen**, the ₹25,000 in `DED-80D-001`/`-002` becomes **₹50,000**. | **s.126(8)(a)** | OLD | ₹50,000 | VERIFIED (S2) |
| `DED-80D-008` | **Medical expenditure on a senior citizen is allowed only if no amount has been paid to keep health insurance in force for that person.** | **s.126(7)** | OLD | — | VERIFIED (S2) |
| `DED-80D-009` | **Multi-year lump-sum premium** is spread: the deduction for each relevant tax year is the *appropriate fraction* — numerator 1, denominator the number of relevant tax years. | **s.126(8)(b), (10)(a), (10)(c)** | OLD | — | VERIFIED (S2) |
| `DED-80D-010` | **Payment mode**: cash is permitted **only** for preventive health check-up; everything else must be otherwise than in cash. | **s.126(9)** | OLD | — | VERIFIED (S2) |
| `DED-80D-011` | "Family" for s.126 means **spouse and dependent children** — parents are dealt with separately. | **s.126(10)(b)** | OLD | — | VERIFIED (S2) |
| `DED-80DD-001` | Maintenance including medical treatment of a **dependant with disability**: **₹75,000**; **₹1,25,000** where the dependant has a **severe disability** (80% or more). Requires a medical certificate with the return; re-certification where the certificate expires. The dependant must not have claimed `DED-80U-001` themselves. | **s.127(1), (3), (6), (7), (8)** | OLD | ₹75,000 / ₹1,25,000 | VERIFIED (S2) |
| `DED-80DDB-001` | Medical treatment of a **prescribed disease or ailment**: **actual amount paid or ₹40,000, whichever is less**. Resident only. *(The senior-citizen enhancement, if any, was not in the extracted window.)* | **s.128(1)** | OLD | ₹40,000 | VERIFIED as to the ₹40,000 base (S2, s.128(1)); the senior-citizen sub-section is **NEEDS VERIFICATION** — re-read S2 s.128(2)-(4) |
| `DED-80E-001` | **Interest** on a loan from a financial institution or approved charitable institution for the **higher education** of self or a relative (spouse, children, or a student for whom the assessee is legal guardian), paid out of income chargeable to tax. **No monetary cap.** | **s.129(1), (3)(e)** | OLD | uncapped | VERIFIED (S2) |
| `DED-80E-002` | Allowed for the **initial tax year and the seven immediately succeeding tax years**, or until the interest is fully paid, **whichever is earlier**. "Initial tax year" = the year the assessee **starts paying** interest. | **s.129(2), (3)(d)** | OLD | 8 years | VERIFIED (S2) |
| `DED-80EE-001` | Residential house-loan interest, **up to ₹50,000**: loan sanctioned **1 Apr 2016 – 31 Mar 2017**; loan ≤ ₹35,00,000; property value ≤ ₹50,00,000; assessee owned no residential house on the sanction date. | **s.130(2), (3)** | OLD | ₹50,000 | VERIFIED (S2) |
| `DED-80EEA-001` | Residential house-loan interest, **up to ₹1,50,000**, for an assessee **not eligible** for `DED-80EE-001`: loan sanctioned **1 Apr 2019 – 31 Mar 2022**; **stamp-duty value ≤ ₹45,00,000**; owned no residential house on the sanction date. | **s.131(1), (2)** | OLD | ₹1,50,000 | VERIFIED (S2) |
| `DED-80EEB-001` | **Electric-vehicle** loan interest, **up to ₹1,50,000**; loan sanctioned **1 Apr 2019 – 31 Mar 2023**. | **s.132(2), (3)** | OLD | ₹1,50,000 | VERIFIED (S2) |
| `DED-80EX-001` | `DED-80EE-001`, `DED-80EEA-001` and `DED-80EEB-001` each carry an **anti-double-deduction bar**: interest allowed under one is not allowed under **any other provision of the Act** in the same or any other tax year — including s.22(1)(b). | **ss.130(4), 131(3), 132(4)** | OLD | — | VERIFIED (S2) |
| `DED-80G-001` | Donations. The Act enumerates 100%-without-limit funds (National Defence Fund, PMNRF, PM CARES, …) and, elsewhere in s.133, categories at 50% and categories subject to a **qualifying-limit** computed on adjusted gross total income. *(Only the 100%-without-limit head was in the extracted window.)* | **s.133(1)(a)** | OLD | — | **NEEDS VERIFICATION** for the 50% categories, the qualifying-limit percentage and its base — re-read S2 s.133 in full |
| `DED-80GG-001` | Rent paid for the assessee's **own residence** where no HRA is received: **least of** (a) rent paid **minus 10% of total income**; (b) **₹5,000 per month**; (c) **25% of total income**. "Total income" for (a) and (c) means total income **before** this deduction. | **s.134(1), (2), (5)** | OLD | ₹5,000/month | VERIFIED (S2, s.134) |
| `DED-80GG-002` | **Not available** where the assessee, spouse, minor child or the assessee's HUF owns residential accommodation at the place where the assessee resides or works; or the assessee owns a house elsewhere valued under s.21(6)/(7)(a); **or the assessee has any Schedule III Sl. 11 (HRA) income**. | **s.134(4)** | OLD | — | VERIFIED (S2, s.134(4)) |
| `DED-80TTA-001` | Interest on deposits with a bank, a co-operative banking society or a Post Office: for an individual **who is not a senior citizen** (or an HUF), **up to ₹10,000 on savings-account deposits only, excluding time deposits**. | **s.153(1)(a)/(c), 153(2)(a)** | OLD | ₹10,000 | VERIFIED (S2, s.153) |
| `DED-80TTB-001` | For an individual **who is a senior citizen**: **up to ₹50,000 on deposits in any account, including time deposits**. | **s.153(1)(b), 153(2)(b)** | OLD | ₹50,000 | VERIFIED (S2, s.153) |
| `DED-80U-001` | Resident individual certified as a **person with disability**: **₹75,000**; **person with severe disability**: **₹1,25,000**. Certificate must be furnished with the return; re-certification on expiry. | **s.154(1), (2)** | OLD | ₹75,000 / ₹1,25,000 | VERIFIED (S2, s.154) |
| `DED-AGNIVEER-001` | Agniveer's own subscription to the Agniveer Corpus Fund: **whole amount**. Central Government contribution to that account: **whole amount** — and **this second limb survives the new regime** (s.202(2)(a)(xii) names s.125(2)). | **s.125(1), (2)** | (1) OLD, (2) BOTH | whole | VERIFIED (S2, ss.125, 202(2)(a)(xii)) |
| `DED-GTI-CAP-001` | **The aggregate of all Chapter VIII deductions shall not exceed gross total income.** | **s.122(2)** | BOTH | — | VERIFIED (S2, s.122(2)) |
| `DED-GTI-DEF-001` | "Gross total income" = total income computed under the Act **before** any Chapter VIII deduction. | **s.122(10)** | BOTH | — | VERIFIED (S2, s.122(10)) |
| `DED-RETURN-001` | Deductions under **Part C** of Chapter VIII (the profit-linked ones) are denied where the return is not furnished by the s.263(1) due date or the claim is not made in that return. Part B (the payment-based deductions a salaried user claims) is **not** subject to this bar. | **s.122(5)** | — | — | VERIFIED (S2, s.122(5)) |
| `DED-NEWREGIME-001` | In the new regime the **entire** Chapter VIII is disallowed except **s.124(1), s.124(2), s.125(2) and s.146**. So in the new regime a salaried individual has exactly **two** Chapter VIII deductions available: employer NPS and the Agniveer Government contribution. | **s.202(2)(a)(xii)** | NEW | — | VERIFIED (S2) |

> **Addendum (W-075c4, 2026-10-07, D-141):** s.134(3)'s *"as may be prescribed"* is S4 **r.65** (p.1581): *"the
> assessee shall file declaration in Form No. 31"* (pp.1913–1914) — the only condition; **no area or place limit is
> prescribed** (the Rules' amendment chain after G.S.R. 198(E) not traced, as S6 notes). Form 31 asks the *"Period of
> Stay (in Months)"*, which limb (b) counts (OQ-22). `DED-80GG-001` and `-002` are built as `DED-RENT-PAID` (TY 2026-27).

> **Addendum (W-075c6, 2026-10-07, D-142):** `DED-80G-001`'s NEEDS VERIFICATION is answered — S2 s.133 read in full
> (pp.167–170): the whole of (1)(a)(i)–(xxii) without limit; the whole of (1)(a)(xxiii) family planning and (xxiv) (a
> company's) within the limit; **50%** of (1)(b)(i) the Prime Minister's Drought Relief Fund without limit and of
> (1)(b)(ii)–(vi) within the limit; the limit is **10% of adjusted gross total income**, applied to the sums (s.133(2));
> adjusted gross total income is gross total income less any untaxed portion and every other Chapter VIII deduction
> (s.133(7)(a)); cash over ₹2,000 disallowed (s.133(5)); s.133(6) reaches (1)(b)(ii) only. Built as
> `DED-DONATION-WHOLE`, `-HALF`, `-QUALIFYING`, `-CASH` (TY 2026-27). The order against s.134 is OQ-23; mixed sums
> under a binding limit, OQ-24.

> **Addendum (W-075d1, 2026-10-07, D-144):** the TY 2025-26 figures of `DED-80C-001`, `-ITEMS-001`, `-NPS-CAP-001`,
> `DED-NPS-EMP-001`…`-003`, `DED-NPS-SELF-001`, `DED-NPS-MINOR-001` and `DED-GTI-CAP-001` are read and built (1961
> ss.80C, 80CCC, 80CCD, 80CCE, 80A(2); `docs/taxplanning-build/sources/2026-10-07-chapter-via.md`). They equal TY
> 2026-27's: 1,50,000 over 80C + 80CCC + 80CCD(1) (s.80CCE); 10% of salary for an employee's 80CCD(1); 50,000 under
> 80CCD(1B), a minor's account included from AY 2026-27 (FA 2025 s.17); 80CCD(2) 14% for a Central or State Government,
> 10% for another employer, 14% under s.115BAC(1A) (F(No.2)A 2019 s.24, FA 2022 s.20, F(No.2)A 2024 s.25); the
> policy-share bands 20/10/15% (s.80C(3), (3A)).

> **Addendum (W-075d2, 2026-10-07, D-145):** `DED-80D-*`'s TY 2025-26 figures (1961 s.80D, unamended after 2019) equal
> TY 2026-27's and are built. Both Acts' (2)(b) give the parents' 25,000 for insurance and check-ups only — **no
> notified or Government health scheme** — and the senior citizen's 50,000 is for a sum paid for **health insurance**
> (s.126(8); 1961 s.80D(4)), not a scheme contribution; the engine now applies both.

> **Addendum (W-075d3, 2026-10-07, D-146):** `DED-80U-001`, `DED-80DD-001` and `DED-80DDB-001`'s TY 2025-26 figures are
> built (1961 ss.80U, 80DD, 80DDB). 80U and 80DDB equal TY 2026-27's. **1961 s.80DD gives a fixed sum** — *"a deduction
> of a sum of seventy-five thousand rupees"* (1,25,000 severe) once anything is spent or deposited — where the 2025
> Act's s.127 says *"up to ₹ 75000"* (built as the lesser of the amount and the limit; OQ-19).

> **Addendum (W-075d4, 2026-10-07, D-147):** `DED-80E-001`, `-002`, `DED-80EE-001`, `DED-80EEA-001` and `DED-80EEB-001`'s
> TY 2025-26 figures are built (1961 ss.80E, 80EE; 80EEA and 80EEB as inserted by F(No.2)A 2019 s.25, 80EEA's window
> extended to 31-3-2022 by FA 2020 s.32 and FA 2021 s.27) — every figure, window and condition equal to TY 2026-27's.

> **Addendum (W-075d5, 2026-10-07, D-148):** `DED-AGNIVEER-001`, `DED-80GG-001`, `-002` and `DED-80G-001`'s TY 2025-26
> figures are built (1961 s.80CCH by FA 2023 s.39; s.80GG with Rules 1962 r.11B; s.80G with PM CARES added by the
> Taxation and Other Laws (Relaxation and Amendment of Certain Provisions) Act, 2020 s.3(XII)(a), and (a)(ii), (iiic),
> (iiid) omitted from AY 2024-25 by FA 2023 s.40) — equal to TY 2026-27's once s.80G's clauses are mapped to s.133's.
> Every Chapter VIII section built for TY 2026-27 now computes for TY 2025-26.

### **H-7 verdict: CONFIRMED on both limbs.**

`FACT (S2, Schedule XV para 1(f))`: *"contribution by an employee to a recognised provident fund"* is
a Schedule XV item, therefore **inside** the s.123 ₹1,50,000 ceiling. Omitting employee PF from the
80C aggregate understates the user's used 80C headroom by, typically, 12% of basic — which makes
every downstream "how much more 80C should you invest" recommendation wrong.

`FACT (S2, s.123)`: there is **no separate 80CCE section** in the 2025 Act because the aggregation is
structural — one section, one ceiling, one schedule of qualifying sums. `INFERENCE`: the correct
model is not "80C + 80CCC + 80CCD(1), then cap at 1.5L". It is **one deduction (`DED-80C-001`) with a
list of contributing component codes and one ceiling**, plus a **sub-cap** on the NPS limb
(`DED-80C-NPS-CAP-001`). Modelling it the 1961-Act way and then bolting 80CCE on top is how the
aggregate gets forgotten.

### **H-11 verdict: CONFIRMED — a single flat 80D cap is wrong four ways.**

There are **two** ₹50,000 ceilings (`DED-80D-006`), each fed by **two** components; a **₹5,000**
preventive sub-limit that cuts across both (`DED-80D-005`); a **senior-citizen substitution** that
changes ₹25,000 to ₹50,000 (`DED-80D-007`); and a **mutual-exclusivity** rule between senior-citizen
medical expenditure and insurance for the same person (`DED-80D-008`). Any one number cannot express
this. See `G2` and `T5`.

### Worked example — `DED-80C-001` with `DED-80C-NPS-CAP-001`, TY 2026-27, old regime

Basic ₹10,00,000, DA nil. Employee PF 12% = ₹1,20,000. LIC premium ₹30,000. Children's tuition fees
₹60,000 (two children). Employee NPS contribution ₹1,50,000.

```
Schedule XV qualifying sums:
  para 1(f)  employee RPF contribution                        = 1,20,000
  para 1(a)  LIC premium                                      =   30,000
  para 1(q)  tuition fees, two children                       =   60,000
  para 1(y)  employee NPS, capped at 10% of salary
             10% × 10,00,000 = 1,00,000  (actual 1,50,000)    = 1,00,000
                                            raw aggregate     = 3,10,000

DED-80C-001 ceiling                                           = 1,50,000
deduction under s.123                                         = 1,50,000

DED-NPS-SELF-001 (s.124(3)): up to 50,000, but s.124(5)/(10) bar
  double-counting with s.123. Unused NPS = 1,50,000 − 1,00,000 =   50,000
  of which none was claimed under s.123                       =   50,000

Total NPS-related and 80C-related deduction                   = 2,00,000
```

`INFERENCE`: `DED-80C-NPS-CAP-001` binds **before** the ₹1,50,000 ceiling, and the residue then feeds
`DED-NPS-SELF-001`. Get the order wrong and the ₹50,000 either vanishes or is counted twice. This
ordering is the single most common error in this area and is why `G2` exists as a separate algorithm.

> **Addendum (W-075a, 2026-10-06, D-129, D-130):** built for TY 2026-27, each read in Gazette CG-DL-E-22082025-265620 —
> `DED-80C-001` / `-ITEMS-001` / `-NPS-CAP-001` as `DED-SCHEDULE-XV` (s.123, p.159; Schedule XV para 1, pp.561–563),
> with 21 of the 26 clauses as components; (a), (c), (j), (k), (l) held for W-075a2 (para 2's share of the sum assured,
> pp.563–564; (c)'s "20 % of salary" names no salary). `DED-NPS-EMP-001`…`-003` as `DED-NPS-EMPLOYER-GOVT`, `-OTHER`,
> `-NEW-TAX-REGIME` (s.124(1)–(2), p.160); `DED-NPS-SELF-001`, `-002`, `DED-NPS-MINOR-001` as `DED-NPS-OWN` (s.124(3)–(5), (10));
> `DED-GTI-CAP-001` / `-DEF-001` as `DED-WITHIN-GROSS-TOTAL` (s.122(2), (10), pp.158–159); `DED-NEWREGIME-001` as each
> row's regimes (s.202(2)(a)(xii), p.227). Para 1(y)(ii)'s 20% of gross total income for a non-employee is not modelled.
> The TY 2025-26 versions wait for W-075d.

> **Addendum (W-075a2, 2026-10-06, D-134, D-135):** Schedule XV para 1(a), life insurance premiums, is built, each policy's
> premium within para 2's share of its actual capital sum assured (pp.563–564) as `DED-PREMIUM-SHARE`. Para 1(c)'s
> "20 % of salary" names no salary Schedule XV defines (para 6 does not; S2 s.16 is "for the purposes of this Part") —
> OQ-16; whether para 2 reaches para 1(j), (k), (l) is OQ-17; both W-075a3. Para 4's reversal of an earlier deduction is
> not modelled.

> **Addendum (W-075b, 2026-10-06, D-136):** `DED-80D-001`…`-011` are built for TY 2026-27 (S2 s.126, pp.161–162), each
> read in the Gazette. `DED-80D-005`'s ₹5,000 is applied across both buckets and allocated in the ratio claimed — `DR 06`
> G2.3's reading; the section states only the aggregate. A senior citizen is resident in India and sixty at any time in
> the year (s.2(100), p.20). One premium covering a senior citizen and someone who is not is OQ-18; separate premiums put
> the bucket on the senior limit if any covers a senior citizen. s.126(5)–(6) (a HUF) is not modelled.

> **Addendum (W-075c1, 2026-10-06, D-138):** `DED-80U-001`, `DED-80DD-001` and `DED-80DDB-001` are built for TY 2026-27
> (S2 s.154, s.127, s.128). **`DED-80DDB-001`'s senior-citizen limb is now read:** s.128(4) substitutes "₹ 100000" for
> "₹40000" where the amount is paid for a senior citizen (p.165); s.128(3) reduces the deduction by insurance or employer
> reimbursement. s.127's "up to ₹ 75000" is built as a ceiling on the amount spent, one dependant at a time (OQ-19);
> mixed senior and non-senior treatment under s.128 is OQ-20.

> **Addendum (W-075c2, 2026-10-06, D-139):** `DED-80E-001`, `-002`, `DED-80EE-001`, `DED-80EEA-001` and `DED-80EEB-001` are
> built for TY 2026-27 (S2 s.129–132, pp.165–167). s.130's "value of residential house property" is undefined and
> entered as the house's value; s.131's is the stamp duty value. `DED-80EX-001`'s bar is recorded for the house-property
> head (W-078).

> **Addendum (W-075c3, 2026-10-06, D-140):** S2 s.16(k) and (l) make the employer's pension-scheme contribution and
> the Government's Agniveer Corpus Fund contribution **salary** (pp.37–38) — so `DED-NPS-EMP-001`…`-003` and
> `DED-AGNIVEER-001`(2) deduct amounts first included in income; the engine now includes both. `DED-AGNIVEER-001` is
> built (s.125, p.161). Whether the s.17(1)(h) excess (`PF-AGGCAP-001`) should leave out the pension contribution
> s.16(k) already counts is OQ-21.

---

## R16 — Income from house property

| Rule ID | Statement | 1961-Act | 2025-Act | Regime | Status |
|---|---|---|---|---|---|
| `HP-CHARGE-001` | The **annual value** of buildings and appurtenant land owned by the assessee is chargeable under this head, except portions occupied for the assessee's own business or profession. | s.22 | **s.20(1), (2)** | BOTH | VERIFIED (S2, s.20) |
| `HP-AV-001` | Annual value = the **higher of** (a) the sum for which the property might reasonably be expected to let from year to year, or (b) the **actual rent received or receivable** if let. | s.23(1) | **s.21(1)** | BOTH | VERIFIED (S2, s.21(1)) |
| `HP-AV-VACANCY-001` | Where a let property was **vacant** for the whole or part of the year and, owing to the vacancy, actual rent is less than the reasonable-expectation sum, annual value = **the amount actually received or receivable**. | s.23(1)(c) | **s.21(2)** | BOTH | VERIFIED (S2, s.21(2)) |
| `HP-AV-TAX-001` | Annual value is **reduced by local-authority taxes (including service taxes) actually paid during the tax year by the owner**, irrespective of the year they became payable. | s.23(1) proviso | **s.21(3)** | BOTH | VERIFIED (S2, s.21(3)) |
| `HP-AV-UNREAL-001` | **Unrealised rent** is excluded from actual rent, subject to prescribed rules. | s.23(1) Expl. + r.4 | **s.21(4)** | BOTH | VERIFIED as to the section (S2, s.21(4)); the prescribed conditions are NEEDS VERIFICATION |
| `HP-AV-STOCK-001` | Property held as **stock-in-trade** and not let at any time: annual value **nil for two years** from the end of the financial year in which the completion certificate is obtained. **FA 2026 s.37 substitutes "nil up to" for "nil for"** — a clarification that the relief runs *up to* two years. | s.23(5) | **s.21(5)** as amended by **FA 2026 s.37** | BOTH | VERIFIED (S2, s.21(5); S3, Ch. III Part B s.37) |
| `HP-SELFOCC-001` | Annual value is **nil** where the owner **occupies it for his own residence or cannot actually occupy it due to any reason**. No employment/business condition. | s.23(2) as substituted by **FA 2025 s.10** | **s.21(6)** | BOTH | VERIFIED both years (S7, FA 2025 s.10; S2, s.21(6)) |
| `HP-SELFOCC-002` | `HP-SELFOCC-001` applies to **at most two houses** specified by the assessee, and **not at all** if the house or any part of it is **actually let at any time** during the year or the owner derives **any other benefit** from it. | s.23(4) | **s.21(7)(a), (b)** | BOTH | VERIFIED (S2, s.21(7)) |
| `HP-DED-30-001` | **30% of the annual value** as determined under s.21. A flat statutory allowance — **not** a deduction of actual expenses. | s.24(a) | **s.22(1)(a)** | BOTH | VERIFIED (S2, s.22(1)(a)) |
| `HP-DED-INT-001` | **Interest payable** on capital borrowed to acquire, construct, repair, renew or reconstruct the property. For a **let-out** property there is **no monetary cap** in s.22(1)(b) itself. | s.24(b) | **s.22(1)(b)** | see `HP-NEW-001` | VERIFIED (S2, s.22(1)(b)) |
| `HP-DED-PRECON-001` | **Pre-construction interest** — interest for the period before the tax year in which the property was acquired or constructed — is allowed in **five equal instalments** starting with that tax year, reduced by any amount already allowed under another provision. | s.24(b) proviso | **s.22(1)(c), 22(3)** | see `HP-NEW-001` | VERIFIED (S2, s.22(1)(c), 22(3)) |
| `HP-CAP-SELFOCC-001` | For **s.21(6) (self-occupied) properties**, the aggregate interest deduction is **₹2,00,000**, subject to: (i) acquisition or construction completed within **five years** from the end of the tax year in which capital was borrowed, and (ii) a **lender's certificate**; otherwise **₹30,000**. | s.24(b) provisos | **s.22(2)(a), (b)** | OLD only | VERIFIED (S2, s.22(2)) |
| `HP-CAP-SELFOCC-002` | **FA 2026 s.38 extends the `HP-CAP-SELFOCC-001` cap to cover s.22(1)(b) *and (c)*** — i.e. **pre-construction interest is inside the ₹2,00,000 / ₹30,000 cap** for a self-occupied property, from TY 2026-27. | — | **s.22(2)** as amended by **FA 2026 s.38** | OLD only | VERIFIED (S3, Ch. III Part B s.38). For TY 2025-26 the 1961-Act position is **NEEDS VERIFICATION** |
| `HP-CAP-SELFOCC-003` | The **aggregate** of `HP-CAP-SELFOCC-001` deductions across **all** s.21(6) properties shall not exceed **₹2,00,000** — so owning two self-occupied houses does not double the cap. | s.24(b) proviso | **s.22(5)** | OLD only | VERIFIED (S2, s.22(5)) |
| `HP-DED-CERT-001` | The lender's certificate must state the interest payable **and** the interest on any new loan taken to repay the original capital. | s.24(b) proviso | **s.22(4)** | — | VERIFIED (S2, s.22(4)) |
| `HP-ARREARS-001` | **Arrears of rent** or **unrealised rent realised subsequently** are taxable under this head in the year of receipt, **whether or not the assessee still owns the property**, with a **30% deduction**. | s.25A | **s.23(1), (2), (3)** | BOTH | VERIFIED (S2, s.23) |
| `HP-COOWN-001` | Co-owners with **definite and ascertainable shares** are **not** assessed as an AOP; each computes separately on their share, and **each is individually entitled to the s.21(6) relief**. | s.26 | **s.24(1), (2)** | BOTH | VERIFIED (S2, s.24) |
| `HP-DEEMOWN-001` | "Owner" includes a transferor to a spouse (except under a separation agreement) or a minor child other than a married daughter; an impartible-estate holder; a society/company allottee; a s.53A part-performance possessor; and a long-lease (12 years or more) holder. | s.27 | **s.25(a)–(e)** | BOTH | VERIFIED (S2, s.25) |
| `HP-NEW-001` | In the **new regime**, s.202(2)(a)(v) disallows **s.22(1)(b) in respect of s.21(6) properties** — i.e. **self-occupied house-loan interest is not deductible**. Interest on a **let-out** property remains deductible, because s.22(1)(b) is disallowed only for s.21(6) properties. | s.115BAC(2) | **s.202(2)(a)(v)** | NEW | VERIFIED (S2, s.202(2)(a)(v)) |
| `HP-NEW-002` | In the new regime, **house-property loss cannot be set off against any other head** and is **not carried forward** (see `SETOFF-NEW-001`). So a let-out property's interest deduction is useful in the new regime **only up to the point where the head's income reaches nil**. | s.115BAC(2) | **s.202(2)(b)(ii), (3)** | NEW | VERIFIED (S2) |

### **H-12 verdict: CONFIRMED — the self-occupied / let-out distinction is load-bearing in four separate rules.**

It determines: (1) whether annual value is nil (`HP-SELFOCC-001`); (2) whether the interest deduction
is capped at ₹2,00,000 or uncapped (`HP-CAP-SELFOCC-001` vs `HP-DED-INT-001`); (3) whether the
deduction survives the new regime at all (`HP-NEW-001`); (4) whether pre-construction interest is
inside or outside the cap (`HP-CAP-SELFOCC-002`). A model with one "housing loan interest" number and
no property-level `occupancy` attribute cannot express any of these.

### Worked example — let-out property, TY 2026-27, old regime

Rent received ₹45,000/month; municipal taxes paid ₹18,000; home-loan interest ₹3,20,000;
pre-construction interest ₹2,00,000 accumulated, third of five instalments due.

```
HP-AV-001   annual value = higher of expected letting value and actual rent
            actual rent  = 45,000 × 12                          = 5,40,000
HP-AV-TAX-001  less municipal taxes actually paid               =   18,000
            annual value                                        = 5,22,000
HP-DED-30-001  30% of 5,22,000                                  = 1,56,600
HP-DED-INT-001 interest, no cap (let out)                       = 3,20,000
HP-DED-PRECON-001 instalment 3 of 5: 2,00,000 ÷ 5               =   40,000
            income from house property
            = 5,22,000 − 1,56,600 − 3,20,000 − 40,000           =    5,400
```

Same facts, **self-occupied** instead of let out:

```
HP-SELFOCC-001  annual value                                    =        0
HP-DED-30-001   30% of 0                                        =        0
HP-CAP-SELFOCC-001 + -002 : interest under 22(1)(b) AND (c)
   3,20,000 + 40,000 = 3,60,000, capped at                      = 2,00,000
            income from house property                          = −2,00,000  (a loss)
SETOFF-HP-001  set off against other heads, capped at           = 2,00,000
```

Same facts, self-occupied, **new regime**:

```
HP-NEW-001   s.22(1)(b) disallowed for a s.21(6) property       =        0
             income from house property                         =        0
```

> **Erratum (W-078b, 2026-10-08):** the new-regime result omits the ₹40,000 pre-construction instalment — S2
> s.202(2)(a)(v) disallows s.22(1)(b) only, and s.22(1)(c) is not extended to it by the Finance Act, 2026 (s.56 omits
> only s.202(2)(a)(iii)) — so in TY 2026-27 the instalment is allowed within the s.22(2) cap (as amended by FA 2026
> s.38) and the house's income is **−₹40,000**, a loss the new regime may not set off against another head
> (s.202(2)(b)(ii)); the ₹0 holds for TY 2025-26, where 1961 s.115BAC(2)(i) removes the whole of s.24(b) (D-156 C1(a)).
> Verified by: Gazette 265620 p.227; Gazette 271439 p.46; Gazette 244830 p.34;
> `HousePropertyInterestTest.evaluate_whenTheNewRegimeAppliesInTaxYear2026_allowsOnlyTheOwnResidencesInstalment`.

`INFERENCE`: the identical property produces ₹5,400 of income, a ₹2,00,000 deduction, or nothing at
all, depending on two flags. That is the whole case for `A1`'s regime comparison being property-aware.

> **Addendum (W-078a, 2026-10-08, D-155):** the head is built without interest — `HP-ANNUAL-VALUE`, `HP-OWN-RESIDENCE`
> (with `OwnResidenceHouses` 2), `HP-STANDARD-DEDUCTION`, `HP-ARREARS`, both years (TY 2025-26: 1961 ss.23–26 in the
> thc.nic.in copy, s.23(2) as substituted by FA 2025 s.10, Gazette 262125 p.20). Interest is W-078b; unrealised rent
> W-078c. **`HP-CAP-SELFOCC-002`'s TY 2025-26 position is now read:** 1961 s.24(b)'s Explanation deducts
> pre-construction interest *"under this clause"*, and the second proviso caps *"the amount of deduction … under this
> clause"* at two lakh rupees — so it is inside the cap in TY 2025-26 as FA 2026 s.38 makes it in TY 2026-27 (thc
> p.147).
>
> **Addendum (W-078b, 2026-10-08, D-156):** interest is built, both years — `HP-INTEREST` (s.22(1)(b), (6); 1961
> s.24(b), s.25), `HP-PRE-CONSTRUCTION` (s.22(1)(c), (3); 1961 s.24(b) Explanation; `TaxYearSpan(4)`), and for an own
> residence `HP-OWN-RESIDENCE-INTEREST` (`ExceptIn NEW_TAX_REGIME`), `HP-OWN-RESIDENCE-PRE-CONSTRUCTION` (TY 2025-26
> `ExceptIn NEW_TAX_REGIME`; TY 2026-27 every regime, see the erratum above) and `HP-OWN-RESIDENCE-INTEREST-CAP`
> (`OwnResidenceInterestLimit`: ₹2,00,000 conditions met, ₹30,000 any other case, ₹2,00,000 aggregate; five years; TY
> 2025-26 also borrowed on or after 1-4-1999, the 1961 second proviso). Loans are the assessee's own (s.24(2)); interest
> deducted under s.130/131 is left out; properties are netted within the head (`SETOFF-INTRA-001`, built as a row by
> W-081) and the head below nil is refused until W-081. `HP-CAP-SELFOCC-002` for TY 2025-26: built inside the cap.

---

## R17 — Income from other sources

| Rule ID | Statement | 1961-Act | 2025-Act | Regime | TY 2025-26 | TY 2026-27 | Status |
|---|---|---|---|---|---|---|---|
| `OS-INT-001` | Interest income is chargeable under this head unless it falls under another head. Savings and time-deposit interest is fully taxable, with relief only through `DED-80TTA-001` / `DED-80TTB-001`. | s.56 | s.92 | BOTH | — | — | VERIFIED as to the head (S2, s.92 read via s.93 cross-references); the enumerating sub-section was **not** read in full — NEEDS VERIFICATION for the exact s.92(2) clause list |
| `OS-DIV-001` | Dividends are taxable at slab rates under this head. | s.56(2)(i) | s.92 | BOTH | — | — | VERIFIED as to chargeability (S2, s.93(2) presupposes it) |
| `OS-DIV-DED-2025` | Against dividend income (and Mutual Fund / specified-company unit income), **only interest expense** is deductible, **limited to 20% of that income**. | s.57(1) proviso | **s.93(2)(b)** *as enacted* | BOTH | 20% *(1961-Act position NEEDS VERIFICATION)* | — | VERIFIED as the 2025-Act *enacted* text (S2, s.93(2)(b)) |
| `OS-DIV-DED-2026` | **FA 2026 s.44(b) substitutes s.93(2): "no deduction shall be allowed"** against dividend income or Mutual Fund / specified-company unit income. The 20% interest allowance is **gone from TY 2026-27**. | — | **s.93(2)** as substituted by **FA 2026 s.44(b)** | BOTH | — | **nil deduction** | VERIFIED (S3, Ch. III Part B s.44(b)) |
| `OS-COLL-001` | Commission or remuneration to a banker for realising **interest on securities** is deductible. **FA 2026 s.44(a) removes "dividends" from this clause**, consistently with `OS-DIV-DED-2026`. | s.57(i) | **s.93(1)(a)** as substituted by **FA 2026 s.44(a)** | BOTH | dividends + interest | **interest on securities only** | VERIFIED (S2, s.93(1)(a); S3 s.44(a)) |
| `OS-FAMPEN-001` | **Family pension** — defined in the Act as *"a regular monthly amount payable by the employer to a family member of an employee upon the death of such employee"* — carries a deduction of **one-third of such income or ₹25,000, whichever is less, where tax is computed under s.202(1) (new regime)**, and **one-third or ₹15,000, whichever is less, in any other case**. | s.57(iia) | **s.93(1)(d)(i), (ii)** | BOTH, at different amounts | ₹25,000 / ₹15,000 *(NEEDS VERIFICATION)* | **₹25,000 new / ₹15,000 old** | VERIFIED for TY 2026-27 (S2, s.93(1)(d)) |
| `OS-FAMPEN-002` | Family pension is **the one head-specific deduction that is *larger* in the new regime**. | **s.93(1)(d)** | — | — | — | VERIFIED (S2) |
| `OS-GRATUITY-DEATH-001` | Gratuity **received on the death of the employee** is deductible **in full** under this head. | — | **s.93(1)(h)** | BOTH | — | full | VERIFIED (S2, s.93(1)(h)) |
| `OS-COMMUT-001` | Commutation of pension received from a **Schedule VII Sl. 3 fund** is deductible **in full** under this head. | — | **s.93(1)(g)** | BOTH | — | full | VERIFIED (S2, s.93(1)(g)) |
| `OS-GENERAL-001` | Any other **revenue** expenditure laid out **wholly and exclusively** for making or earning such income is deductible. | s.57(iii) | **s.93(1)(e)** | BOTH | — | — | VERIFIED (S2) |
| `OS-NODED-001` | Not deductible: **personal expenses**; interest payable outside India without TDS; salary payable outside India without TDS. | s.58 | **s.94(1)** | BOTH | — | — | VERIFIED (S2, s.94(1)) |
| `OS-LOTTERY-001` | **No deduction of any expenditure or allowance whatsoever** against winnings from lotteries, crossword puzzles, races, card games, gambling or betting. (Horse-owners maintaining racehorses are excepted.) | s.58(4) | **s.94(4), (5)** | BOTH | — | — | VERIFIED (S2, s.94(4),(5)) |
| `OS-GIFT-001` | Sums of money or property received without or for inadequate consideration are chargeable under this head above a **₹50,000** threshold, with exclusions for **relatives**, marriage, will/inheritance, contemplation of death, local authorities, registered non-profits, listed non-transfers, a trust solely for the individual's relatives, and prescribed classes. For immovable property the threshold is the **higher of ₹50,000 or 10% of consideration**. | s.56(2)(x) | **s.92(2)(m), 92(3), 92(4)** | BOTH | — | — | VERIFIED (S2, s.92(2)(m), (3)) |

---

## R18 — Set-off and carry-forward

| Rule ID | Statement | 1961-Act | 2025-Act | Regime | Status |
|---|---|---|---|---|---|
| `SETOFF-INTRA-001` | **Intra-head**: a loss from any source under a head (other than "Capital gains") is set off against income from any other source under the **same** head in the same tax year. | s.70 | **s.108(1)** | BOTH | VERIFIED (S2, s.108(1)) |
| `SETOFF-INTRA-CG-001` | Within capital gains: a **short-term** loss is set off against income from **any other capital asset**; a **long-term** loss only against **long-term** capital gains. | s.70(2), (3) | **s.108(2)(a), (b)** | BOTH | VERIFIED (S2, s.108(2)) |
| `SETOFF-HP-001` | **Inter-head**: a loss under "Income from house property" is set off against income under any other head **to the extent of ₹2,00,000**. | s.71(3A) | **s.109(1)(b)** | OLD only | VERIFIED (S2, s.109(1)(b)) |
| `SETOFF-PGBP-001` | A business loss is **never** set off against "Salaries". | s.71(2A) | **s.109(1)(a)** | BOTH | VERIFIED (S2, s.109(1)(a)) |
| `SETOFF-CG-001` | A **capital-gains loss is never set off against any other head**. | s.71(3) | **s.109(2)** | BOTH | VERIFIED (S2, s.109(2)) |
| `SETOFF-CF-HP-001` | Unabsorbed house-property loss is carried forward and set off **only against house-property income**, for **not more than eight** immediately succeeding tax years. | s.71B | **s.110(1), (2)** | OLD only (see `SETOFF-NEW-001`) | VERIFIED (S2, s.110) |
| `SETOFF-CF-CG-001` | Unabsorbed capital loss is carried forward for **not more than eight** tax years; short-term loss against any capital gains, long-term loss only against long-term. | s.74 | **s.111(1), (2)** | BOTH | VERIFIED (S2, s.111) |
| `SETOFF-NEW-001` | In the **new regime**: no set-off of house-property loss against any other head; no set-off of brought-forward loss or depreciation attributable to a disallowed deduction; and such loss is **deemed to have been given full effect to**, so **no further deduction in any subsequent year**. | s.115BAC(2) | **s.202(2)(b), 202(3)** | NEW | VERIFIED (S2) |

`SETOFF-NEW-002` — **INFERENCE (from `SETOFF-NEW-001`)**: house-property loss in the new regime is
not merely deferred, it is **destroyed**. A regime comparison that treats the new regime's disallowed
HP loss as a carry-forward asset overstates the new regime. This is the mirror of `SAL-PTAX-002` and
matters for the same reason.

---

## R19 — Relief for arrears

| Rule ID | Statement | 1961-Act | 2025-Act | Status |
|---|---|---|---|---|
| `REL-ARREARS-001` | Where total income is assessed at a **higher rate than it otherwise would have been** because of (a) arrear or advance salary, (b) salary for more than twelve months in one tax year, (c) a payment in the nature of "profits in lieu of salary", or (d) arrears of **family pension**, the Assessing Officer shall, **on an application by the assessee**, grant **such relief as may be prescribed**. | s.89 | **s.157(1)** | VERIFIED (S2, s.157(1)) |
| `REL-ARREARS-002` | **No relief** on any income for which a deduction has been claimed under **s.19(1) Table Sl. No. 12** (the VRS deduction) in that or any other tax year. | s.89 proviso | **s.157(2)** | VERIFIED (S2, s.157(2)) |
| `REL-ARREARS-FORM-001` | The relief is computed under **Rules 2026 rule 73**, and the particulars are furnished in **Form No. 39** — the 2025-Act successor to Form 10E. | r.21A + Form 10E | **Rules 2026 r.73(1), (3); Form No. 39** | VERIFIED (S4, r.73(1),(3); Form No. 39 heading *"Form for claiming relief under section 157(1) of the Act in case of receipt of additional salary, or gratuity or ..."*) |
| `REL-ARREARS-002A` | Where the employer is a Government, company, co-operative society, local authority, University, institution, association or body, the employer **may** give effect to the relief when deducting tax — the employee-side projection must therefore be able to mirror it. | s.192(2A) | **s.392(4)(a)(ii); Rules 2026 r.73** | VERIFIED (S2, s.392(4)(a)(ii); S4, r.73) |
| `REL-ARREARS-CALC-001` | The **computation method** — the tables in r.73(1) mapping each kind of receipt to a relief formula — was located but **not read in full**. | r.21A(2)–(6) | **Rules 2026 r.73(1) Table** | **NEEDS VERIFICATION** — re-read S4 r.73 Table, columns A and B, before implementing |

`RECOMMENDATION`: `REL-ARREARS-001` is **out of MVP** (see `P3`) precisely because
`REL-ARREARS-CALC-001` is unverified and the computation requires **recomputing prior years' tax at
prior years' rates** — i.e. loading rule packs for years that are not the current one. That is a
capability `M1` must support, and a feature that must not be attempted before it does.

---

## R20 — TDS on salary

| Rule ID | Statement | 1961-Act | 2025-Act | Status |
|---|---|---|---|---|
| `TDS-SAL-001` | Any person responsible for paying income chargeable under "Salaries" **shall deduct income-tax on the amount payable**, at the time of payment, **at the average rate of income-tax computed on the basis of the rates in force for the tax year**, **on the estimated income of the assessee under this head for that year**. | s.192(1) | **s.392(1)** | VERIFIED (S2, s.392(1)) |
| `TDS-SAL-AVG-001` | "**Average rate**" — the whole method follows from this word. The employer estimates the **full-year** salary income, computes the full-year tax, derives an average rate, and applies it to each payment. It is **not** a slab applied to each month's pay. | s.192(1) | **s.392(1)** | VERIFIED (S2) |
| `TDS-SAL-PERQ-001` | For a **non-monetary perquisite**, the employer may **at its option pay the tax itself** without deducting, computed **at the same average rate on salary income including that perquisite**; that payment is treated as TDS for all purposes. | s.192(1A), (1B) | **s.392(2)(a), (b)** | VERIFIED (S2, s.392(2)) |
| `TDS-SAL-PERQ-002` | Tax so paid by the employer is **excluded from the employee's income** — see `PERQ-TAXPAID-001`. | s.10(10CC) | **Sch. III Table Sl. 10** | VERIFIED (S2) |
| `TDS-SAL-ESOP-001` | An **eligible start-up** paying s.17(1)(d) (ESOP/sweat-equity) income deducts or pays tax at the **rates in force for the tax year in which the security is allotted or transferred**, within the deferred timing of s.289(3). | s.192(1C) | **s.392(3)** | VERIFIED (S2, s.392(3)) |
| `TDS-SAL-12B-001` | The employee **may, at his option**, furnish particulars of: (i) salary from **any other employer** in the tax year; (ii) **s.157 relief**; (iii) **loss under "Income from house property"**; (iv) **income under any other head** (not a loss, except the HP loss); (v) **TDS/TCS** for the same tax year — and those particulars **increase or decrease** the tax to be deducted. | s.192(2), (2A), (2B) | **s.392(4)(a)** | VERIFIED (S2, s.392(4)(a)) |
| `TDS-SAL-12B-002` | The form is **Form No. 122** under **Rules 2026 r.204(1)** — the successor to Forms 12B and 12BB's other-income limb. | Form 12B / 12BB | **r.204(1), Form No. 122** | VERIFIED (S4, r.204(1)) |
| `TDS-SAL-REDUCE-001` | **The tax deductible from salary shall not be reduced in any case except on account of (i) loss under "Income from house property" and (ii) TDS/TCS under other provisions.** Other-head income may only **increase** the deduction. | s.192(2B) proviso | **s.392(4)(b)** | VERIFIED (S2, s.392(4)(b)) |
| `TDS-SAL-12BA-001` | The employer **shall** furnish the employee a statement of **perquisites or profits in lieu of salary and their value** — in the relevant columns of **Form No. 130** where salary ≤ **₹1,50,000**, or in **Form No. 123** where salary exceeds ₹1,50,000. Form No. 123 is the successor to Form 12BA. | s.192(2C) + r.26A | **s.392(5)(a); Rules 2026 r.204(2)(a), (b)** | VERIFIED (S2, s.392(5)(a); S4, r.204(2)) |
| `TDS-SAL-12BB-001` | The employer **shall obtain** from the employee **evidence, proof or particulars of prescribed claims (including a claim for set-off of loss)** — furnished in **Form No. 124** under **Rules 2026 r.205(1)**, the successor to Form 12BB. | s.192(2D) + r.26C | **s.392(5)(b); Rules 2026 r.205(1), Form No. 124** | VERIFIED (S2, s.392(5)(b); S4, r.205(1)) |
| `TDS-SAL-12BB-002` | The evidence required by **r.205(2)**: HRA — landlord name, address and **PAN where aggregate rent exceeds ₹1,00,000**, plus relationship if any; **LTC/LTA** — evidence of expenditure; **house-property interest** — lender name, address and PAN; **Chapter VIII** — evidence of investment or expenditure. | r.26C Table | **r.205(2) Table Sl. 1–4** | VERIFIED (S4, r.205(2)) |
| `TDS-SAL-ADJUST-001` | The employer **may increase or reduce the amount deducted to adjust any excess or deficiency arising from a previous deduction or a failure to deduct during the tax year**. This is the statutory basis for true-up. | s.192(3) | **s.392(5)(c)** | VERIFIED (S2, s.392(5)(c)) |
| `TDS-SAL-FX-001` | Salary payable in foreign currency is converted at a **prescribed** rate for TDS. | s.192(6) + r.26 | **s.392(8); Rules 2026 r.206** | VERIFIED (S2, s.392(8); S4, r.206) |
| `TDS-CERT-001` | The TDS certificate is **Form No. 130** issued under **s.395**, per **Rules 2026 r.215(1) Table Sl. No. 1**. | s.203 + Form 16 | **s.395; r.215(1); Form No. 130** | VERIFIED (S4, Form No. 130 heading) |
| `TDS-CREDIT-001` | TDS paid to the Central Government is treated as payment of tax on the deductee's behalf, and credit is given **for the tax year for which the income is assessable**. Where income is assessable over several years, credit is **spread in the same proportion**. | s.199 + r.37BA | **s.390(5); Rules 2026 r.203(5), (6)** | VERIFIED (S2, s.390(5); S4, r.203(5),(6)) |

### **`TDS-PROJ-001` — what the D5 employee-side projection must reproduce**

`INFERENCE (from `TDS-SAL-001`, `TDS-SAL-AVG-001`, `TDS-SAL-ADJUST-001`, `TDS-SAL-12B-001`, `TDS-SAL-REDUCE-001`)`:
the employer's monthly deduction is a **running re-estimate**, not a schedule. At each payroll run the
employer (a) re-estimates full-year salary income on current knowledge, (b) computes full-year tax,
(c) subtracts what has already been deducted, and (d) spreads the remainder over the remaining
payroll runs. A mid-year revision, a bonus, a declaration changing to a proof, or a previous
employer's Form No. 130 arriving all enter at step (a) and re-shape every remaining month. The
asymmetry in `TDS-SAL-REDUCE-001` is a hard constraint: declared other income can only push the
deduction **up**; only a house-property loss and other TDS can push it **down**. See `G4`.

---

## R21 — Form No. 130 (the Form 16 successor) — field structure

**Status: VERIFIED** (S4, Form No. 130, read in full). This is the authoritative field list for D4
ingestion for TY 2026-27. For TY 2025-26 the document is **Form 16** under Rules 1962 — **NEEDS
VERIFICATION**; its Part B Annexure structure was not opened.

| Rule ID | Part | Contents |
|---|---|---|
| `DOC-F130-A-001` | **Part A** | Certificate No.; last updated on; employer/specified-bank name, address, **PAN**, **TAN**, email, contact; **tax year**; **period with the employer (from/to)**; employee name, address, **PAN** |
| `DOC-F130-B-001` | **Part B** | Per-quarter table: receipt numbers of the original quarterly TDS statements under s.397(3)(b), **amount paid/credited**, **rate of deduction**, **amount of tax deducted**, **amount deposited/adjusted**; plus book-adjustment details (BIN: Form No. 137 receipt number, DDO serial, transfer-voucher date, matching status) and challan details (CIN: BSR code, deposit date, challan serial, TIN 2.0 matching status). **Note 5: the tax deducted shown is the sum of tax, surcharge and cess.** |
| `DOC-F130-C-A` | **Part C Annexure-I** | Row **A**: *"Whether opting out of taxation under section 202(1)? (Yes/No)"* — **the regime flag**. Rows 1–21, listed below |
| `DOC-F130-C-B` | **Part C Annexure-II** | The same shape for a **specified senior citizen** under s.393(1) Table Sl. 8(iii) — pension plus bank interest |

### `DOC-F130-ROWS-001` — Part C Annexure-I, rows in order

| Row | Field | Maps to |
|---|---|---|
| A | Opting out of s.202(1)? | `REG-OPTOUT-SAL-001` |
| 1(a) | Salary as per s.16 | `SAL-DEF-001` |
| 1(b) | Value of perquisites under s.17 (per Form No. 123 where applicable) | `PERQ-DEF-001` |
| 1(c) | Profits in lieu of salary under s.18 (per Form No. 123) | s.18 |
| 1(d) | Total | — |
| 1(e) | **Reported total salary from other employer(s)** | `TDS-SAL-12B-001` |
| 2(a) | Travel concession — Sch. III Sl. 8 | `SAL-LTA-001` |
| 2(b) | House rent allowance — Sch. III Sl. 11 | `SAL-HRA-001` |
| 2(c) | Other special allowances — Sch. III Sl. 12 and 13 | `SAL-ALW-DUTY-001`, `SAL-ALW-PERS-001` |
| 2(d), (e) | Any other s.11 exemption, itemised by section, and its total | Schedule II / III generally |
| 2(f) | Total exemption = 2(a)+2(b)+2(c)+2(e) | — |
| 3 | Salary from current employer = 1(d) − 2(f) | — |
| 4(a) | Tax on employment — s.19(1) Sl. 1 | `SAL-PTAX-001` |
| 4(b) | Standard deduction — s.19(1) Sl. 2 | `SAL-STDDED-001/002` |
| 4(c) | Gratuity — Sl. 3, 4, 5, 6 | `RET-GRAT-*` |
| 4(d) | Commuted pension — Sl. 7, 8, 9 | `RET-COMMUT-*` |
| 4(e) | Workman compensation — Sl. 10, 11 | `RET-RETRENCH-*` |
| 4(f) | VRS — Sl. 12 | `RET-VRS-001` |
| 4(g) | Leave encashment — Sl. 13, 14 | `RET-LEAVE-*` |
| 5 | Total s.19(1) deductions | — |
| 6 | **Income under "Salaries" = 3 + 1(e) − 5** | — |
| 7(a) | Other-head income reported under s.392(4)(a)(iv) | `TDS-SAL-12B-001` |
| 7(b) | House-property loss reported under s.392(4)(a)(iii) | `SETOFF-HP-001` |
| 8 | Total other income = 7(a) − 7(b) | — |
| 9 | **Gross total income = 6 + 8** | `DED-GTI-DEF-001` |
| 10(a)–(g) | Chapter VIII, **gross amount** and **deductible amount**: s.123; s.124(3); **s.124(1)(a)**; s.126; s.129; s.125(1); s.125(2) | `DED-80C-001`, `DED-NPS-SELF-001`, `DED-NPS-EMP-001`, `DED-80D-001`, `DED-80E-001`, `DED-AGNIVEER-001` |
| 10(h)–(k) | s.133 (donations) and s.153 (deposit interest) with **gross / qualifying / deductible**; plus free-text "any other provision" rows | `DED-80G-001`, `DED-80TTA-001` |
| 11 | Aggregate deductible under Chapter VIII | `DED-GTI-CAP-001` |
| 12 | **Total taxable income = 9 − 11** | — |
| 13 | Tax on total income | `RATE-SLAB-*` |
| 14 | **Rebate under s.156** | `REBATE-*` |
| 15 | Surcharge | `RATE-SUR-*` |
| 16 | Health and education cess @ 4% | `RATE-CESS-001` |
| 17 | Tax payable = 13 + 15 + 16 − 14 | see `REBATE-ORDER-001` |
| 18 | **Relief under s.157** (attach details) | `REL-ARREARS-001` |
| 19 | TDS as per Form No. 122 | `TDS-SAL-12B-001` |
| 20 | TCS as per Form No. 122 | `TDS-SAL-12B-001` |
| 21 | **Net tax payable = 17 − 18 − 19 − 20** | — |

### Notes that shape ingestion

- `DOC-F130-MULTI-001` — **FACT (S4, Form No. 130 Note 7)**: where the assessee had **more than one
  employer**, *"each of the employers shall issue Part A and Part B of the certificate ... pertaining
  to the period for which such assessee was employed with each of the employer"*.
- `DOC-F130-ANNEX-001` — **FACT (S4, Part C Note 1)**: Part C Annexure-I *"may be issued by each of
  the employers **or the last employer at the option of the assessee**"*.
- `INFERENCE (from both)`: a multi-employer year yields **N Part A/B blocks and either N or 1 Part C
  blocks**. Ingestion must key on `(TAN, period-from, period-to)` and must **not** assume one document
  per tax year or that Part C totals reconcile to the sum of the Part B blocks.
- `DOC-F130-PREFILL-001` — **FACT (S4, Note 8)**: *"Some of the information in the Form would be
  pre-filled to the extent possible."* Layout will drift; a coordinate-based parser will break.
- `DOC-F130-TAXSUM-001` — **FACT (S4, Note 5)**: Part B's "tax deducted" is **tax + surcharge + cess**
  combined, while Part C rows 13, 15 and 16 are separate. Reconciliation must compare like with like.

---

## R22 — ITR form applicability (TY 2026-27, Rules 2026 rule 164)

> **Amended by `21-itr-forms.md`.** These rows remain the **legal record**; the executable form is
> `itr-forms.json` in each pack, with eligibility expressed in the `Condition` algebra. Two
> corrections from that pass: the form set is **nine** forms (ITR-1..7 plus **ITR-V** and **ITR-UN**),
> and `FACT (S4, r.164(4))` ITR-2 is for *"an individual **not being an individual to whom sub-rule (2)
> applies**"* — so the forms are **mutually exclusive** and selection is a deterministic cascade, not
> a "pick the simplest eligible form" choice. `FACT (S4, r.164(14))`: an earlier year's return uses
> *"the appropriate form as applicable in that tax year"*, so historical form sets stay live.

**Status: VERIFIED** (S4, r.164(1)–(8), read directly). For TY 2025-26 the corresponding rule is
Rules 1962 rule 12 — **NEEDS VERIFICATION**.

| Rule ID | Statement |
|---|---|
| `ITR-SCOPE-001` | r.164(1): the rule governs returns under s.263(1), s.268(1)(a) or s.280 **relating to the tax year commencing on 1 April 2026** — i.e. the form set is **per tax year** and is reissued annually |
| `ITR-1-ELIG-001` | **ITR-1 (SAHAJ)** is for an **individual, resident other than not ordinarily resident**, whose total income includes income under: (a) **Salaries** or family pension as defined in s.93(1)(d); (b) **House property**, where the assessee **does not own more than two** house properties **and** has no brought-forward or to-be-carried-forward loss under the head; (c) **Other sources**, except lottery winnings and racehorse income, with no loss under the head; (d) **Capital gains**, where the assessee has **only long-term capital gains under s.198 not exceeding ₹1,25,000** and no brought-forward or carried-forward loss under the head |
| `ITR-1-BAR-001` | ITR-1 is **barred** where the person: has assets or a financial interest outside India; has signing authority in a foreign account; has income from a source outside India; has income apportionable under s.10; **has claimed a deduction under s.93 other than s.93(1)(d)** (i.e. any other-sources deduction beyond family pension); is a **director** in any company; **held any unlisted equity share** at any time in the tax year; is assessable on income on which tax was deducted in another person's hands; has claimed relief under s.159 or deduction under s.160; **has agricultural income exceeding ₹5,000**; **has total income exceeding ₹50,00,000**; is a person whose tax was deducted under s.393(3) Table Sl. 5; had payment or deduction **deferred** under s.391(2) or s.392(3) (**the ESOP deferral**); or has income taxed under Part A of Chapter XIII |
| `ITR-2-ELIG-001` | **ITR-2** is for an individual **to whom r.164(2) does not apply**, or an HUF, where total income **does not include business or professional income** |
| `ITR-3-ELIG-001` | **ITR-3** is for an individual or HUF **having** business or professional income |
| `ITR-4-ELIG-001` | **ITR-4 (SUGAM)** is for a resident individual/HUF/non-LLP firm with **presumptive** business income under s.58, and LTCG under s.198 not exceeding ₹1,25,000, subject to bars mirroring `ITR-1-BAR-001` plus **owning more than two house properties** and **any brought-forward or carried-forward loss under any head** |

### `ITR-D3-001` — what this means for the D3 scope

`INFERENCE (from `ITR-1-ELIG-001`, `ITR-1-BAR-001`, and D3)`: the D3 user — salary, house property,
other sources, plus capital gains fed from `portfolio` — sits on the **ITR-1/ITR-2 boundary**, and
which side they fall on is **decided by facts the product already holds**:

| Trigger | Pushes to | Source of truth |
|---|---|---|
| Total income > ₹50,00,000 | ITR-2 | computation |
| Owns more than two house properties | ITR-2 | `M3` property list |
| Any HP loss carried forward or to be carried forward | ITR-2 | `SETOFF-CF-HP-001` |
| **Short-term** capital gains of any amount | ITR-2 | `portfolio` feed, `CapitalGainsType.SHORT_TERM` |
| **Long-term** gains under s.198 **exceeding ₹1,25,000** | ITR-2 | `portfolio` feed |
| Any capital loss brought forward or to be carried forward | ITR-2 | `portfolio` feed |
| **Held any unlisted equity share** at any time in the year | ITR-2 | **not currently held anywhere** |
| **Speculative (intraday) income** — business income | **ITR-3** | `portfolio` `CapitalGainsType.SPECULATIVE` |
| Claimed s.153 (deposit-interest) deduction | ITR-2 | computation — see note |
| Agricultural income > ₹5,000 | ITR-2 | profile |
| Director in a company | ITR-2 | profile |

**`ITR-1-BAR-002` — STATUS UNCLEAR, and it matters.** `ITR-1-BAR-001` bars ITR-1 where the person
*"has claimed deduction under section 93, other than deduction claimed under clause (d) of
sub-section (1)"*. **s.93 is the *other-sources* deductions section**, whose clause (d) is family
pension — so on the face of the text a s.153 (80TTA/80TTB) claim, which is a **Chapter VIII**
deduction and not a s.93 one, does **not** bar ITR-1. Under Rules 1962 rule 12 the analogous bar is
framed on **s.57** other than 57(iia), which is the same construction. `RECOMMENDATION`: implement
the bar exactly as written — s.93 deductions other than s.93(1)(d) — and flag it for professional
review rather than broadening it by analogy.

**`ITR-SPEC-001` — INFERENCE (from `portfolio`'s own model)**: `CapitalGainsType.SPECULATIVE` is
documented in the codebase as *"Intraday equity. Speculative business income taxed at slab rate, not
capital gains at all"* (`backend/src/main/java/com/thiru/wealthlens/portfolio/dto/enums/CapitalGainsType.java:34-36`,
read this session). **Speculative income is business income**, so any of it pushes the user to
**ITR-3** and outside the D3 income-head scope entirely. The ITR advisor must say so rather than
silently classifying it as capital gains.

---

## R23 — Advance tax

| Rule ID | Statement | 1961-Act | 2025-Act | TY 2026-27 | Status |
|---|---|---|---|---|---|
| `ADV-LIABLE-001` | Advance tax is payable during the financial year in respect of the **current income** — the total income which would be chargeable for that tax year. | s.207 | **s.403(1), (2)** | applies | VERIFIED (S2, s.403) |
| `ADV-SENIOR-001` | **Not** payable by a **resident individual who (a) has no income under "Profits and gains of business or profession" and (b) is sixty years or more at any time during the tax year.** | s.207(2) | **s.403(3)** | applies | VERIFIED (S2, s.403(3)) |
| `ADV-THRESHOLD-001` | Payable where the amount of such tax computed for the year is **₹10,000 or more**. | s.208 | **s.404** | ₹10,000 | VERIFIED (S2, s.404) |
| `ADV-COMPUTE-001` | `A = B − C` where `B` = income-tax on the estimated current income at the **rates in force in the financial year** and `C` = tax **deductible or collectible at source** during that year on income taken into account in the estimate, provided the payer has actually deducted/collected. | s.209 | **s.405(1)** | applies | VERIFIED (S2, s.405(1)) |
| `ADV-INSTAL-001` | Four instalments: **on or before 15 June — not less than 15%**; **15 September — not less than 45%**; **15 December — not less than 75%**; **15 March — the whole amount**, each reduced by amounts already paid. | s.211 | **s.408(1) Table** | applies | VERIFIED (S2, s.408(1)) |
| `ADV-MAR31-001` | Any amount paid by way of advance tax **on or before 31 March** is treated as advance tax paid during that financial year. | s.211 proviso | **s.408(3)** | applies | VERIFIED (S2, s.408(3)) |
| `ADV-SELF-001` | The assessee pays **on his own accord** on his own estimate of current income and may **increase or reduce** the remaining instalments to match a revised estimate. | s.210(1), (2) | **s.406(1), (2), (3)** | applies | VERIFIED (S2, s.406) |

### `ADV-SAL-001` — why this matters for a salaried user

`INFERENCE (from `ADV-COMPUTE-001` and `TDS-SAL-REDUCE-001`)`: salary TDS is subtracted at `C`, so a
purely salaried user with no other income normally has **no advance-tax liability**. But D3 adds
interest, dividends and **capital gains from `portfolio`**, and `TDS-SAL-REDUCE-001` forbids the
employer from reducing salary TDS on account of other-head income — the employee may only *increase*
it by declaring under `TDS-SAL-12B-001`. So the user has exactly two ways to cover tax on other
income: **declare it to the employer** (raising salary TDS) or **pay advance tax**. That choice, with
its instalment calendar, is a genuine advisory output — see `A3`.

---

## R24 — Verdicts on the placeholder's hypotheses

| # | Hypothesis | Verdict | Rule IDs | Evidence |
|---|---|---|---|---|
| **H-1** | Surcharge bands seeded at ₹5 Cr+; no surcharge marginal relief | **CONFIRMED (both limbs)** | `RATE-SUR-OLD-2025`, `RATE-SUR-NEW-2025`, `RATE-SUR-OLD-2026`, `RATE-SUR-NEW-2026`, `RATE-SUR-MR-001` | Entry threshold is ₹50,00,000 in all four; ₹5 Cr is only the 37% band and **that band does not exist in the new regime in either year**. Marginal relief is stated four separate times in FA 2026 |
| **H-2** | Rules-2026 figures under a TY 2025-26 label | **CONFIRMED for every listed value** | `SAL-HRA-METRO-2026`, `PERQ-MEAL-2026`, `PERQ-GIFT-2026`, `SAL-ALW-CEA-2026`, `SAL-ALW-HOSTEL-2026`, `PERQ-CAR-2026` | Each was read directly from the Income-tax Rules 2026 (G.S.R. 198(E)), which apply **from TY 2026-27**. The 8-city HRA list, ₹200/meal, ₹15,000 gift, ₹3,000 CEA, ₹9,000 hostel, ₹5,000/₹7,000 car + ₹3,000 chauffeur are all Rules-2026 values |
| **H-3** | 87A marginal relief applied in the old regime | **CONFIRMED as an error** | `REBATE-OLD-001`, `REBATE-NEW-MR-001` | s.156(1) (old-regime limb) has **no** marginal-relief clause; s.156(2), which does, opens *"where ... chargeable to tax under section 202(1)"* |
| **H-4** | HRA salary excluded DA | **CONFIRMED as an error** | `SAL-HRA-004`, `SAL-DEF-HRA-001` | r.279(2)(b): *"'salary' includes dearness allowance, if provided for under the terms of employment"* |
| **H-5** | Slab bands one rupee short; no ₹10 rounding | **CONFIRMED (both limbs)** | `RATE-SLAB-BAND-001`, `RATE-ROUND-002` | The statutory bands partition the rupee line; `400001` is a label, not an arithmetic bound. s.516 mandates ₹10 rounding of total income and of the amount payable |
| **H-6** | No employer-contribution aggregate cap; no tax on interest on the excess | **CONFIRMED (both limbs)** | `PF-AGGCAP-001`, `PF-AGGCAP-002`, `PF-RPF12-001`, `PF-EEINT-001` | s.17(1)(h) caps the aggregate at ₹7,50,000; s.17(1)(i) charges the accretion on the excess. Two further independent charges exist |
| **H-7** | Employee PF not in 80C; no 80CCE | **CONFIRMED (both limbs)** | `DED-80C-001`, `DED-MAP-021`, `PF-EEDED-001` | Schedule XV para 1(f) puts employee RPF contribution inside the s.123 ₹1,50,000 ceiling; the 80CCE aggregate is structural in s.123 |
| **H-8** | LTA exempted in full | **CONFIRMED as an error** | `SAL-LTA-002`, `SAL-LTA-004`, `SAL-LTA-005`, `SAL-LTA-BLOCK-001`, `SAL-LTA-NEW-001` | Four independent caps: actual expenditure, fare class by shortest route, two journeys per four-calendar-year block, and unavailability in the new regime |
| **H-9** | Books, phone and uniform exempt at 100%, uncapped | **CONFIRMED as an error** | `SAL-ALW-DUTY-001`, `SAL-ALW-DUTY-LIST-2026`, `SAL-ALW-BOOKS-001`, `PERQ-PHONE-2026` | Uniform is a Sch. III Sl. 12 allowance, exempt only to the extent **actually incurred** on duty-related expenditure. Books and phone appear in **neither** prescribed list; phone is a perquisite question |
| **H-10** | Transport allowance ₹19,200 for everyone | **CONFIRMED, and larger than stated** | `SAL-ALW-TRANSPORT-DIS-2026` | The only transport allowance in the Rules-2026 table is for a **blind / deaf-and-dumb / orthopaedically handicapped** employee, at **₹15,000 (metro) or ₹8,000 (other) per month plus DA**. There is no general transport allowance |
| **H-11** | 80D a single flat cap | **CONFIRMED as an error** | `DED-80D-001` … `DED-80D-011` | Two independent ₹50,000 ceilings, a ₹5,000 preventive sub-limit, a senior-citizen substitution, a mutual-exclusivity rule and a payment-mode rule |
| **H-12** | 24(b) with no self-occupied vs let-out distinction | **CONFIRMED as an error** | `HP-CAP-SELFOCC-001`, `HP-DED-INT-001`, `HP-NEW-001`, `HP-CAP-SELFOCC-002` | The distinction drives annual value, the cap, new-regime availability and pre-construction treatment — four rules |
| **H-13** | Professional tax across the regimes | **CONFIRMED as a real distinction** | `SAL-PTAX-001`, `SAL-PTAX-002` | Entire amount in the old regime; **disallowed** in the new by s.202(2)(a)(iv) |
| **H-14** | Retirement-benefit limits | **CONFIRMED as a risk** | `RET-GRAT-OTHER-001`, `RET-RETRENCH-001`, `RET-LEAVE-OTHER-001`, `RET-GRAT-AGG-001`, `RET-LEAVE-AGG-001`, `RET-VRS-002` | Several ceilings are **notification-driven**, not in the Act, and the aggregate/exclusivity rules are cross-year and cross-employer |
| **H-15** | Net take-home included notional perquisites | **CONFIRMED as an error** — and it is a definitional error, not a tax one | `PERQ-DEF-001`, and `A4` | Perquisites such as `PERQ-CAR-2026` are **valuations of a benefit**, not cash. `RECOMMENDATION`: never present a "take-home" figure that includes a non-monetary perquisite value. Separate `taxableIncomeComponents` from `cashComponents` in the model (`M2`) so the two cannot be summed by accident |
| **H-16** | Regime-choice rules for salaried users | **CONFIRMED as a real risk area** | `REG-DEFAULT-001`, `REG-OPTOUT-SAL-001`, `REG-OPTOUT-FORM-001`, `REG-OPTOUT-YEARLY-001`, `REG-OPTOUT-LATE-001` | New regime is the default; the salaried opt-out rides on the return; for TY 2026-27 there is **no separate form** (r.136); the lock-in rules apply only to business income; the belated-return position is **STATUS UNCLEAR** |

**Refuted: none.** Every hypothesis survived contact with the statutory text. H-2, H-10 and H-6 came
out **worse** than stated; H-9 and H-16 came out more nuanced.

---

## Diff table — TY 2025-26 versus TY 2026-27

Rows marked ⚠ are where a single rule pack copied forward would be **wrong**.

| Area | TY 2025-26 (1961 Act) | TY 2026-27 (2025 Act) | Same? |
|---|---|---|---|
| Governing Act | Income-tax Act 1961 | **Income-tax Act 2025** | ⚠ different Act, different section numbers throughout |
| Period concept | previous year + assessment year | **tax year** only | ⚠ |
| Rate source | FA 2026 s.2 + First Schedule **Part I-A** | FA 2026 s.3 + First Schedule **Part I-B** | ⚠ |
| Rules | Income-tax Rules **1962** | Income-tax Rules **2026** (G.S.R. 198(E)) | ⚠ |
| New-regime slabs | 4/8/12/16/20/24 L — Nil/5/10/15/20/25/30 | **identical** | ✓ |
| Old-regime slabs | 2.5/5/10 L — Nil/5/20/30, age variants | **identical** | ✓ |
| Rebate, new regime | ₹60,000 up to ₹12,00,000 + marginal relief | **identical** | ✓ |
| Rebate, old regime | ₹12,500 up to ₹5,00,000, **no** marginal relief | **identical** | ✓ |
| Surcharge, old | 10/15/25/37 at 50L/1Cr/2Cr/5Cr | **identical** | ✓ |
| Surcharge, new | 10/15/25 at 50L/1Cr/2Cr, **no 37%** | **identical** | ✓ |
| Surcharge marginal relief | `Wo/To = U + V` | `Wn/Tn = U + V` | ✓ in form |
| Cess | 4% | 4% | ✓ |
| Rounding | ss.288A **and** 288B (two sections) | **s.516** (one section, both amounts) | ✓ in effect |
| Standard deduction | ₹75,000 new / ₹50,000 old | **identical** | ✓ |
| **Entertainment allowance** | s.16(ii), Govt employees | **no counterpart in the 2025 Act** | ⚠ |
| **HRA metro list** | Rules 1962 r.2A — **unverified, believed 4 cities** | **8 cities** (r.279) | ⚠ |
| **Children education allowance** | Rules 1962 r.2BB — unverified | **₹3,000/month/child** | ⚠ |
| **Hostel allowance** | Rules 1962 r.2BB — unverified | **₹9,000/month/child** | ⚠ |
| **Transport allowance (disabled)** | Rules 1962 r.2BB — unverified | **₹15,000 / ₹8,000 per month + DA** | ⚠ |
| **Meal perquisite** | Rules 1962 r.3 — unverified | **₹200 per meal** | ⚠ |
| **Gift perquisite** | Rules 1962 r.3 — unverified | **below ₹15,000** | ⚠ |
| **Car perquisite** | Rules 1962 r.3 — unverified | **₹5,000 / ₹7,000 + ₹3,000 chauffeur** | ⚠ |
| **Specified-employee threshold** | ₹50,000 until FA 2025 s.9(a) made it prescribed **w.e.f. TY 2025-26** | prescribed | ⚠ unverified in **both** years |
| LTA block | four calendar years from 2022 → block **2022-2025** | block **2026-2029** | ⚠ the block rolls over exactly between the two years |
| **Dividend deduction** | 20% of dividend income as interest expense | **nil** (FA 2026 s.44(b)) | ⚠ |
| **Self-occupied pre-construction interest** | position unverified | **inside** the ₹2,00,000 cap (FA 2026 s.38) | ⚠ |
| Stock-in-trade nil AV | "nil for two years" | "nil **up to** two years" (FA 2026 s.37) | ✓ in effect |
| Employer-contribution aggregate cap | ₹7,50,000 (unverified) | ₹7,50,000 | ✓ believed |
| Employee-PF interest thresholds | ₹5,00,000 / ₹2,50,000 (unverified) | ₹5,00,000 / ₹2,50,000 | ✓ believed |
| 80C / s.123 ceiling | ₹1,50,000, 80CCE aggregate | ₹1,50,000, **aggregate integral to s.123** | ✓ in effect |
| **Regime opt-out mechanism** | Form 10-IEA (unverified) | **in the return itself** — r.136, **no separate form** | ⚠ |
| **TDS certificate** | Form 16 | **Form No. 130** | ⚠ |
| **Perquisite statement** | Form 12BA | **Form No. 123** | ⚠ |
| **Previous-employer particulars** | Form 12B / 12BB | **Form No. 122** | ⚠ |
| **Claim evidence** | Form 12BB | **Form No. 124** | ⚠ |
| **Arrears relief** | s.89, Form 10E | **s.157, Form No. 39, r.73** | ⚠ |
| **ITR rule** | Rules 1962 r.12 | **Rules 2026 r.164** | ⚠ |
| ITR-1 total-income ceiling | ₹50,00,000 (unverified) | ₹50,00,000 | ✓ believed |
| ITR-1 LTCG allowance | unverified | **s.198 LTCG ≤ ₹1,25,000** | ⚠ |
| Return due date, salaried | **31 July** of the AY (FA 2026 s.5) | **31 July** of the FY succeeding the tax year (FA 2026 s.66) | ✓ |
| Revised return window | before **end of the assessment year** or completion of assessment | **within 12 months from the end of the tax year** or completion of assessment | ⚠ |
| Advance-tax instalments | 15/45/75/100% | **identical** | ✓ |

### The shape of the diff

`INFERENCE`: **rates and thresholds barely moved; everything around them was renumbered, and the
Rules moved a great deal.** Of the 24 ⚠ rows, **19 are re-identification** (a different section, rule
or form number for the same idea) and **5 are substantive** (HRA metro list, the four allowance and
perquisite values, dividend deduction, pre-construction interest, entertainment allowance).

`RECOMMENDATION`: this is the single most important input to the architecture. A design that versions
**rule identity** — "which provision, in which instrument, says this, for this year" — survives the
Act change as a data edit. A design that versions **only values** does not, because most of what
changed was identity, not value. See `M1` and `M2`.
