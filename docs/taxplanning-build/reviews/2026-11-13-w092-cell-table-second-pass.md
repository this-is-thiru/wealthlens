# W-092 — the cell table, second pass, drafted for the owner's adoption (TY 2025-26 ITR-1 and ITR-2)

This continues `reviews/2026-11-13-w092-cell-table.md` (adopted by Q1a – Q4a, D-654), in the same format. **Drafted by
Claude.** A cell becomes data only once the owner adopts it.

Paths and labels are from `sources/2026-11-13-itr-schedules-ty-2025-26.md`: ITR-1 271423 pp.17 – 18; ITR-2 271435
pp.41 – 42, 52 – 54, 58, 67 – 70. Rows are read from the TY 2025-26 rule files' `provision1961`. Probe (throwaway,
deleted): Anand under `FIRST_SCHEDULE_RATES` leaves one DEDUCTION line per Chapter VI-A row, its figure the deduction
allowed:
- `DED-HEALTH-FAMILY-SENIOR` 40,000 → 40,000;
- `DED-DEPOSIT-INTEREST-SENIOR` 1,04,000 → 50,000;
- `chapterviii` 90,000.

A RELIEF, INTEREST or FEE line's figure is the amount itself (`REBATE-RESIDENT-INDIVIDUAL`, `INT-*`, `FEE-LATE-RETURN`).

## Q9 — withdrawn at the build (the first pass was right)

Q9 said `AMOUNT_PAYABLE` and `REFUND` leave out interest and the s.234F fee, because `tax.balance` does
(`BalanceNode.java:27`). But `round.netpayable` rounds `tax.balance` **with** `interest.and.fees`
(`RoundNetPayableNode.java:18, 24`), so both cells were correct as shipped in 3b5f0003. The claim came from reading
`BalanceNode` alone. A probe at the build proved it: re-pointing the payable at `round.netpayable` changed nothing.

The cells stay on `round.netpayable`, and Q9a's rounding mechanism is not built, since it would have no caller.
`TAX_LIABILITY`, `TOTAL_LIABILITY` and `INTEREST_AND_FEE` are still placed (ITR-1 D5, D11; ITR-2 B-TTI 12 – 14). A
late-filing case shows the payable equals D11 rounded under s.288B.

## A. Chapter VI-A by section — ITR-1 Part C, ITR-2 Schedule VI-A

| Cell | ITR-1 | ITR-2 | Section | Source (proposed) |
|---|---|---|---|---|
| `DED_80C` | C.80C | VI-A.a | 80C | see Q5 (`DED-SCHEDULE-XV` is one line for 80C, 80CCC and 80CCD(1) under the s.80CCE ceiling) |
| `DED_80CCC` | C.80CCC | VI-A.b | 80CCC | see Q5 |
| `DED_80CCD_1` | C.80CCD(1) | VI-A.c | 80CCD(1) | see Q5 |
| `DED_80CCD_1B` | C.80CCD(1B) | VI-A.d | 80CCD(1B) | FromRules [`DED-NPS-OWN`] |
| `DED_80CCD_2` | C.80CCD(2) | VI-A.e | 80CCD(2) | FromRules [`DED-NPS-EMPLOYER-GOVT`, `DED-NPS-EMPLOYER-OTHER`, `DED-NPS-EMPLOYER-NEW-TAX-REGIME`] |
| `DED_80CCH` | C.80CCH | VI-A.ua | 80CCH | FromRules [`DED-AGNIVEER-OWN`, `DED-AGNIVEER-GOVERNMENT`] |
| `DED_80D` | C.80D | VI-A.f | 80D | FromRules [`DED-HEALTH-FAMILY`, `DED-HEALTH-FAMILY-SENIOR`, `DED-HEALTH-PARENTS`, `DED-HEALTH-PARENTS-SENIOR`] |
| `DED_80DD` | C.80DD | VI-A.g | 80DD | FromRules [`DED-DISABLED-DEPENDANT`, `DED-DISABLED-DEPENDANT-SEVERE`] |
| `DED_80DDB` | C.80DDB | VI-A.h | 80DDB | FromRules [`DED-SPECIFIED-DISEASE`, `DED-SPECIFIED-DISEASE-SENIOR`] |
| `DED_80E` | C.80E | VI-A.i | 80E | FromRules [`DED-EDUCATION-LOAN`] |
| `DED_80EE` | C.80EE | VI-A.j | 80EE | FromRules [`DED-HOUSE-LOAN-EARLIER`] |
| `DED_80EEA` | C.80EEA | VI-A.k | 80EEA | FromRules [`DED-HOUSE-LOAN`] |
| `DED_80EEB` | C.80EEB | VI-A.l | 80EEB | FromRules [`DED-ELECTRIC-VEHICLE-LOAN`] |
| `DED_80G` | C.80G | VI-A.m | 80G | FromRules [`DED-DONATION-WHOLE`, `DED-DONATION-HALF`] |
| `DED_80GG` | C.80GG | VI-A.n | 80GG | FromRules [`DED-RENT-PAID`] |
| `DED_80GGA` | C.80GGA | VI-A.o | 80GGA | ManualEntry ("not computed") |
| `DED_80GGC` | C.80GGC | VI-A.p | 80GGC | ManualEntry ("not computed") |
| `DED_80QQB` | — | VI-A.q | 80QQB | ManualEntry ("not computed") |
| `DED_80RRB` | — | VI-A.r | 80RRB | ManualEntry ("not computed") |
| `DED_80TTA` | C.80TTA | VI-A.s | 80TTA | FromRules [`DED-SAVINGS-INTEREST`] |
| `DED_80TTB` | C.80TTB | VI-A.t | 80TTB | FromRules [`DED-DEPOSIT-INTEREST-SENIOR`] |
| `DED_80U` | C.80U | VI-A.u | 80U | FromRules [`DED-DISABILITY`, `DED-DISABILITY-SEVERE`] |
| `DEDUCTIONS_VI_A` (exists) | C1 | VI-A.v | Total | FromNodes `chapterviii` |

Only rows that leave the section's DEDUCTION line are listed. Rows that measure a limb or a limit are left out:
- 80D: `-LIMB`, `-INSURANCE-PAID`, `-MEDICAL-LIMB`, `-CHECKUP`, `-CASH-PERMITTED`, `-SPREAD`;
- 80DD: `DED-DEPENDANT`, `-OWN-CLAIM`;
- 80G: `-QUALIFYING`, `-UNREPORTED`, `-CASH`.

Summing those too would count the deduction twice. **At the build**, each section's cell is proven against
`chapterviii`: a persona per section, with the section cells adding up to the total.

## B. ITR-1 Part C totals and C3(a)

| Cell | ITR-1 | Label | Source |
|---|---|---|---|
| `TOTAL_INCOME` (exists) | C2 | Total income (B4 − C1) | FromNodes `totalincome` |
| `LTCG_112A_SALE` | C3(a).i | Total sale consideration | ManualEntry — see Q10 |
| `LTCG_112A_COST` | C3(a).ii | Total cost of acquisition | ManualEntry — see Q10 |
| `LTCG_112A_GAIN` | C3(a).iii | Long term capital gains as per sec 112A | FromCapitalGainsPart `LONG_TERM_LISTED_EQUITY` |

## C. ITR-1 Part D

| Cell | ITR-1 | Label | Source |
|---|---|---|---|
| `TAX_ON_TOTAL_INCOME` | D1 | Tax payable on total income | SumOf(`TAX_AT_NORMAL_RATES`, `TAX_AT_SPECIAL_RATES`) — Q7 |
| `TAX_AFTER_REBATE` | D3 | Tax after rebate | SumOf(D1, −`TAX_REBATE_87A`) |
| `TAX_AND_CESS` | D5 | Total tax and cess | SumOf(D3, `TAX_CESS`) |
| `RELIEF_89` | D6 | Relief u/s 89 | ManualEntry ("not computed; Form 10E") |
| `INTEREST_234A` | D7 | Interest u/s 234A | FromRules [`INT-LATE-RETURN`] |
| `INTEREST_234B` | D8 | Interest u/s 234B | FromRules [`INT-ADVANCE-SHORTFALL`] |
| `INTEREST_234C` | D9 | Interest u/s 234C | FromRules [`INT-DEFERMENT`] (its instalment lines summed) |
| `FEE_234I` | D10(a) | Fee for furnishing revised return (s.234-I) | ManualEntry (revised returns are OQ-40's) |
| `TAX_FEE_AND_INTEREST` | D11 | Total tax, fee and interest | SumOf(D5, D7, D8, D9, D10). D6 and D10(a) are not computed, so they take nothing from it, as with `SALARY_NET` |

## D. ITR-2 Part B-TI, items 3 – 8 and 10

| Cell | ITR-2 | Label | Source |
|---|---|---|---|
| `STCG_111A` | B-TI.3a.i | Short-term chargeable @ 20% | FromCapitalGainsPart `SHORT_TERM_LISTED_EQUITY` |
| `STCG_30` | B-TI.3a.ii | Short-term chargeable @ 30% | ManualEntry ("not computed") |
| `STCG_APPLICABLE_RATE` | B-TI.3a.iii | Short-term chargeable at applicable rate | FromCapitalGainsPart `SHORT_TERM_SLAB` |
| `STCG_DTAA` | B-TI.3a.iv | Short-term at DTAA rates | ManualEntry |
| `STCG_TOTAL` | B-TI.3a.v | Total short-term (nil if loss) | SumOf(3a.i, 3a.iii), NIL_IF_LOSS |
| `LTCG_12_5` | B-TI.3b.i | Long-term chargeable @ 12.5% | see Q8 (first pass placed `LTCG_112A` only) |
| `LTCG_DTAA` | B-TI.3b.ii | Long-term at DTAA rates | ManualEntry |
| `LTCG_TOTAL` | B-TI.3b.iii | Total long-term (nil if loss) | SumOf(3b.i), NIL_IF_LOSS |
| `CG_SHORT_AND_LONG` | B-TI.3c | Sum of short-term / long-term (nil if loss) | SumOf(3a.v, 3b.iii), NIL_IF_LOSS |
| `CG_115BBH` | B-TI.3d | Gain chargeable @ 30% u/s 115BBH | ManualEntry (virtual digital assets are not computed) |
| `CG_TOTAL` | B-TI.3e | Total capital gains | SumOf(3c). 3d is not computed |
| `OS_TOTAL` | B-TI.4d | Total other sources (nil if loss) | FromNodes `othersources.income`, NIL_IF_LOSS. 4b and 4c are not computed |
| `HEADS_TOTAL` | B-TI.5 | Total of head-wise income | SumOf(1, 2, 3e, 4d) |
| `SETOFF_CURRENT_YEAR` | B-TI.6 | Losses of current year set off | FromRules [`SETOFF-ACROSS-HEADS`, `SETOFF-HOUSE-PROPERTY`] |
| `BALANCE_AFTER_SETOFF` | B-TI.7 | Balance after set-off (5 − 6) | SumOf(5, −6) |
| `SETOFF_BROUGHT_FORWARD` | B-TI.8 | Brought-forward losses set off | FromRules [`SETOFF-CARRIED-CAPITAL-GAINS`, `SETOFF-CARRIED-HOUSE-PROPERTY`] |
| `SPECIAL_RATE_INCOME` | B-TI.10 | Income at special rates included in 9 | SumOf(3a.i, 3b.i) |

## E. ITR-2 Part B-TTI, items 2b – 15

| Cell | ITR-2 | Label | Source |
|---|---|---|---|
| `TAX_AT_SPECIAL_RATES` | B-TTI.2b | Tax at special rates | Q7 |
| `REBATE_AGRICULTURAL` | B-TTI.2c | Rebate on agricultural income | ManualEntry (agricultural income is a declared amount, not computed into tax) |
| `TAX_ON_TOTAL_INCOME` | B-TTI.2d | Tax payable on total income | as ITR-1 D1 (one cell; 2c not computed) |
| `TAX_AFTER_REBATE` | B-TTI.4 | Tax payable after rebate | as ITR-1 D3 |
| `TAX_SURCHARGE` | B-TTI.5 (5iv) | Surcharge | FromNodes `tax.surcharge` (5i – 5iii's split: ManualEntry) |
| `GROSS_TAX_LIABILITY` | B-TTI.7 | Gross tax liability (4 + 5iv + 6) | SumOf(4, 5, 6) |
| `GROSS_TAX_PAYABLE` | B-TTI.8 | Gross tax payable (higher of 1d and 7) | SumOf(7) — AMT (1d) is not computed for an individual with no s.115JC deduction |
| (8a – 8c, 9, 10, 11a – d) | B-TTI | ESOP deferral, s.115JD credit, relief | ManualEntry each, naming what is not computed |
| `NET_TAX_LIABILITY` | B-TTI.12 | Net tax liability | FromNodes `tax.netpayable`, the bill after rebate, surcharge and cess |
| `INTEREST_234A`, `_234B`, `_234C` | B-TTI.13a – c | Interest | as ITR-1 D7 – D9 |
| `INTEREST_AND_FEE` | B-TTI.13e | Total interest and fee | SumOf(13a, 13b, 13c, 13d) |
| `AGGREGATE_LIABILITY` | B-TTI.14 | Aggregate liability (12 + 13e) | SumOf(12, 13e) |
| (15a – 15d) | B-TTI | Advance tax, TDS, TCS, self-assessment | ManualEntry. `CREDIT-TAX-PAID`'s lines do not name their kind, so only 15e's total is filled |

## Questions

- **Q5 — 80C, 80CCC, 80CCD(1).** The engine allows one figure under the s.80CCE ceiling (`DED-SCHEDULE-XV`, provision
  "ss.80C, 80CCC, 80CCD(1), 80CCE").
  - **Q5a (recommended):** `DED_80C` reads it, labelled "80C, 80CCC and 80CCD(1) together, within s.80CCE".
    `DED_80CCC` and `DED_80CCD_1` are ManualEntry: "included under 80C; split by section from your proofs".
  - Q5b: all three ManualEntry.
- **Q7 — the tax at all special rates.** D1, 2b and 2d need the sum over every special rate.
  - **Q7a (recommended):** `FromTaxBasis` may say `"specialRate": "ALL"`.
  - Q7b: a `SumOf` over one cell per rate, one cell for each special rate the year has.
- **Q8 — B-TI 3b(i) "Long-term chargeable @ 12.5%".** The first pass placed only the s.112A part there, but s.112's part
  (`LONG_TERM_OTHER`) is at 12.5% too for transfers in TY 2025-26.
  - **Q8a (recommended):** `FromCapitalGainsPart` takes a list of parts, and 3b(i) reads both.
  - Q8b: two cells summed.
  - Either way, the first pass's `LTCG_112A` at 3b(i) is corrected.
- **Q9** — above.
- **Q10 — C3(a)(i), (ii), sale and cost.** The figures exist per bucket in `capitalgains.income`'s outcomes, but no
  source reads them.
  - **Q10a (recommended):** ManualEntry now, placed in W-093a beside Schedule CG.
  - Q10b: a new source now.

**Out of this pass, and why:**
- **HP per property (HP.1a – 1k):** the engine's house-property lines are per property, but no source reads a line per
  item. Placed with W-093a, as CG's are.
- **OS 1a – 1e by kind:** `OS-INTEREST`'s lines do not name the kind at the cell's level (savings, deposits, others),
  so 1b's split is ManualEntry and 1b's total is `FromRules [OS-INTEREST]`. That is drafted in W-093a's table, with
  OS 2 – 3.
