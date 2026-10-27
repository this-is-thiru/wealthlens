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
| **P4 perquisite kinds** | `node/HouseholdBenefitPerquisite.java:47`, `:65–69`; `node/MealPerquisite.java:46–55`; `node/ExpenseBenefitPerquisite.java:45–56`; `node/MotorCarPerquisite.java:65`, `:70–74`, `:92`, `:150–151`; `node/PerquisiteNode.java:158–159`; `node/AssetPerquisite.java:85`, `:92`; `node/SpecifiedSecurityPerquisite.java:70`, `:99` (Q11: not a finding, W-154g, D-335); G9: `HouseholdBenefitPerquisite:71` (carrier), `MotorCarPerquisite:187`, `ExpenseBenefitPerquisite:48`, `:65`, `:68` (wholly for work, records kept, uniform health club) | L3 | the item's path as data (a definition on its rule set, chosen by item facts) | N1 (D-253) for car, asset, accommodation, club |
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
| P4 `node/SpecifiedSecurityPerquisite.java:70–78`, `:99–106` (fair market value by basis; W-154g, D-335) | dispatch by the input's sealed shape: each `FairMarketValueBasis` record carries only the prices its method reads (S4 r.15(6)–(7); Rules 1962 r.3(8)–(9), as `PERQ-SPECIFIED-SECURITY` quotes them), so a data definition would restate the shape one-to-one, and a changed method changes what is entered; the window (`PERQ-SPECIFIED-DATE`) and the rate are rule data already |

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

**Status: audited and placed (session 28, D-314): Q1 is W-159, after W-158.** G1 – G8 by the triage script over `taxplanning/` (119 hits,
`reviews/law-in-code/l2-hits.txt`), G9 (1, `l2-g9-boolean-facts.txt`), G10 (0); every hit given a verdict. Read whole:
`enums/` (9), `portfolio/` (2), `recommendation/RegimeAdvisor`; `RestructuringEngine:180–250`. The rest of `engine/`,
`recommendation/` and `policy/service/` was read by its hits only: under C2a the legacy compute path is one finding,
so a line-by-line read would add places to it, not rows.

| Grep | Hits | Verdicted |
|---|---|---|
| G1 date literals / G3 money and rate makers | 0 / 0 | — |
| G2 numeric literals | 36 | 36 |
| G4 `switch` / `case` | 15 | 15 |
| G5 sets and maps | 1 | 1 |
| G6 enum comparisons | 10 | 10 |
| G7 constants | 12 | 12 |
| G8 quoted codes | 45 | 45 |
| G9 boolean-fact tests / G10 classifying enum methods | 1 / 0 | 1 |

### The finding — the legacy compute path holds the law a second time, and no placed row removes it

| Proposed row | Places (all under `taxplanning/`) | Kind | Direction | Owned already? |
|---|---|---|---|---|
| **Q1 retire the legacy compute path** | **Law as data outside the packs:** the five `resources/data/tax-policies/*-2025-26*.json` (one tax year), seeded by `policy/service/PolicySeederService.java:44–49` (`TAX_YEAR = "2025-26"` and the file names in Java). **Law in Java:** `engine/PerquisiteValuationService.java:29–54`, `:66`, `:116–147`, `:161`, `:183` (fallback figures when a policy field is absent — car, driver, meal, gift); `engine/NewRegimeTaxEngine.java:112` (meal fallback); `engine/AbstractTaxEngine.java:202` (cess fallback 4 %); `engine/HraExemptionCalculator.java:14–16` (`METRO_CITIES`, a 30-day month), `:57` (rent above 1,00,000), `:62–67`, `:112–115` (50 % / 40 % / 10 %); `salary/dto/SalaryProfileResponse.java:53` (a second `METRO_CITIES`); `recommendation/RegimeAdvisor.java:32–34` (80C 1,50,000, a 30 % rate), `:43` (80D 25,000), `:69` (30 %), and its prose on which deductions each regime allows; `recommendation/RestructuringEngine.java:240`, `:248` (30 % fallback marginal rate), `:229–233` (perquisite by allowance code). **Closed legal categories:** `enums/CarEngineSize` (`LEQ_1600CC_OR_EV`), `enums/CityTier` (`METRO_8`), `enums/RegimeType` (two regimes). **Also against §4:** money as `long` / `double` throughout `engine/` and `recommendation/`. The response already says "this calculator is being replaced and its results are known to be wrong" (`salary/dto/KnownErrorsComputation.java:16`) | L6, L1, L2 | delete it, as `03-WORK-ITEMS.md` Appendix E group **L2** lists ("the compute path, together"), once `taxengine` serves what it serves; D-063: "remove it when the replacement passes its golden tests (`09-build-plan.md` B2)" | **none found.** Appendix E proposed `W-109b` or `W-045m` for L2, "waits on OQ-3"; D-063 answered OQ-3 (warn now, remove later) and W-045m built the warning, but no row was added for the removal (`grep` of WI rows for the classes, the JSON and "legacy" finds only W-078i, W-088u8 and W-088l2) |

N6 (`RestructuringEngine.java:195–200`, the allowance ranking by string `switch`) is still present. It ranks advice —
a product choice, not law — so it is **S**, and it goes with Q1, since `recommendation/**` is in Appendix E's L2 group.

### Not findings

| Hits | Reason |
|---|---|
| `portfolio/CapitalGainsAdapter.java:57–63` (refuses `UNCLASSIFIED` and `SPECULATIVE`) | routes on `portfolio`'s classification; the classes are `TradeClassifier`'s — **N4, audited in W-088l3**. The adapter's `TLocalDate.financialYear` call (`:44`) is **N2, W-088l3** |
| `portfolio/MoneyBridge.java` (`PAISE_PLACES`, `BigDecimal.valueOf`; `toTMoney` refuses by design, TL-8 §7, tested) | conversion, a deliberate guard |
| `engine/TaxEngineFactory.java:17–19`, `engine/FormulaEvaluator.java:50`, `NewRegimeTaxEngine:196` / `OldRegimeTaxEngine:257` (slab regime match), `policy/service/PolicyService.java:50`, `RestructuringEngine:79` (`ACTIVE`), `:166–168`, `:263–267` (action text), `salary/service/SalaryProfileService.java:81` (`NOT_DECIDED`), `KnownErrorsComputation.NOTICE`, the G8 component and part codes (`"BASIC"`, `"EMPLOYER_PF"`, `"PERQUISITE"` …) | inside Q1 (the legacy path's own dispatch and codes) or the code's own states; no separate row |

### Summary for placement

One proposed row, Q1: the removal Appendix E planned and D-063 decided, never placed. Every law-in-code place in the
module is inside it. If it should not wait for the whole replacement, the alternative is to fix the places in Java now,
which the module's own notice says is code being replaced.

## W-088l3 — `portfolio`, `corporate`, `brokercharges`, `shared`, the rest

**Status: audited and placed (session 28, D-315): T1 – T8 are W-160 – W-167 (in the order T1, T2, T4, T3, T5, T8, T6, T7), after W-159.** G1 – G8 per module (`reviews/law-in-code/l3-<module>-hits.txt`;
`insurance` had none), G9 (9, `l3-g9-boolean-facts.txt`), G10 (1); every hit given a verdict. Read whole:
`shared/util/time/TLocalDate`, `shared/util/collection/TJsonMapper` (its `return null`s), `ChargeDeductibilityService`,
`CapitalGainsType`; the holding-period JSON and a rate card (`zerodha-equity-delivery-2025-04-01.json`); `holding/`,
`corporate/service/` by their hits.

| Module | G1 | G2 | G4 | G5 | G6 | G7 | G8 | Verdicted |
|---|---|---|---|---|---|---|---|---|
| `portfolio` | 2 | 45 | 36 | 13 | 15 | 23 | 3 | 137 |
| `brokercharges` | 1 | 24 | 20 | 13 | 7 | 26 | 0 | 91 |
| `shared` | 1 | 15 | 12 | 0 | 0 | 40 | 6 | 74 |
| `corporate` | 5 | 2 | 10 | 1 | 0 | 3 | 0 | 21 |
| `finance` | 0 | 16 | 11 | 0 | 0 | 0 | 0 | 27 |
| `auth` | 0 | 1 | 0 | 4 | 0 | 3 | 3 | 11 |
| `helper` | 0 | 0 | 0 | 0 | 0 | 0 | 1 | 1 |

### Findings, grouped into proposed rows

| Proposed row | Places | Kind | Direction | Owned already? |
|---|---|---|---|---|
| **T1 `portfolio`'s holding-period law and classes** (N3, N4) | `resources/data/holding-periods/holding-period-policies.json` with `portfolio/holding/HoldingPeriodResolver.java:97–104` and `HoldingPeriodService.java:52–60` (the law a second time beside `RS-CG-TERM`); `holding/TradeClassifier.java:47–55` (intraday equity speculative, futures and options unclassified — segment-decided classes); `dto/enums/CapitalGainsType.java:36–38` (`countsAsCapitalGains`, G10); the stored `trade_outcomes.capital_gains_type` (a field derived from today's law); its readers `ProfitAndLossService:136`, `:425`; `service/PortfolioService.java:817–821` (holdings long-term after one year for every asset, from `LocalDate.now()`) | L6, L2, L4 | `portfolio` publishes the facts, the tax side decides; whether `portfolio`'s own reports keep a term (read from the engine, or none) is the product change D-302 D2a left to this audit | **Done** by W-160a – f (D-363 – D-371): `portfolio` publishes facts only and its duplicate law is removed; the by-term read view is W-160g, after W-109 |
| **T2 the April – March year outside `TaxYearId`** (N2) | `shared/util/time/TLocalDate.java:61–66` (`financialYear`, 8 callers incl. `taxplanning/portfolio/CapitalGainsAdapter:44`); `portfolio/service/CapitalGainsSummaryService.java:48`, `:64` (the year's window built again); `brokercharges/entity/model/YearlyChargeSummary.java:42` (April as month 0) | L1 | one derivation: `TaxYearId.containing` needs `shared → taxengine :: id` (D-269); or a `shared` type the two sides agree on — for placement | **Done** by W-161 (D-374): `shared/util/time/FinancialYear` is the one derivation outside the engine (the seven `TLocalDate.financialYear` callers, `CapitalGainsSummaryService`, `ProfitAndLossSplitReshape`, `YearlyChargeSummary`); the engine keeps `TaxYearId` (D-269) — accepted, since the engine importing `shared` would begin a module cycle |
| **T3 statutory charges on each broker's card** (C1a) | `resources/data/charges/*-equity-*.json`: `STT` (0.1 % delivery), `STAMP_DUTY` (0.015 %, buy), `GST` (18 % on a listed base), `SEBI_FEE`, repeated on every broker's card, windowed by the card | L6 | a shared statutory card the broker cards reference; or accepted, the cards modelling the contract note's bill — for placement | — |
| **T4 deductibility as a flag on the charge catalogue** | `brokercharges/entity/ChargeCatalogueEntity.java:69–70` (`deductible_for_capital_gains`, not versioned by year); read by `service/ChargeDeductibilityService` (an unknown or unflagged code excluded with a warning) | L4 | which charges reduce a gain (s.48) as tax-side rule data, by year; the catalogue keeps the charge's facts | **Split** (D-375): W-162a the engine nets the gain from facts (`RS-CG-CHARGE`) — **built** (D-376), W-162b the stored `deductible_*` fields go — **built** (D-377), W-162c the catalogue flag goes |
| **T5 a Gold Bond's maturity defaulted** | `portfolio/service/parser/AssetRequestParser.java:179` (`transactionDate.plusYears(8)` when none is given) | L1 (law not identified), and a default in place of a missing fact | require the date, or take it from instrument data | — |
| **T6 dead weight** (N5, and one found here) | `corporate/dto/CorporateActionDto.java:40` (`date`, accepted, never saved — D-306); `portfolio/service/ProfitAndLossService.java:54` (`MARCH`, unused) | S | remove | — |
| **T7 `TJsonMapper`'s silent `null`** (N7) | `shared/util/collection/TJsonMapper.java:31`, `:38`, `:46`, `:66`, `:83` | S (CLAUDE.md §2 "nothing silent") | `Optional` or a thrown failure, callers adjusted | — |
| **T8 coverage placement** (N8) | `content/coverage.json:70` (2025 s.67) and `:644` (1961 s.45) list `SETOFF-WITHIN-CAPITAL-GAINS` and `CG-PARTS`, while `:111` (2025 s.108) and `:692` (1961 s.70) list `HP-ANNUAL-VALUE`, `HP-INTEREST`, `HP-OWN-RESIDENCE-INTEREST`. The repo's own act index (`content/acts/INCOME_TAX_ACT_2025.json`) gives s.67 as "Capital gains" (the charging section, p.104) and s.108 as the set-off section (p.147), which the set-off row itself cites | S (register data) | move each row to the section it cites; check the house-property rows' sections | — |

### Not findings

| Hits | Reason |
|---|---|
| `corporate/service/CorporateActionService.java:41–42`, `:164–169` (quarter windows), `:219–240` (action dispatch), `:562`, `:586` (the demerger's announced price percentage); `CorporateActionType.FILTERABLE_CORPORATE_ACTIONS` | the product's processing cycle and blocking; the percentage is the action's own fact |
| `finance/**` (EMI, SIP solving), `auth/**` (role hierarchy, token expiry), `helper` (a sample package id) | no law |
| `shared/util/calculator/XirrCalculator`, `TMoney`, `ExcelHeaders`, `ExcelParser`, `QueryBuilder`, `SafeExpressions` | arithmetic and plumbing |
| `portfolio` arithmetic, analytics, parsers and exports (G2, G4 rest); `CapitalGainsSummaryService:165` (STT evidence from a breakdown, D-292); `ProfitAndLossService:58` (`FORTNIGHT_BOUNDARY`, the P&L's half-month buckets); `Acquisition:26`, `TradeVenue`, `TemporaryTransactionService:115` | the code's own states, reporting choices, decided facts |
| `brokercharges` engine and calculators (rates read from the card), `ChargeScheduleResolver:49–52` (specificity weights), `ChargeScheduleService:54` (a card's verification age), `ChargeRecordingGatewayImpl:52`, `ChargeEngine:113`, `:174`, `:257`, `:264` (flags from the card) | the rules engine's machinery; values come from data |

### Summary for placement

Eight proposed rows. T1 is the substantial one — `portfolio` still classifies capital gains by today's law beside the
engine. T3 and T4 are the charge side's law (rates, deductibility). T2 closes N2. T5 – T8 are small. With W-088l1 and
W-088l2, the audit is complete.
