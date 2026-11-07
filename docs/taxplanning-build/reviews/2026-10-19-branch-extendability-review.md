# Branch review — extendability and engineering principles (2026-10-19)

**Scope:** `review/taxplanning` against `master` (merge base `f82a8805`), unpushed. 255 commits: 380 main Java files
(359 in `taxengine`), 26k lines of main Java, 27k lines of rule data, 37k of tests, 52k of docs.

**Measured against:**
- `CLAUDE.md` "Engineering standards", including the new rule "Everything extendable — facts, not rules";
- SOLID.

**Method:**
- Mechanical sweeps of all 380 main files:
  - literals: dates, tax years, money, numbers;
  - broad catches, `return null`, untracked TODOs;
  - method length, parameter count, class size;
  - enum values, and maps from a category to a rule id.
- The hotspots read by hand.

**Not reviewed line by line:**
- the rule data's legal content (each row was sourced and locked item by item);
- the tests;
- the docs.

**Verdict: not yet fully extendable.**
- **Where the design holds:** figures, dates and conditions do live in tax-year-versioned rule data. The sweep finds
  no law literal in engine code apart from the two noted below.
- **Where the law is still in Java:**
  - which rule applies to which fact;
  - several input enums whose values are legal thresholds;
  - the closed sets of rate bases.
- **Where SOLID is weak:**
  - recurring long parameter lists (missing types);
  - one duplicated carry-forward mechanism;
  - one nullable value crossing a boundary.

## Extendability — law in code

| # | Severity | Finding | Evidence | Direction |
|---|---|---|---|---|
| E1 | High, systemic | **Which rule applies to which fact is wired in Java.** The values are data; the dispatch is code. A new regime, table column or special rate means a code change. | About 300 `RuleIds.*` references across 30+ nodes (`OtherSourcesNode` 48, `ChapterEightNode` 27, …). Category → rule maps: `MotorCarPerquisite.PARTLY` / `OWN_RUNNING` (`node/MotorCarPerquisite.java:51`), `PerquisiteNode.RULE_FOR_PROVIDER` (`:67`), `RetirementNode.RULE_FOR_ENTRY` (`:57`), `AssetPerquisite.WEAR_AND_TEAR` (`:43`), `NotifiedRetirementLimits.AGGREGATE_FOR_ENTRY` (`:46`), `TaxByBasisNode.SPECIAL_RATES` (`:79`), per-regime `SurchargeRows` (`SurchargeNode.java:108`) | Rows declare what they apply to, as conditions on facts. A node evaluates a *family* of rows and does not name each one |
| E2 | High | **Input enums carry legal thresholds as values**, so the law is frozen into the input model. | `CarEngine.UP_TO_1600_CC` / `ABOVE_1600_CC` (`input/CarEngine.java`); `PopulationBand.ABOVE_FORTY_LAKH` / `FIFTEEN_TO_FORTY_LAKH` (2011 census; `input/PopulationBand.java`) | Facts: engine capacity in cc plus whether electric; the place's population. Bands and columns become rule data |
| E3 | High | **Closed sets of rate bases and parts.** A new special rate (another s.115B*-type rate) needs new enum values and code in three places. | `RateBasisHint` (3 values), `CapitalGainsPart` (4), `CapitalGainsInput.Term` | Rate bases as rule data; parts derived from them. Overlaps W-088f2 and W-088k |
| E4 | Medium | STT conditions mapped in Java in the adapter (D-241) | `taxplanning/portfolio/CapitalGainsAdapter.java` `shortTermBasis` / `longTermBasis` | **W-088f2** (placed) |
| E5 | Medium | `portfolio` decides short-term or long-term. Its policies are data and windowed, but they sit on the wrong side | `HoldingPeriodService`, `holding-period-policies.json` | **W-088k** (placed) |
| E6 | Low | The shipped tax years are declared in Java | `config/TaxEngineConfig.java:24` `SHIPPED_YEARS = Set.of(TaxYearId.of(2025), TaxYearId.of(2026))` | A resource manifest beside the locks (still declared, not scanned) |
| E7 | Low | The tax year is derived from a date by an inline April rule, duplicating the one source | `node/HousePropertyInterest.java:203` | One `TaxYearId` method used everywhere |
| E8 | Accepted | `CommutingCity` (`METRO_CITY` / `OTHER_CITY`) is the Rules' own undefined category, stated by the taxpayer | `rules/definition/CommutingCity.java` (D-102, OQ-7) | None needed. If the Rules ever list cities, it becomes rule data |

## SOLID and engineering standards

| # | Severity | Finding | Evidence | Direction |
|---|---|---|---|---|
| S1 | Medium | **Missing types (CLAUDE.md: more than about 5 parameters).** 39 methods take 6 or 7 parameters. | The same `line(...)` helper with 6–7 parameters in about 15 nodes (`MotorCarPerquisite:172`, `LoanPerquisite:96`, `MealPerquisite:87`, …); `SetOffNode` `setOff` / `absorb` / `absorbCapitalGains` / `use` (7 each); `CarriedCapitalLossSetOff.use` (7); `TraceLine.of` (7); `NodeContext` (7) | A trace-line specification type, and a set-off context type |
| S2 | Medium | **Duplicated mechanism, closed to extension.** The house-property and capital-loss carry-forwards repeat one skeleton (oldest first, refuse a future year, expire by span, absorb). A business or speculative loss would be a third copy. | `node/SetOffNode.java:205–230`; `node/CarriedCapitalLossSetOff.java:44–70` | One carried-loss mechanism, parameterised by rule data: the heads it may absorb, the span, the return condition |
| S3 | Medium | **Null across a boundary.** `RuleVersion.cap` is null for "no cap", while `relief`, `rounding` and `definition` are `Optional`. | `rules/temporal/RuleVersion.java:47`; `rules/json/RuleJson.java:119` `capOrNull`; null checks in `TemporallyApportionedCap:100`, `HousePropertyInterest:142`, `CessNode:54`, `SurchargeNode:289` | `Optional<CapShape>` |
| S4 | Low | **Size and SRP.** | Classes: `OtherSourcesNode` 556 lines, `ShapeJson` 467, `ChapterEightNode` 436. Methods over 40 lines: `LeaveTravelExemption.lines` 44 / `covered` 42, `RetirementNode.payment` 48, `SalaryDeductionNode.entertainmentAllowance` 45, `RulePackCompiler.compile` 49. On `portfolio` methods the branch extended: `TradeMatchingService.buildLotsFromBuys` 87, `matchSellToLots` 88, `PortfolioService.toTradeOutcomeContext` 80, `TradeOutcomeRecorder.buildRow` 65 | Split per concern as each is next touched |
| S5 | Low | **The enforced rule is looser than the written one.** Checkstyle allows 60-line methods and 7 parameters; CLAUDE.md says about 40 and about 5. | `checkstyle.xml:68–74` | Tighten, with a baseline that may only shrink |

## Verified clean

- **No law literals in engine code.** The only date, money or number literals are `Rate.HUNDRED` (arithmetic) and E6.
- **The two remaining hits predate the branch.** No `catch (Exception)` the branch introduced (`PortfolioService:280`
  predates it). No `return null` in branch code except S3 (`TJsonMapper` predates it).
- **No `TODO` / `FIXME` without an id**, and no branch code in the Checkstyle baseline: the only `taxengine` entries
  are five test files.
- **Tests at `74c7eaf2`:** unit 3004 / 0, integration 252 / 0.

## Out of scope, noted

`taxplanning/recommendation/RestructuringEngine.java:198–200` ranks allowances in a string `switch`. It predates the
branch, so it belongs to W-088l's repo-wide audit.
