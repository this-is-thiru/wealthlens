# OQ-26 — the tax "in respect of" dividend income, and the 20% interest allowance's split (D-229)

Session 21, 2026-10-18. ChatGPT share https://chatgpt.com/s/t_6ad43c4cb40c8191862e60e2993537d7 (decoded). Its
finding: no official source prescribes either the method for the income-tax attributable to slab-rate dividends under
the 15% surcharge limit, or the split of a capped s.57 interest allowance between dividends and unit income.

| # | Source | Status | Passage |
|---|---|---|---|
| 1 | Income-tax (Third Amendment) Rules, 2026 — ITR-2, AY 2026-27, Gazette CG-DL-E-31032026-271435 (G.S.R. 227(E), Notification No. 46/2026), https://egazette.gov.in/WriteReadData/2026/271435.pdf | READ | p.68 Part B-TTI: *"2a Tax at normal rates on 15 of Part B-TI"*, *"2b Tax at special rates (total of col. (ii) of Schedule SI)"*; row 5 surcharge: *"i @ 25% of 15(ii) of Schedule SI"*, *"ii @10% or 15%, as applicable"*, *"iii On [(4) – 15(ii) of Schedule SI – tax on incomes referred in 5(ii) above)]"* — a "tax on" the 15%-limited incomes is assumed; how it is measured for slab-rate dividends is not stated |
| 2 | Finance Act, 2026, First Schedule Part I-A / I-B Para F Table 1 Sl. 1 (vi) | READ for D-200 (pp.7, 14, 82–84, 87) | *"the rate of surcharge on the amount of income-tax computed in respect of that part of income shall not exceed 15 per cent."* — no method |
| 3 | Income-tax Act, 1961 s.57 proviso, ins. by the Finance Act, 2020 s.30(b), egazette 2020/218938 p.25 | READ for D-194 (`OS-DIVIDEND-INTEREST`) | *"such deduction shall not exceed twenty per cent. of the dividend income, or income in respect of such units, included in the total income"* — no split between dividends and units |
| 4 | Memorandum explaining the Finance Bill, 2026 (1-2-2026) and 2020 (1-2-2020), incometaxindia.gov.in | REPORTED (403 here) | as relayed: restate the 15% limit and the 20% ceiling; no method, no split |
| 5 | CBDT circulars, ITR instructions, decisions | none found by the share | — |

## Reading

Both questions are unanswered by any official source. The figure each method gives differs: a top slice
(the incremental tax) attributes the highest slab rate to the dividends, so more tax bears the 15% limit and the
surcharge is lower; a pro-rata share attributes the average rate, so the surcharge is higher.

## A lead — the e-filing system computes the split (read here)

| # | Source | Status | Passage |
|---|---|---|---|
| 6 | CBDT e-Filing, *ITR 2 – Validation Rules for AY 2026-27*, v1.0, 26-5-2026, https://www.incometax.gov.in/iec/foportal/sites/default/files/2026-05/CBDT__e-Filing_ITR%202_Validation%20Rules_AY%202026-27_V1.0.pdf | READ | p.17, rule 214: *"In Schedule OS, Sl. No. 10 the quarterly break up of Dividend Income should be equal to [1ai (Dividend, gross) - DTAA Dividend other than 2(22)(e) - System calculated value of Interest expenditure u/s 57 attributable to Dividend other than 2(22)(e)]"*; p.18, rule 216: *"Interest expenditure on dividend u/s 57(1) should not be more than 20% of Dividend income."* — the system attributes interest to dividends by a method the rules do not state |

A follow-up prompt (chained, same conversation) asks for the utility's or schema's method for that attribution and for
the "tax on incomes referred in 5(ii)" figure (owner, 2026-10-18).

## The chained share, and the Department's own utility (read here)

Chained share https://chatgpt.com/s/t_6ad4416b9604819199763f8921a6649f (decoded): no published formula found; it pointed
to the AY 2026-27 ITR-2 schema, schema-change document, downloads page and the ITAT Pune *Hreyansh Vasundhara Family
Trust* order (ITA 1793/PUN/2024, 26-6-2025 — on the rate classification, no formula). I fetched them and the Excel
utility and read the utility's formulas and VBA as text (nothing executed).

| # | Source | Status | Passage |
|---|---|---|---|
| 7 | ITR-2 JSON schema `ITR-2_2026_Main_V1.2.json` and `ITR 2_Schema change document_AY2026-27_V1.2.pdf`, incometax.gov.in (2026-08) | READ | `ComputationOfTaxLiability`: `SurchargeOnAboveCrore` "Surcharge other than 115BBE after marginal relief" and `Surcharge25ofSI` — no formula; the change document names neither surcharge nor dividends |
| 8 | **ITR-2 Excel utility AY 2026-27 v1.4**, `ITR2_AY_26-27_V1.4.zip` (13-8-2026), https://www.incometax.gov.in/iec/foportal/sites/default/files/2026-08/ITR2_AY_26-27_V1.4.zip — the Department's own computation | READ | sheet *Tax Calculated*: `E332` (surcharge other than 115BBE) `= 0.25*((TaxPayableOnTotInc − Rebate) − SI_115BBE − … − V309 − DTAA tax) + 0.15*V309` where `V309 = SUM(V300:V304, V305:V308)` (the 15%-limited taxes); **`V300 = IF(TaxAtNormalRate>0, IFERROR(taxAggrIncm/OSSW1,0)*Surcharge_IncomeEH, 0)`** — `taxAggrIncm` (`B30`) the slab tax on `totalIncm` (`B23 = Sheet8b.AggregateIncome`), `OSSW1` set by the VBA to `Sheet8b.AggregateIncomeNew`, `Surcharge_IncomeEH` (`V298`) the dividend income: **the dividends' tax is the normal-rate tax × dividend income ÷ aggregate income — a pro-rata (average-rate) share, not a top slice**. VBA (module computing Schedule OS): `osDI = min(os.DividendGross_1ai_1aii, osBFLA, SW1)`; `osDI = Round(0.2 * osDI)`; `osIE = min(osIE, osDI)` → `os.amountinterestexpenditure` — **the 20% cap is applied to one dividend pool (Schedule OS 1a(i) + 1a(ii)); no split** |
| 9 | ITR-2, Gazette 271435 pp.52–55, Schedule OS | READ | *"1a Dividends, Gross (ai+aii+aiii) … i Dividend income other than (ii) and (iii) … ii Dividend income u/s 2(22)(e) … iii Dividend income u/s 2(22)(f)"*; no normal-rate row for domestic mutual-fund unit income (units in foreign currency only at 2dvii, special rate); *"3aii Interest expenditure on dividend u/s 57(i) (available only if income offered in 1a(i) and / or 1a(ii))"*, *"3aiia Eligible Interest expenditure u/s 57(1) – Computed Amount"* |

**Observation:** the utility's dividend pool (1a(i)) is where a filer reports unit distributions, the form having no
other normal-rate row; it is then also the dividend income the 15% surcharge limit reads (`V298`). The engine keeps
`UNIT_INCOME` out of dividend income for the surcharge test (D-200 C2a, on S2 s.2(40)). An inference from the form's
layout, not a stated rule.
