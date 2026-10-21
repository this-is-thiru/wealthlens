# Law-in-code audit (W-088l, D-312)

CLAUDE.md §4: *"Every legal date, boundary, rate or qualifying list is tax-year-versioned rule data on the tax side,
never a Java constant, `if` or `switch` … Existing violations are listed by `W-088l`; a new one is a defect."* This
file is that list. Each section is one sub-item's audit; each finding ends in a row the owner placed, or a recorded
"no action".

## Criteria

| Kind | Finding | Not a finding |
|---|---|---|
| **L1 value** | a legal date, amount, rate, period, threshold or age as a Java literal or constant | arithmetic (`Rate.HUNDRED`), units, formatting, test code |
| **L2 list** | a qualifying list deciding legal treatment as an enum set, `switch`, map or `if` chain | a list of the code's own states |
| **L3 path** | a computation path chosen in Java by a legal category | dispatch by a shape type that only evaluates data |
| **L4 field** | a model or read-model field derived from today's law (a flag, label or class computed from a threshold) | a fact (a date, an amount, a mode, an event) |
| **L5 collapse** | an open category folded into a catch-all | — |
| **S noted** | a carried note that is not law (dead code, a silent `null`, a misplaced register entry) | — |

A section is cited only where a rule row or `sources/` already names it (R2); otherwise "law not identified". A
category the Rules leave undefined and the taxpayer states is recorded as accepted (review E8, D-102).

**Method.** Per module: (1) the greps below, run and kept with their hit counts; (2) every hit read in context and
given a verdict; (3) every class naming a legal category read whole, since a path choice need not match a pattern.
**Limit:** a grep-led read, not every line of every file.

## Carried notes

| # | From | Note | Audited in |
|---|---|---|---|
| N1 | D-253 | `RetirementNode` paths by entry; `NotifiedRetirementLimits.context` / `capped` by entry; `AssetPerquisite` method by kind; `MotorCarPerquisite` Sl. 1(c)(i)/(ii) and several-cars proviso; `PerquisiteNode:159`; D-252's club and disability branches | W-088l1 |
| N2 | D-269 | `shared/util/time/TLocalDate.financialYear` | W-088l3 |
| N3 | D-302 D2a | `portfolio` `HoldingPeriodService` / `holding-period-policies.json` | W-088l3 |
| N4 | `plans/W-088k.md:93` | `TradeClassifier`'s segment-decided classes | W-088l3 |
| N5 | D-306 | `CorporateActionDto.date` never saved | W-088l3 |
| N6 | review 2026-10-19 | `RestructuringEngine.java:198–200` allowance ranking by string `switch` | W-088l2 |
| N7 | `plans/W-088r.md:122` | `TJsonMapper` `return null` | W-088l3 |
| N8 | `plans/W-088o1.md` risk 1 | `coverage.json` placement of `SETOFF-WITHIN-CAPITAL-GAINS` (2025 s.67 / 1961 s.45) and the house-property rows (s.108 / s.70) | W-088l3 |

## W-088l1 — `taxengine`

**Status: audited and placed (session 28, D-313): P1 – P8 are W-151 – W-158, after W-088l3.** Every hit of G1 – G8 has a verdict. In place of reading `node/`
and `input/` whole (planned, not done), two further passes looked for what the patterns miss: **G9**, Java tests of the
taxpayer's boolean facts in `node/` (13 hits, `reviews/law-in-code/l1-g9-boolean-facts.txt`), and **G10**, enum
methods that classify (`switch (this)`, `this ==`; 3 hits). A path choice that matches none of G1 – G10 may still be
unfound.

**Greps** — `tools/law_in_code_triage.py` over `backend/src/main/java/com/thiru/wealthlens/taxengine`, comments,
strings and text blocks blanked, `rules/generated/` skipped; raw hits in `reviews/law-in-code/l1-hits.txt`.

| Grep | Pattern | Hits | Verdicted |
|---|---|---|---|
| G1 | date literals | 2 | 2 |
| G2 | 3+-digit or decimal literals, `BigDecimal.valueOf` | 9 | 9 |
| G3 | `Money.of` / `Rate.of` / `percent(` | 12 | 12 |
| G4 | `switch` / `case` | 115 | 115 |
| G5 | enum sets, maps, `Set.of` / `Map.of` | 184 | 184 |
| G6 | enum `==` / `!=` / `.equals` | 131 | 131 |
| G7 | `static final` constants | 216 | 216 |
| G8 | quoted upper-case codes | 86 | 86 |
| G9 | `node/`: `if` / `?` / `&&` / `filter` on an input's boolean fact | 13 | 13 |
| G10 | enum methods: `switch (this)`, `this ==` | 3 | 3 |

### Findings so far, grouped into proposed rows

| Proposed row | Findings (`path:line`, all under `taxengine/`) | Kind | Direction | Owned already? |
|---|---|---|---|---|
| **P1 rounding method** | `node/RoundingStep.java:26` (`SECTION_516_STEP = Money.TEN`, any other step refused), `rules/money/Money.java:166–173` (paise ignored, half up at 5) | L1, L3 | the rounding rows already state the step (`RuleRounding`); the method ("ignore paise, then to the nearest multiple, half up") as data on the row, the node applying whatever the row says | — |
| **P2 averaging period** | `input/Separation.java:23` (`MONTHS = 10`), `node/NotifiedRetirementLimits.java:44` (`MONTHS_AVERAGED = 10`) | L1 (law not identified here) | the months as rule data on the retirement rows | — |
| **P3 other-sources kinds** | `node/OtherSourcesNode.java:75–89` (`IN_FULL`, `WHOLE_DEDUCTED`, `BUSINESS_HEAD_KINDS`), `:148–159` (kind → treatment), `:169`, `:192`, `:210`, `:313`; `node/DividendInterestAllowance.java:74`, `:78`; `node/DepositInterestDeduction.java:51–53`, `:113–114`; `node/SurchargeNode.java:100` (`DIVIDEND_KINDS`) | L2, L3 | each kind's treatment chosen by rows on `ItemFactIs` of the kind, as W-088m2 did for other items | — |
| **P4 perquisite kinds** | `node/HouseholdBenefitPerquisite.java:47`, `:65–69`; `node/MealPerquisite.java:46–55`; `node/ExpenseBenefitPerquisite.java:45–56`; `node/MotorCarPerquisite.java:65`, `:70–74`, `:92`, `:150–151`; `node/PerquisiteNode.java:158–159`; `node/AssetPerquisite.java:85`, `:92`; `node/SpecifiedSecurityPerquisite.java:70`, `:99`; G9: `HouseholdBenefitPerquisite:71` (carrier), `MotorCarPerquisite:187`, `ExpenseBenefitPerquisite:48`, `:65`, `:68` (wholly for work, records kept, uniform health club) | L3 | the item's path as data (a definition on its rule set, chosen by item facts) | N1 (D-253) for car, asset, accommodation, club |
| **P5 retirement paths** | `node/RetirementNode.java:153`, `:208`, `:240`, `:243` (arrears relief); `node/NotifiedRetirementLimits.java:75`, `:102`; `input/RetirementPayment.java:50–60` (the facts each entry requires) | L3 | as P4, on `RS-RET-ENTRY` | N1 (D-253) |
| **P6 deduction and exemption kinds** | `node/DonationDeduction.java:139–143`, `:154`; `node/HealthInsuranceDeduction.java:90`, `:94`, `:100–141`, `:153`, `:174`; `node/DisabilityDeduction.java:64`, `:104`, `:164–166`; `node/ChapterEightNode.java:64–65`, `:99`, `:193–198`; `node/ScheduleXvDeduction.java:123–134`; `node/LoanInterestDeduction.java:82`; `node/EmployerContributionNode.java:140`; `node/GrossSalaryNode.java:93`; `node/SalaryDeductionNode.java:177`; `node/AllowanceExemption.java:57–58`; `node/LeaveTravelExemption.java:211`; `node/GiftIncome.java:29`, `:52`, `:60–64`, `:108`; `input/DonationClause.java:29–30` (`isWhole()`, G10); G9: `DonationDeduction:154` (reported by the donee), `HealthInsuranceDeduction:90` (cash) | L2, L3 | as P3 / P4 — the qualifying kinds and relationships as conditions on rows; likely a split by Chapter VIII, salary, gifts when placed | N1 for the disability branch |
| **P7 house-property occupancy** | `node/HousePropertyNode.java:111`, `:119`, `:158`; `node/HousePropertyInterest.java:70`, `:86`, `:160–181` (G9: `:181` the lender's certificate); `election/HouseElection.java:53`, `:55`, `:81` | L3 (borderline: occupancy is the Act's own category) | occupancy as an item fact the rows condition on; yours to judge whether it is a finding | — |
| **P8 input validations stating legal combinations** | `input/EmployerExpenseBenefit.java:44`, `:49`; `input/EmployerAccommodation.java:60–61`, `:73`, `:88`; `input/EmployerVehicle.java:62–87`; `input/EmployerHouseholdBenefit.java:38–44`; `input/GiftReceived.java:33`; `input/RetirementPayment.java:62`; `input/HousingLoan.java:47`; `input/HouseProperty.java:59` | L2 (borderline: some are fact consistency, not law) | each checked: law → a row's condition; fact consistency → no action | — |

### Not findings (verdicts so far)

| Hits | Reason |
|---|---|
| G1 `rules/id/TaxYearId.java:48`, `:52` (1 April – 31 March) | the tax year is the versioning axis itself, defined once (D-269); accepted |
| G2 `rules/money/Money.java`, `Rate.java` (`BigDecimal.valueOf`, `HUNDRED`); `TaxYearId:44` label format | arithmetic and formatting |
| G3 `rules/json/*` (parsing), `Rate.ZERO`, `percent()`; `node/AssetPerquisite:100`, `node/SurchargeNode:184`, `:199` (rates read from rows); `input/HouseProperty:55` (a share between 0 and 100 %) | parsing, arithmetic, or values from rule data |
| G7 — `RuleSetId` / `RuleId` / `ItemFactId` / `NodeId` constants and the nodes' `RULES` sets (what a node declares it reads); file and directory names, JSON mappers, `Money.ZERO`, `Money.TEN` (used by P1 only), `ItemFacts.NONE`, `SideVenue.UNRECORDED`, `UNKNOWN` / `UNRECORDED` codes, `ComputationReceipt.LENGTH`, `DataDrivenRegimeBehaviour` | ids, plumbing, the code's own states (the remaining G7 hits are P2, P3, P6) |
| G6 `pipeline/NodeContext.java:294–312` (rule-set selection), `election/ElectionOutcome:29`, `ElectionResolver:320`, `RegimeOffers:69`, `RegimeElection:74`; `node/CapitalGainsStt:82` (trace text); `node/SetOffNode`, `node/CarriedLossSetOff` on `HeadCode.CAPITAL_GAINS` (the parts mechanism; which head a carried loss reaches is `LossCarryForward` data); every `.equals(Money.ZERO)` | the engine's own machinery, election state, arithmetic |
| G4 `node/SalaryBaseNode.java:114–116` (evaluates the row's `DearnessAllowanceTest`), `input/InputsFingerprint:106` | dispatch by a data shape; hashing |
| G4 / G5 / G6 under `rules/` (`cap`, `catalogue`, `component`, `condition`, `coverage`, `id`, `money`, `pack`, `regime`, `registry`, `temporal`, `validate`; 57 + 84 + 27) and `rules/json`, `rules/timeline` (114: tag and directory dispatch, `Presence`, `PublicationStatus`, `MidYearCombination`, `TemporalAxis`) | the rule vocabulary's and loader's own machinery; `rules/definition/DisabledTransportLimit:57` (`CommutingCity`) is review E8, accepted (D-102) |
| G5 in `node/`, `pipeline/` — `CapContext` base maps, `EnumMap` by `HeadCode` / `AgeSubject` / `RulePackLayer`, the nodes' `Set.of(STEP…)` | plumbing (the remaining G5 hits are P3, P5, P6) |
| G8 — 75 id constructors (`BaseId`, `ComponentCode`, `RuleSetId`, …); `ComputationReceipt:30` (base-32 alphabet), `CapitalGainsInput:117` (the mode a corporate action requires), `RuleJson:77–79` (proration tags), `UNKNOWN` / `UNRECORDED` | ids, the code's own states |
| G8 `node/CapitalGainsTerm.java:61` (`"BONUS"`: the ex-date caveat on the trace) | changes no figure; owned by the W-088k4 work (D-307) — C4a, no new row |
| G9 `node/CarriedLossSetOff:134` (the row's `requiresReturnFiledInTime`), `NotifiedRetirementLimits:153` (the row's term), `DonationDeduction:151` (the row's cash limit), `:175` (P6's `isWhole`) | the condition comes from rule data |
| G10 `node/CapitalGainsStt.java:99`, `:103` (`Side`) | the code's own state |

### Summary for placement

Eight proposed rows. P1 and P2 are small (one value or method each). P3 – P6 are W-088m2's pattern — an item's kind or
facts choosing its row or path in Java — across 27 classes (P3 4, P4 7, P5 3, P6 13), and would each be split when planned.
P7 and P8 are borderline and yours to rule in or out. N1 is entirely inside P4 – P6, so it needs no row of its own.

### N1 re-verified

All of D-253's list is still present: `RetirementNode:153`, `:208`, `:240`; `NotifiedRetirementLimits:75`, `:102`;
`AssetPerquisite:85`, `:92`; `MotorCarPerquisite:150–151` (several cars) and the column choice in `employerCar`;
`PerquisiteNode:158–159` (was `:159`); the club branch `ExpenseBenefitPerquisite:52`; the disability branches
`DisabilityDeduction:64`, `:104`. They are folded into P4 – P6.

## W-088l2 — `taxplanning`

*Not yet audited.*

## W-088l3 — `portfolio`, `corporate`, `brokercharges`, `shared`, the rest

*Not yet audited.*
