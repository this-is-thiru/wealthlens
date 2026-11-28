# Module `taxengine` — income tax as data, and the engine that computes it

> Written from the code at `review/taxplanning`; every claim cites the line it was read from, and a bare path is under
> `backend/src/main/java/com/thiru/wealthlens/taxengine/`. Template: `docs/taxplanning-build/plans/W-357b.md` (tag `docs-history-m3`) C2a. The module is
> written in six parts (D-915), all now in: boundary and run (W-357b9a, D-916), the nodes (W-357b9b, D-917), the inputs (W-357b9c,
> D-919), the rules vocabulary (W-357b9d, D-920), rule packs and filing (W-357b9e, D-921), elections and the optimiser (W-357b9f, D-922).

## 1. Purpose

`taxengine` computes a person's income tax for a year, and explains every figure. The law is **data**: a rule pack per tax year,
shipped under `src/main/resources/rulepacks/`. The engine is a graph of **nodes**, each a step of the computation
(`package-info.java:1` – `:15`).

**It is pure.** It fetches nothing and reads no clock: everything it may read arrives in `EngineInputs`, including the instant of
evaluation, so a computation can be reproduced years later and the optimiser can afford thousands of runs. ArchUnit enforces both
(`taxengine_fetches_nothing`, `taxengine_has_no_clock`; `package-info.java:5` – `:9`). The one non-pure value is a fresh
`ComputationId`, which nothing is computed from (`result/ComputationId.java:27` – `:37`).

## 2. Boundaries

- **`CLOSED` module,** `allowedDependencies = {"shared"}` (`backend/src/main/java/com/thiru/wealthlens/taxengine/package-info.java:16` –
  `:20`; D-048).
- **Other modules see only its named interfaces:**

| Interface | Package | What it gives | Imports from other modules (grep) |
|---|---|---|---|
| `api` | `api` | `TaxCalculator` and the answer types (`api/package-info.java:1` – `:3`) | 80 |
| `input` | `input`, and four types elsewhere: `TemporalContext`, `ElectionState`, `ResidentialStatus`, `DisabilitySeverity` | everything the engine may read (`input/package-info.java`) | 60 |
| `id` | `rules/id` | the identifier types: `TaxYearId`, `RuleId`, `RegimeCode` and the rest | 44 |
| `money` | `rules/money` | `Money` and `Rate` | 33 |
| `result` | `result` | `TaxComputation`, `FilingPosition` | 0 (gap 2) |

- **The only caller is `taxplanning`.** It builds `EngineInputs` from stored state, calls `TaxCalculator` (26 of its methods are called;
  `compute` from 12 places), and stores and serves `ComputedTax`. Rules sit apart from the runner:
  `ArchitectureTest.rules_do_not_depend_on_the_runner` (CLAUDE.md).

## 3. The API (no HTTP routes)

`taxengine` serves no route; `taxplanning` serves its figures. `TaxCalculator` (`api/TaxCalculator.java:83` – `:691`) is the one way in.
Every method first takes the **year's shipped pack**: a year with no pack is refused as the caller's mistake, naming the years shipped
(`:445` – `:455`; W-263), which `taxplanning` answers as 400.

| Group | Methods | Where |
|---|---|---|
| Compute | `compute` → `ComputedTax`; `chooseForm` → `ItrFormChoice`; `compareRegimes` → `RegimeComparison`; `regimeElection` → `RegimeElectionDue` list | `:101` – `:120`, `:136` – `:156`, `:267` – `:304` |
| Price what would change the bill (each a second run) | `optimise`, `priceDeductionRoom`, `billWithRoomsFilled`, `priceClaims`, `priceRentRoom`, `termIfSold` | `:327` – `:432` |
| Read the year's law | `defaultRegime`, `salaryCertificateLaw`, `payLineComponents`, `certificate`, `claimPlaybooks`, `declarations`, `requireAsked`, `longestCarryForward` | `:361` – `:372`, `:463` – `:524`, `:606` – `:632` |
| Read the year's words | `computationSentences`, `reportSentences`, `evidenceSentences`, `claimSentences`, `instrumentCitations` | `:380` – `:391`, `:543` – `:565` |
| Pin and audit a stored computation | `packVersions`, `ruleChecksums`, `verificationLevels`, `provisionsCited` | `:530` – `:600`, `:638` – `:649` |

**`ComputedTax`** (`api/ComputedTax.java`), the answer `compute` returns, is four groups unwrapped into one JSON object:
- **`Provenance`:** computation id, receipt, year, regime, inputs fingerprint, the pack's law and exec layer versions.
- **`Figures`:** total income, bill, credits, final amount (payable above nil, refundable below), Chapter VIII deductions allowed,
  the house-property and other-sources heads.
- **`Dues`:** advance tax and its instalments, the interest and fees owed, the interest added to a refund.
- **`Workings`:** each trace step with its explanation, each capital-gains bucket's decision, and the notices.

## 4. Stored data

None. The engine stores nothing. It reads the shipped packs from the classpath once, at start: `TaxEngineConfig` compiles every year
`rulepacks/shipped-years.json` declares (`2025-26`, `2026-27`), so a pack that no longer matches its lock stops the boot rather than
failing a request (`config/TaxEngineConfig.java:8` – `:29`). Loading and locks are W-357b9e's part.

## 5. Behaviour

**A run** (`pipeline/PipelineRunner.java:98` – `:143`):
1. **The nodes:** the regime's pipeline, pack data, names the nodes by the slot each `produces`; the `NodeRegistry` of the 30 shipped
   nodes resolves them (`api/TaxCalculator.java:92`; `node/ShippedNodes.java:9`).
2. **The order** is derived from what each node `requires` (Kahn's algorithm). Among nodes ready at once, the one the pipeline lists
   first runs first, so the order and the trace read the same on every run (`pipeline/TopologicalSort.java:48` – `:132`). A required slot
   no node produces, or a cycle, is refused; the message names the cycle (`:61` – `:68`, `:141` – `:171`).
3. **Each node** evaluates through a `NodeContext` scoped to it. Reading a slot it did not declare, or a rule set it did not name,
   throws (`pipeline/NodeContext.java:34` – `:58`, `:139` – `:219`).
4. **Its result** is a value and the trace lines explaining it; notices hang off a line, never in a free list
   (`pipeline/NodeResult.java:7` – `:49`). The runner numbers every line into one ascending sequence (`PipelineRunner.java:146` – `:154`,
   `:253` – `:265`).
5. **Nothing is caught.** A node that cannot evaluate stops the run, never yields a zero (`:22` – `:33`, `:127`; D-NOSILENT;
   `ArchitectureTest.taxengine_does_not_swallow_failures`).

**Re-entry and second runs:**
- **Re-entry:** a node may ask for a slot as it would be had another slot held another value. Only the nodes between them run again,
  in their own store, and their trace is dropped (`PipelineRunner.java:156` – `:219`).
- **A run with rules withheld** prices a claim: each withheld rule's line is nil and says so (`:103` – `:113`; W-118).

**The registry's checks** (`pipeline/NodeRegistry.java:45` – `:170`): one writer per slot, a node must state what it requires and
which rule sets it evaluates, and no node may require what it produces. **Invariant I3**, "every rule in the pack is evaluated by some
node", is computed from the nodes' rule sets. A disallowance set's basis counts as evaluated, since every explanation of a rule it
forbids cites it (`:161` – `:170`). Run against the shipped pack by `T1SlabAndRebateTest:106`.

**The durable answer** (`result/`):
- **`TaxComputation`** is what the statute computes from the year's facts alone, and has **no date** in it, so it can be cached on
  inputs and law (`result/TaxComputation.java:15` – `:110`). It holds the id, year, regime, the law and exec layers, the inputs
  fingerprint, the trace, total income, tax by basis, the bill before filing effects, and the return's facts.
- **`ComputationProjector`** says which slot is which field (`result/ComputationProjector.java:12` – `:63`):
  - total income is `round.totalincome`;
  - tax by basis is `tax.bybasis`, before rebate;
  - the bill is `tax.netpayable`, unrounded.
- **The receipt** is `TXC-{year}-{6 base32 characters}`, derived from the id's first six hex pairs, each taken mod 32
  (`result/ComputationReceipt.java:25` – `:68`).

**The trace** (`trace/`) is the result, not a log of it: the JSON, the PDF and the return forms are projections of it
(`trace/TraceLine.java:13` – `:33`). A line carries:
- its sequence, node and rule, and its kind (`StepKind`: income, exemption, deduction, set-off, tax, relief, rounding, measure,
  interest, fee; `trace/StepKind.java:17` – `:45`);
- in and out, what bound it, provenance, a reason as facts, and headroom.

A notice has a severity (`INFO`, `ATTENTION`, `ACTION_REQUIRED`, `UNVERIFIED_RULE`) and cannot abort a run
(`trace/Severity.java:11` – `:30`; `trace/Notice.java:23` – `:48`). `ExplanationRenderer` turns a line into the pack's own words, falling
back to the rule id, never a blank (`api/ExplanationRenderer.java`).

### Worked examples (by hand)

**The canonical order.** A pipeline lists `a`, `b`, `c`; `c` requires `a`; `a` and `b` require nothing.
- **Ready at first:** `a` and `b`. `a` runs first, being listed first.
- **Then:** `b` and `c` are ready, and `b` runs, listed before `c`. Then `c`.

The order is `a, b, c` on every run. Listed `b, a, c`, it would be `b, a, c`.

**A receipt.** For the id `9f3a21c4-0b7e-…`, the first six hex pairs are 9f, 3a, 21, c4, 0b, 7e = 159, 58, 33, 196, 11, 126. Mod 32 they
are 31, 26, 1, 4, 11, 30, which read in `ABCDEFGHIJKLMNOPQRSTUVWXYZ234567` as 7, 2, B, E, L, 6. For TY 2026-27 the receipt is
**`TXC-2026-72BEL6`**.

**A part's land gains lowered** (the case `IndexedGainTest` asserts, moved here from W-088g3's plan, which W-357b9a removed;
`pipeline/IndexedGain.java:43` – `:62`). A land gain of
₹30,00,000, indexed ₹7,17,948.72, sits in a part of ₹40,00,000 that the nil band lowers to ₹37,50,000.
- **The gain:** 30,00,000 × 37,50,000 ÷ 40,00,000 = **₹28,12,500**, so it loses ₹1,87,500.
- **The indexed measure:** it loses the same rupees, 7,17,948.72 − 1,87,500 = **₹5,30,448.72**, never below nil.
- **Rounding:** two gains of ₹0.50 in a part of ₹1.00 lowered to ₹0.03 each round to ₹0.02, which would hold ₹0.04. The last gives up a
  paisa: ₹0.02 and ₹0.01.

### The nodes (W-357b9b, D-917)

Thirty nodes ship (`node/ShippedNodes.java:9` – `:39`), each writing one slot. Their order is the regime's pipeline, as the edges allow
(§5). Every legal figure a node applies is a row of the year's pack, never a Java constant. Where a node "has no rule", it is untraced: it
adds or reshapes figures whose own lines explain them.

| Slot | Node | Reads | What it computes |
|---|---|---|---|
| `inputs.components` | `ComponentPartitionNode` | — | The pay lines partitioned into heads by the year's component registry; an unknown or not-live code stops the run (D-090; `ComponentPartitionNode.java:31`) |
| `salary.base` | `SalaryBaseNode` | components | The narrow "salary" (basic, and dearness allowance where the terms provide) that HRA, gratuity, leave encashment and the pension limits are measured on (D-092) |
| `employer.contrib.charges` | `EmployerContributionNode` | base | The employer's provident fund, NPS and superannuation contributions beyond the yearly aggregate limit, and the accretion on the excess (D-126) |
| `salary.exemptions` | `ExemptionNode` | components, base | House rent allowance per period of occupation, the leave travel concession, the salary allowances (D-099 – D-102) |
| `salary.retirement` | `RetirementNode` | components | Gratuity, leave encashment, commuted pension and the other retirement and termination deductions, each under the entry the person names (D-103, D-106, D-109) |
| `salary.monetaryincome` | `MonetarySalaryIncomeNode` | components, base, exemptions, retirement | Salary income without perquisites: the measure of a "specified employee" (D-113) |
| `salary.perquisites` | `PerquisiteNode` | base, exemptions, monetary income | Accommodation, vehicles, meals and gifts, loans, holidays and cards, assets, household benefits, specified securities (D-111 – D-118) |
| `salary.gross` | `GrossSalaryNode` | components, perquisites, contribution charges | What "salary includes": the head's lines that feed the definition, plus perquisites and the contribution charges (D-091) |
| `salary.deductions` | `SalaryDeductionNode` | components, gross, base | The tax on employment, the standard deduction (the regime's amount or the salary, whichever is less), and for TY 2025-26 an entertainment allowance (D-093, D-105) |
| `salary.incomebeforetravel` | `SalaryIncomeBeforeTravelNode` | gross, exemptions, retirement, deductions | Gross less exemptions, retirement and other deductions: the income the medical-travel test reads (D-112) |
| `salary.medicaltravel` | `MedicalTreatmentAbroadNode` | income before travel | The part of an employer's spending on treatment abroad that is a perquisite (D-112) |
| `salary.income` | `SalaryIncomeNode` | before travel, medical travel | Income under "Salaries" |
| `houseproperty.income` | `HousePropertyNode` | — | Per property: annual value (nil for an own residence), less 30 %, arrears, the owner's share, less interest; the head less the own residences' interest within limits (D-155, D-156) |
| `othersources.income` | `OtherSourcesNode` | — | Interest, dividends, unit income, compensation interest, gifts, life-policy sums and the rest, each less what may be deducted against it (D-194 – D-207) |
| `capitalgains.income` | `CapitalGainsNode` | — | Each bucket's gain (consideration less cost less the charges the law deducts), the reinvestment exemption, its part, term and STT reading, the head's own losses (D-243, D-289, D-305, W-162a, W-088i) |
| `setoff` | `SetOffNode` | the four heads | A head's loss against the others in the elected or declared order, the house-property limit, the regime's bar, and losses carried in from earlier years (D-217) |
| `gti` | `GrossTotalIncomeNode` | set-off | Gross total income (D-132) |
| `chapterviii` | `ChapterEightNode` | gti, set-off, components, base | The Chapter VIII deductions, in order, each within its own limit and all within s.122(2)'s (D-130 – D-142, D-195) |
| `totalincome` | `TotalIncomeNode` | gti, Chapter VIII | Total income |
| `round.totalincome` | `RoundTotalIncomeNode` | total income | Rounded to the nearest ₹10: the first rounding point (s.516; `RATE-ROUND-003`) |
| `tax.bybasis` | `TaxByBasisNode` | rounded TI, set-off | Special-rate capital gains at their rows' rates; the rest through the regime's ladder; partial integration of agricultural income (D-071, D-244, D-778) |
| `tax.afterrebate` | `RebateNode` | by basis, rounded TI | Each rebate row of the regime, limited to slab-rate tax where its row says (D-074) |
| `tax.surcharge` | `SurchargeNode` | after rebate, rounded TI, set-off | One rate on the whole tax from the band the income falls in, the greater of the two incomes' ladders, less marginal relief by re-entry (D-084 – D-086) |
| `tax.cess` | `CessNode` | after rebate, surcharge | The Health and Education Cess on tax and surcharge (D-079) |
| `tax.netpayable` | `NetPayableNode` | after rebate, surcharge, cess | The bill before filing effects: what elections compare (D-080, D-436) |
| `credits` | `CreditsNode` | — | Tax already paid: at source, in advance, self-assessment (D-436) |
| `tax.balance` | `BalanceNode` | net payable, credits | Payable above nil, refundable below |
| `advance.tax` | `AdvanceTaxNode` | net payable | Liability, and what each instalment still asks (D-446) |
| `interest.and.fees` | `InterestAndFeeNode` | net payable, advance tax, rounded TI | Interest for a late return, an advance-tax shortfall and deferment (with its relief), the late-return fee, and interest added to a refund (D-446, D-660, D-663) |
| `round.netpayable` | `RoundNetPayableNode` | balance, interest and fees | The final amount rounded to ₹10, a refund on its magnitude: the second and last rounding point (`ROUND-NEG-001`) |

The slots' names are `NodeIds`. Each node's rule sets are its `ruleSets()`, and I3 checks that they reach every row of the pack (§5).

**What the order guarantees, by what the nodes read:**
- **Cess** reads `tax.afterrebate` and `tax.surcharge` and nothing earlier, so it cannot be charged on tax before the rebate
  (`CessNode.java:31`).
- **Rebate before surcharge.** A rebate limited to slab-rate tax keeps special-rate tax: ₹12,00,000 that is mostly capital gains keeps
  its special-rate tax (`RebateNode.java:46`; `DR 13` T13-1).
- **The bill stays the comparison.** Credits reach `tax.balance`, not `tax.netpayable` (`NetPayableNode.java:24`; `BalanceNode.java:18`).
- **Two rounding points, exactly:** total income, then the final amount (`RoundTotalIncomeNode`, `RoundNetPayableNode`; asserted by
  `T1SlabAndRebateTest.run_whenTheCaseIsComputed_roundsInExactlyTwoPlaces`).

### Worked examples: the nodes (by hand, from the shipped rows)

The rows, as TY 2026-27 ships them (`rulepacks/timeline/law/rules/`; each row's `introducedBy` names its Gazette copy):
- `RATE-SLAB-NEW-TAX-REGIME`: 0 up to ₹4,00,000; 5 % to ₹8,00,000; 10 % to ₹12,00,000; 15 % to ₹16,00,000; 20 % to ₹20,00,000; 25 % to
  ₹24,00,000; 30 % above (s.202(1) Table).
- `REBATE-NEW-TAX-REGIME`: up to ₹60,000 where total income is no more than ₹12,00,000, limited to slab-rate tax (s.156(2)(a), (3)).
- `REBATE-NEW-TAX-REGIME-EXCESS`: the tax limited to the income above ₹12,00,000, on slab-rate tax.
- `RATE-HEALTH-EDUCATION-CESS`: 4 % of tax and surcharge (Finance Act, 2026 s.3(15)).
- `SAL-STANDARD-DEDUCTION-NEW-TAX-REGIME`: ₹75,000 or the salary, whichever is less (s.19(1) Table Sl. 2(a)).

**A salary through the whole chain** (new regime, a resident individual, TY 2026-27, basic pay ₹13,25,000 and nothing else):
1. `salary.gross`: 13,25,000.
2. `salary.deductions`: least of 75,000 and 13,25,000 = 75,000.
3. `salary.income` = `gti` = `totalincome`: 12,50,000. `round.totalincome`: 12,50,000.
4. `tax.bybasis`, on the ladder: 4,00,000 × 0 + 4,00,000 × 5 % + 4,00,000 × 10 % + 50,000 × 15 % = 0 + 20,000 + 40,000 + 7,500 = **67,500**.
5. `tax.afterrebate`: the ₹60,000 rebate does not apply, since 12,50,000 > 12,00,000. The excess rule limits the tax to 12,50,000 −
   12,00,000 = 50,000, so **50,000**.
6. `tax.surcharge`: nil, the income being below every band.
7. `tax.cess`: 4 % × 50,000 = 2,000.
8. `tax.netpayable` = 52,000. With no credits, `round.netpayable` is **₹52,000**.

This is `DR 08` T1-N3's ₹52,000 for total income ₹12,50,000, which `T1SlabAndRebateTest` asserts (`golden/T1/T1-N3.json`).

**Where the excess rule stops binding** (T1-N4a and T1-N4b). Total income ₹12,70,580:
- **Ladder:** 60,000 + 15 % × 70,580 = 70,587. The excess rule caps it at 70,580.
- **Cess:** 4 % × 70,580 = 2,823.20, so 73,403.20, rounded to **₹73,400**.

At ₹12,70,590:
- **Ladder:** 60,000 + 15 % × 70,590 = 70,588.50. The cap 70,590 no longer binds, so 70,588.50.
- **Cess:** 2,823.54, so 73,412.04, rounded to **₹73,410**.

Both are `DR 08`'s figures.

### The inputs (W-357b9c, D-919)

**`EngineInputs`** (`input/EngineInputs.java:57` – `:103`) is the complete input to a computation, assembled by `taxplanning`. Every
component is mandatory, and the year it names must equal its temporal context's (`:69` – `:88`):

| Component | Type | What it holds |
|---|---|---|
| `taxYear`, `regime` | `TaxYearId`, `RegimeCode` | the year and the regime computed under |
| `temporal` | `TemporalContext` | the year's start and end, the **as-of date** (the engine reads no clock) and a date per temporal axis (`rules/temporal/TemporalContext.java:25` – `:30`) |
| `entered` | `EnteredFacts` | what the person entered: 40 lists and optionals, one per kind of fact (`input/EnteredFacts.java:105` – `:145`) |
| `assessee` | `Assessee`: `Individual` (date of birth, residence, disability, date of death) or `Huf` (residence) | who is assessed, one record per type, and **no PAN**: no rule computes on one (`input/Assessee.java:22` – `:37`; `input/Individual.java:32` – `:37`) |
| `family` | `FamilySnapshot` | the other people whose facts a rule reads (`input/FamilySnapshot.java:25`) |
| `ledgers` | `LedgerSnapshot` | the cross-year state one year cannot hold: leave-travel blocks, retirement deductions allowed earlier, house-property and capital losses carried forward, the education loan's first year (`input/LedgerSnapshot.java:45` – `:51`) |
| `elections` | `RegimeElectionSnapshot` | where the person stands with each regime, passed in, never fetched |
| `capitalGains` | `CapitalGainsInput` | the portfolio's gross buckets, never tax, pinned to an `asOf` and the portfolio's computation reference (`input/CapitalGainsInput.java:42` – `:177`) |
| `filing` | `FilingFacts` | the due date, when furnished, advance tax and self-assessment payments with dates, tax at source, the declarations' answers, and when a refund was granted (`input/FilingFacts.java:38`) |

**`EnteredFacts`' kinds, by head:**
- **Salary:** `Employment` (pay lines by registry code; dearness allowance terms; the employer's kind and, for a company, director or
  substantial interest).
- **Exemptions:** `Tenancy`, `Journey` and `JourneyTraveller`, `AllowanceUse`, `DisabledTransportClaim`.
- **Retirement:** `RetirementPayment` with `Separation`, `LeaveAtRetirement`, `StatutoryEntitlement`.
- **Perquisites:** `EmployerAccommodation` and `Furniture`, `EmployerVehicle` and `CarEngine`, meals, gifts, loans, expense benefits,
  asset use and transfer, household benefits, child education, share allotments with `FairMarketValueBasis`, and medical treatment abroad.
- **Employer funds:** `EmployerFundContribution`, `RetirementFundHistory`, `RecognisedFundInterest`.
- **Chapter VIII:** qualifying payments, life policies, health payments, a disabled dependant, specified disease, education and purpose
  loans, the Agniveer fund, the rent declaration, donations.
- **House property:** `HouseProperty` with `HousingLoan`.
- **Other sources:** `OtherSourceItem` by `OtherSourceKind`, `ProvidentFundAccount`, `GiftReceived`, `BusinessTrustUnits`,
  `LifePolicyReceipt`, `ClaimedPolicy`.
- **Capital gains:** `HouseReinvestment`.
- **Set-off:** an `absorptionOrder`.

A fact is entered as the person knows it, never as a rule's column. A car is "electric, or its cubic capacity", not Table II's band
(`input/CarEngine.java:16`), and which band applies is the rule's.

**Each record refuses a bad part** at construction, naming the record and the component: "`<Record>.<component>` may not be null". A
nil-or-more amount refuses a negative. `InputRefusals.eachComponentNulled` proves the null contract for every component, a component added
later included.

**`InputsFingerprint`** (`input/InputsFingerprint.java:70` – `:199`) is the input half of the cache key: a SHA-256 of the inputs'
**canonical form**, walked by reflection, so a component added anywhere enters the key with no one remembering (`:33` – `:38`):
- **A record** is `T:Name{a=…,b=…}`, its components sorted by name, so reordering a declaration invalidates nothing (`:130` – `:146`).
- **Sets and maps** are sorted by their canonical form; **lists** keep their order (`:51` – `:60`, `:160` – `:188`).
- **The leaves:** an enum is `E:NAME`, an Optional `O:` then its value, an absent value `~`, any other leaf `V:Type:value`
  (`:105` – `:124`).

The pack is not in it: `RulePack.computationCacheKey()` covers the law and exec layers, and leaves the editorial ones out (`:40` – `:47`).

**A person's age** for a rule is their completed years at the **tax year's end** (`pipeline/NodeContext.java:370`), from the date of birth
carried here and the year the inputs name, never from a clock (`input/Individual.java:12` – `:24`).

### Worked examples: the inputs (by hand)

**A canonical form.** `new Huf(ResidentialStatus.RESIDENT)` has one component, `residence`, an enum, so its canonical form is
**`T:Huf{residence=E:RESIDENT}`**. An `Optional.empty()` component would read `O:~`. A list of two enums `[A, B]` would read
`L[E:A,E:B]` in that order, but as a set `S[E:A,E:B]` whatever its order.

**An age at the year's end.** For TY 2026-27 the year ends 2027-03-31:
- born 1966-11-10: 60 completed years at that day;
- born 1967-04-02: 59, since the sixtieth birthday is 2027-04-02, after the year ends.

### The rules vocabulary (W-357b9d, D-920)

`taxengine.rules` is the language the rule packs are written in: 190 types in twelve packages, which may not depend on the rest of
`taxengine` (`ArchitectureTest.rules_do_not_depend_on_the_runner`). Each family is **closed**: a sealed interface whose permits list is
the whole set, so a `switch` that misses a shape does not compile. A new shape is a new record and a new permit, never an edit to an
existing one.

**A rule** is a stable id with an ordered list of **`RuleVersion`s** (`rules/temporal/RuleVersion.java:45`). A version holds:
- its `Validity` and the regimes it applies to;
- at most one each of a cap, a relief, a rounding and a definition;
- its conditions;
- its provision in each Act (`Provision`: citation, reference, `PRESENT` or `ABSENT_IN_THIS_ACT`);
- the amendment that introduced it, and its legal status, publication status and provenance.

A change in the law is a second version under the same id, never a new rule guarded by a date (`rules/condition/Condition.java:26` –
`:29`).

| Family | Shapes | What it states |
|---|---|---|
| **`Validity`** (`rules/temporal/Validity.java:29`, `:60`, `:98`) | `Between`, `Retrospective`, `PendingNotification` | when a version is in force. Each rule declares the clock it is read against, a `TemporalAxis`: `TAX_YEAR`, `EVENT_DATE`, `ACQUISITION_DATE`, `SANCTION_DATE`, `PAYMENT_DATE` (`TemporalAxis.java:18` – `:30`). A date with no version in force is `NoRuleVersionException`; an axis whose date was never supplied is `MissingTemporalCoordinateException` |
| **`CapShape`** (`rules/cap/CapShape.java:27` – `:29`) | `Fixed`, `PercentOfBase`, `SlabLadder`, `LeastOf`, `AggregateWithCeiling`, `PerUnitPerPeriod`, `AmountInExcess`, `PercentOfBaseByLocation`, `PercentOfBaseByPopulation` (9) | a statutory limit or rate table. It reads named amounts (`BaseId`) from a `CapContext`, never a figure, and answers a `CapResult`: the value, what bound it, the reason and the headroom to the next limit |
| **`ReliefShape`** (`rules/relief/ReliefShape.java:14`) | `FlatWithCeiling`, `ExcessOverThreshold`, `ThresholdLadderCeiling`, `NoRelief` | an amount taken off tax already computed. `RuleRelief` adds whether the Act limits it to the tax at slab rates, stated per row (`RuleRelief.java:13`) |
| **`Condition`** (`rules/condition/Condition.java:48` – `:73`) | 25: residence, age, person type, disability; income at most or at least a node's value; a head's income or loss; components and evidence present; an item's facts (`ItemFactIs`, `ItemFactAtMost`, `ItemDateBefore`); declarations; a return's facts; `AllOf`, `AnyOf`, `Not` | a yes/no test a row attaches to itself |
| **`RuleDefinition`** (`rules/definition/RuleDefinition.java:11`) | 44 | what an amount *is*: a head's total, a salary base, a journey count per block, an index by year, an absorption order, a holding period, an interest calculation, a late fee. `RuleDefinitions` reads the one kind a node expects and refuses any other |
| **`ProrationBasis`** (`rules/temporal/proration/ProrationBasis.java:19`) | `Days`, `CalendarMonths`, `CompletedMonths` | how a rule measures the part of a year it applies to, named per rule |
| **`RuleRounding`** (`rules/rounding/RuleRounding.java:18`) | one record: a step and a round-up point, both whole rupees | how a version rounds. `Money.roundToMultiple` does the arithmetic (`rules/money/Money.java:199` – `:206`) |

**How a condition answers:**
- **Boundaries are inclusive:** `IncomeAtMost` holds at its limit (`rules/condition/IncomeAtMost.java:25` – `:26`). A threshold's
  "exceeding" is the `Not` of the inclusive form.
- **A fact never supplied** makes a condition throw, never answer (`AllOf.java:9` – `:10`).
- **`Not` negates the answer, never the failure** (`rules/condition/Not.java:8` – `:13`). A missing fact is not turned into "true",
  which would allow a deduction nobody knew the person qualified for.
- **`AllOf` and `AnyOf` short-circuit,** so a limb after the deciding one is not read (`AllOf.java:35` – `:46`; `AnyOf.java:36`).

**The registries:**
- **`ComponentDefinition`** (`rules/component/ComponentDefinition.java:52`) is one pay line of the pack's registry, so a new allowance
  is data. It holds:
  - its code and head;
  - its `ComponentKind` (salary, perquisite, employer or employee contribution, statutory deduction);
  - its `CashNature` and `TaxTreatment`;
  - the rules it feeds and the salary bases it counts in;
  - its lifecycle;
  - its `Liquidity`: when money paid in can come out, dated.
- **`EmployerFundDefinition`** is the same for an employer's fund.
- **`DeclarationDefinition`** is one question of the year's declaration catalogue, answered yes/no or as an amount.

**Regimes** are data too. A `RegimeSpec` (`rules/regime/RegimeSpec.java:47`) holds:
- whom it applies to;
- its rate table (a rule id) and its disallowance and eligibility sets;
- its `ElectionProtocol` and `RegimeLifecycle`: entry window, in-force window, continuation and invalidation;
- whether it is the default;
- its `PipelineSpec`: the set of nodes it runs, whose order is derived, not listed;
- optionally a `BehaviourId`, the escape hatch for behaviour that is neither a number nor a node.

A person's standing with a regime is an `ElectionState`: `NEVER_ELECTED`, `IN_EFFECT`, `WITHDRAWN` and `PERMANENTLY_INELIGIBLE`
among them (`ElectionState.java:21` – `:35`).

**Reasons:**
- A trace line's "why" is a `Reason`: a `ReasonCode` and named, typed `ReasonFact`s (`rules/reason/Reason.java:14`).
- `ReasonCodes` lists the 167 codes the engine publishes, with the facts each carries.
- The words are the pack's content, never the engine's.

**Money and rates:**
- **`Money`** (`rules/money/Money.java:23`) is an exact `BigDecimal` of rupees at scale 2, rounded half-up once on construction
  (`:26`, `:42`). Intermediates are carried to 12 significant digits (`:33`), so a percentage is not rounded until it is a `Money`.
- **`Rate`** (`rules/money/Rate.java:22`) is a fraction, built from a percentage (`Rate.percent("30")` is 0.30; `:39` – `:41`), so
  30 and 0.30 cannot be confused.
- `money` is one of the two packages allowed to call `setScale` (`Money.java:20` – `:21`).

**Ids** (`rules/id`, 20 records) are named strings or numbers, never enums, so a new code is pack data. Two read differently:
- **`TaxYearId(startYear)`**: `TaxYearId(2026)` is TY 2026-27, from 1 April 2026 to 31 March 2027 (`rules/id/TaxYearId.java:17`).
- **`DateRange`**, the period type, is inclusive at both ends and may be open-ended (`rules/temporal/DateRange.java:18`).

**`HeadCode`** is the five heads (`rules/condition/HeadCode.java:17` – `:23`). `BUSINESS_OR_PROFESSION` exists so that its mere
presence can change which regime rules apply, not so that it is computed (`:7` – `:15`).

### Worked examples: the vocabulary (by hand)

**Rounding** with a step of ₹10 and a round-up point of ₹5, the values `Money.roundToMultiple`'s Javadoc names (`Money.java:188` –
`:191`):
- **₹1,234.99:** paise dropped → 1,234; remainder 4 < 5 → **₹1,230**.
- **₹1,235.00:** remainder 5 ≥ 5 → **₹1,240**.
- **−₹1,235.00:** rounded on the magnitude, sign restored → **−₹1,240**.

**A relief** (`ReliefShapeTest`; its figures are in §11, W-048). `ExcessOverThreshold(₹12,00,000)` at a total income of ₹12,10,000
and tax of ₹61,500:
- the excess is 12,10,000 − 12,00,000 = 10,000;
- relief = 61,500 − 10,000 = **₹51,500**, never below nil;
- at a total income of ₹12,00,000 the income is not above the threshold, so the relief is **₹0**.

**A part of a year.** `DateRange` 2026-04-15 to 2026-06-10 within TY 2026-27:
- `CalendarMonths` counts April, May and June: **3**;
- `CompletedMonths` counts only May, the month lying wholly inside: **1**;
- `Days` counts 16 + 31 + 10 = **57**, both ends included.

### Rule packs and filing (W-357b9e, D-921)

**The timeline is the source.** The law is written once, across every year, under `rulepacks/timeline/`. Each year's pack is
compiled from it, so a regime that lives ten years is one file, not ten copies (`rules/timeline/Timeline.java:25` – `:26`).
`TimelineReader` reads one file per identity, its layer given by its directory. It refuses the whole timeline, naming the file, when a
file sits in a directory it does not know (`rules/timeline/TimelineReader.java:43` – `:55`; the directories `:59` – `:91`).

| Directory | Holds | Shipped files |
|---|---|---|
| `law/rules/` | one rule per file, every version | 248 |
| `law/rule-sets.json` | the sets nodes evaluate, each row naming its own | 1 |
| `law/regimes/`, `law/disallowances/` | `NEW_TAX_REGIME`, `FIRST_SCHEDULE_RATES`; their two shared disallowance sets | 2, 2 |
| `exec/pipelines/`, `exec/components/`, `exec/funds/`, `exec/declarations/` | nodes, pay components, employer funds, declarations | 30, 52, 3, 16 |
| `forms/itr-forms/`, `forms/itr-cells/`, `forms/certificates/` | `ITR-1`, `ITR-2`; the schedule cells; `FORM_130` | 2, 240, 1 |
| `content/` | explanations, the claim catalogue, the coverage register, the section catalogue (`sections/`), the Acts' section lists (`acts/`) | 1, 1, 1, 66, 2 |

**A pack has four layers** (`rules/pack/RulePackLayer.java:23` – `:48`), split by how often and on whose authority each changes:

| Layer | Holds | Changes a figure? |
|---|---|---|
| `LAW` | rates, caps, conditions, regimes, disallowance sets, versions | yes: the only layer that may change a number |
| `EXEC` | pipelines, components, funds, declarations | yes, by choosing which law applies |
| `FORMS` | return forms, schedules, cells, certificates | no |
| `CONTENT` | explanations, the claim catalogue | no |

The computation cache keys on `LAW` and `EXEC` alone (`RulePack.computationCacheKey()`, `rules/pack/RulePack.java:231`). An edit to
the claim catalogue therefore invalidates nothing.

**Compiling a year** (`rules/timeline/RulePackCompiler.java:70`):
- **What a year holds:** every `PUBLISHED` version whose validity overlaps the year, plus every published pending-notification version.
  A rule left with no version is omitted.
- **Projected components:** a component keeps only the rules the year holds, but a reference to a rule the timeline states in *no* year
  is refused first.
- **Canonical output:** keys sorted, no whitespace, `_`-prefixed note fields dropped. So a checksum moves when the law moves, not when a
  note does (`:56` – `:68`).

**The lock** is `rulepacks/<year>.lock.json`: each layer's version and checksum, a checksum per rule, and the year's governing
instruments (`rules/timeline/RulePackLock.java:37`). `verify` refuses a compiled year that differs from it, naming each rule that
moved (`:97` – `:121`). A rule can move three ways:
- "`X` changed: lock says …, timeline compiles to …";
- "`X` is in the timeline … but not in its lock";
- a layer's own checksum.

| Lock | law | exec | forms | content | rules |
|---|---|---|---|---|---|
| `2025-26.lock.json` | `2025-26.law.129` | `.exec.35` | `.forms.9` | `.content.30` | 244 |
| `2026-27.lock.json` | `2026-27.law.132` | `.exec.38` | `.forms.18` | `.content.31` | 241 |

**Loading.** `shipped-years.json` declares the years (`2025-26`, `2026-27`): declared, not scanned, because a year that silently
fails to appear is worse than one that fails to load. At startup `ShippedRulePacks.compile` does three things for each year
(`rules/timeline/ShippedRulePacks.java:39` – `:51`):
- compiles it;
- verifies it against its lock (`ClasspathRulePackProvider.load`, `rules/timeline/ClasspathRulePackProvider.java:85` – `:94`);
- assembles it into a `RulePack` with the instruments its own lock names (`JsonPackAssembler`).

A mismatch stops the boot. The timeline is read from a directory in the build, or from the JAR when shipped (`:112` – `:134`).

**Reading JSON** (`rules/json`): one reader per family, each a tagged union keyed on `"type"` (`ShapeJson`, `ConditionJson`,
`DefinitionJson`). Every failure is a `PackValidationException` naming the file and field (`PackJson`).

**Validation** has three layers:
- **Each record refuses itself** at construction.
- **The lock** refuses an unreviewed change at load.
- **`PackValidator`** (`rules/validate/PackValidator.java:59`) refuses a pack whose references do not resolve, and reports every
  failure together. Examples: a rule naming a missing component, a regime's missing disallowance set, two default regimes. It runs in
  the build (`RulePackLoadTest`, `TaxYear2025RulePackTest` and the synthetic pack's test), not on the load path; see W-393 (1).

**A rule's three status axes** (`rules/registry`):
- **`LegalStatus`:** `BILL`, `ENACTED`, `NOTIFIED`, `PENDING_NOTIFICATION`, `IN_FORCE` or `WITHDRAWN`.
- **`VerificationLevel`:** `UNVERIFIED` or `SOURCE_CHECKED`. This one reaches the person.
- **`PublicationStatus`:** `DRAFT`, `IN_REVIEW` or `PUBLISHED`. Only `PUBLISHED` enters a pack.

A rule's `Provenance` carries its source and how far it is confirmed, and is inside the rule's checksum. The `_note` fields are outside
it.

**Changing a year's law** passes three guards, each a test:
- the lock, rewritten by `docs/taxplanning-build/tools/run_relock.sh`, with a layer's version bumped once per item;
- the review file `reviews/pack-review-2025-26-to-2026-27.md`, written by `PackReview` and checked by `PackReviewTest`
  (`-Dpack.review.write=true` rewrites it). It lists `PackLint`'s findings, then each rule whose law changed at the boundary, then
  those only re-sourced;
- the generated constants `RuleIds`, `RuleSetIds`, `ComponentCodes` and `NodeIds` (`rules/generated`), regenerated from the
  timeline and checked by `GeneratedConstantsTest`. A code Java names is a constant, so a renamed code breaks its caller at compile
  time.

**Coverage** (`rules/coverage`): `content/coverage.json` classifies every section of each Act exactly once as `Modelled`,
`Planned`, `Deferred`, `NotComputational` or `OutOfScope` (`Coverage.java:15` – `:75`). The Act is part of a section's identity,
since the two Acts reuse numbers (`SectionRef.java:19`).

| Act | Modelled | Planned | Deferred | Not computational | Out of scope | Total |
|---|---|---|---|---|---|---|
| 1961 | 60 | 6 | 370 | 368 | 16 | 820 |
| 2025 | 57 | 6 | 233 | 236 | 3 | 535 |

**The catalogues** (`rules/catalogue`):
- **The section catalogue** (`content/sections/`, 66 entries) explains a section to a person: what it is, the routes that qualify, and
  what disqualifies, each linked to what enforces it (`EnforcedBy`).
- **The claim catalogue** (`content/claim-catalogue.json`) says how a deduction or exemption is claimed:
  - its channel, and what its absence from Form 16 means;
  - the steps, by whom and by when;
  - the evidence and what settles it.

  A rule that is not a claim says why (`NotAClaim`).

**Filing** (`rules/filing`):
- **`ItrFormSpec`** (`ItrFormSpec.java:33`): one form as a year's law defines it. It holds whom it is for, the kinds of return, its
  positive conditions and bars, the forms it excludes, how it may be furnished, and its schedules.
- **`ItrFormSelector`** (`ItrFormSelector.java:27`) runs the year's cascade:
  - the candidates are the forms serving the person and the kind of return, in `cascadeOrder`;
  - a form is excluded when an earlier form open to the return is one it excludes;
  - the first open form is chosen, unless an earlier one waits on an unanswered question, when none is.

  Each form gets a `FormVerdict`: `ELIGIBLE`, `INELIGIBLE`, `EXCLUDED` or `UNDETERMINED` (`FormVerdict.java:30`). No form name is
  in Java.
- **`ScheduleMapper`** (`ScheduleMapper.java:37`) fills a form's cells in one fold. Each `ScheduleCell` says where its figure comes from,
  as a `FieldSource` (`FieldSource.java:16`). A source can be:
  - nodes, the tax basis, rules or a declaration;
  - capital-gains parts or buckets, an item's figure, other-source kinds, set-off or an accrual;
  - a sum;
  - a manual entry.

  A cell the run cannot fill carries its reason, never a blank or a zero.
- **`CertificateSpec`** (`CertificateSpec.java:18`): a certificate's printed layout (Form No. 130). It lists parts by heading, lines by
  label and tables by column. It also states the sums the form prints, the amounts that should be near each other, and the amounts the
  engine also computes.

**The synthetic pack** (`backend/src/test/resources/rulepacks-synthetic/`): three made-up years, 2099-00 to 2101-02, each with a lock. It
is not law and never shipped; it proves the engine computes a law no shipped year has (§11, W-146).

### Worked examples: rule packs (by hand)

**A note does not move a checksum.** A rule file's `_note` is dropped from the canonical form, so rewording it leaves the rule's checksum,
the `LAW` layer's and the lock as they were. Its `provenance.sourceDescription` is kept, so rewording that moves all three and needs a
relock. This is why W-357b9e re-points no pack citation (W-393 (4)).

**The cascade.** Take a resident individual who has answered every question and who is open to both forms, `ITR-1` coming first in
`cascadeOrder`. If `ITR-2` lists `ITR-1` in its `excludes`:
- `ITR-1` is `ELIGIBLE` and chosen;
- `ITR-2` is `EXCLUDED` by `ITR-1`, without its conditions being tested.

If the person had left a question `ITR-1` reads unanswered, `ITR-1` would be `UNDETERMINED` and no form would be chosen, though `ITR-2`
was open.

### Elections and the optimiser (W-357b9f, D-922)

**An election** is a choice the law leaves to the person that changes the tax (`election/Election.java:22` – `:50`). The family is
closed: `RegimeElection`, `HouseElection` and `SetOffOrderElection`. Each one says:
- the rule a choice's priced line cites (`basis`) and the node whose figure it changes;
- its candidates, the declared default first;
- the choice the person stated, if any;
- the inputs with a choice made, and the regime a choice is computed under (the one being computed, unless the choice *is* the regime).

| Election | The choice | Candidates |
|---|---|---|
| `RegimeElection` (`election/RegimeElection.java:24`) | the regime the tax is computed under | the regimes `RegimeOffers` makes available, the default first |
| `HouseElection` (`election/HouseElection.java:34`) | which houses have a nil annual value | every set of at most `HP-OWN-RESIDENCE`'s number of houses not let; the specification as entered first, then larger sets before smaller (`:24` – `:32`) |
| `SetOffOrderElection` (`election/SetOffOrderElection.java:29`) | the order the other heads absorb a head's loss | every order of the heads in `SETOFF-ACROSS-HEADS`'s `AbsorptionOrder`, that order first. Neither Act prescribes one, and the Tribunal has held the choice the assessee's (`:21` – `:28`) |

**`RegimeOffers`** (`election/RegimeOffers.java:31`) answers each regime as `Available` or `Barred` with its reason (`RegimeOffer.java:19`,
`:39`), never a silent omission. A regime is offered when all of these hold (`RegimeOffers.java:20` – `:29`):
- it applies to the person type and is in force in the year;
- it is open to new entrants, or the person is already in it;
- every eligibility set is met.

A regime whose binding applies to the person is refused. Its binding needs business income, which nothing computes, so a computable
person always has the free annual choice.

**Resolving by recomputation** (`election/ElectionResolver.java:27` – `:44`), never by a heuristic:
- **`resolve`** (`:86` – `:118`): a stated choice is computed once and applied as stated (`ElectionMode.USER_SPECIFIED`). Otherwise
  every candidate is a whole `PipelineRunner.run`, and the least `tax.netpayable` wins (`AUTO_OPTIMISE`).
- **Ties** go to the earlier candidate (a strict `isLessThan`), so to the default.
- **Every alternative is kept** as an `ElectionOutcome`'s `PricedAlternative`s, and written as a MEASURE line of the election's basis rule
  under its node in the chosen run's trace. The outputs are projections of the trace.
- **`resolveTogether`** (`:125` – `:154`) prices several elections over every combination of their choices, as whole runs, since they
  are not independent: the regime changes what a house specification is worth. Combinations are counted in mixed radix, the last
  election varying fastest (`:193` – `:202`); a tie goes to the earlier combination.

**Where elections run today:**
- `TaxCalculator.compareRegimes` resolves the regime election (`api/TaxCalculator.java:267` – `:272`).
- The optimiser resolves it for every allocation it prices (`optimise/AllocationPricing.java:482` – `:490`).
- `compute` runs one pipeline under the inputs' regime (`api/TaxCalculator.java:100` – `:102`).
- `HouseElection`, `SetOffOrderElection` and `resolveTogether` have no caller outside tests, so the app computes houses as entered and
  the declared absorption order (W-394 (1)).

**The salary optimiser** (`optimise/SalaryOptimiser.java:19` – `:26`, `:45` – `:62`) re-divides the movable pay of one employment
within its cost to company. The caller's `AllocationSpace` states:
- what may move: pay components, the employer's fund contributions, and the employee's own contributions above what is entered;
- the residual component that funds the moves;
- a cash floor;
- how many components the final exhaustive check covers;
- the provident fund's terms.

The algorithm:
1. **Candidates per component:** nil, the amount entered, and its **saturation point** under each regime offered.
   - `Saturation` finds the least whole-rupee amount past which one more rupee no longer lowers total income. It measures by bisection
     rather than by a formula, so the law is read only through the pack (`optimise/Saturation.java:8` – `:27`).
   - An employee's own contribution is measured differently: as the least addition at which total income reaches its level at the
     ceiling. Its rules need not be convex.
2. **A greedy fill.** It takes the move that saves the most tax per ₹10,00,000 moved, and re-prices every move after each one, because
   components interact.
3. **A bounded exhaustive check** over the `verificationBreadth` components whose first move saved most. It catches a greedy miss.
4. **A refill.** Each addition is re-measured at the answer, cut where it over-fills, and raised only where the floor still holds.

Every price is a full computation with the regime chosen inside it (`AllocationPricing`): the objective is evaluated, never modelled.
`Following` moves the provident fund contributions with their base at the ratio the user entered, so no provident fund rate sits in
Java. An allocation whose cash after tax falls below the floor is not taken; pay that is already below it is refused.

**The pricers**, each a second run against the first:

| Class | Prices | How |
|---|---|---|
| `DeductionRoomPricing` (`optimise/DeductionRoomPricing.java:59`, `:94`) | the room left under a deduction's ceiling, and its saving | Schedule XV: the ceiling less what was allowed, paid into the first contributor without a sub-cap. Health (s.126): filled with a premium as large as the largest ceiling, for the assessee and for the living parents. Own NPS: rupee for rupee |
| `RentRoomPricing` (`optimise/RentRoomPricing.java:60`, `:80`) | what rent could still earn: HRA, or s.134 rent paid | every rented home's rent raised so no rent limb binds, so the other limbs set the figure; with no home, one assumed for the year per location band. The rent needed is read from the row's own rent limb |
| `ClaimPricing` (`optimise/ClaimPricing.java:27`, `:44`) | each claim's worth | the run with the claim's whole catalogue entry withheld (its rule and the rules it covers), side by side. An entry with no deduction line is served unpriced |
| `RoomFilling` (`optimise/RoomFilling.java:21`, `:26`) | the bill with every chosen room filled at once | one run, so overlapping rooms (own NPS and the investment ceiling) count once: the low end of a coarse figure's band |

`taxplanning` reaches all of these through `TaxCalculator`: `compareRegimes`, `regimeElection`, `optimise`, `priceDeductionRoom`,
`priceRentRoom`, `priceClaims` and `billWithRoomsFilled` (see §3).

### Worked examples: elections and the optimiser (by hand)

**A tie.** Suppose `NEW_TAX_REGIME` (the default, offered first) and `FIRST_SCHEDULE_RATES` both give a net payable of ₹50,000.
`resolve` keeps the first unless a later one is strictly less, so the choice is `NEW_TAX_REGIME`, `AUTO_OPTIMISE`, with both
alternatives priced.

**Joint combinations.** Two elections, a house election with 2 choices and a set-off order with 3, give a radix of [2, 3] and 2 × 3 = 6
whole runs:
- the order is [0,0], [0,1], [0,2], [1,0], [1,1], [1,2];
- combination 4 is: 4 mod 3 = 1, then 1 mod 2 = 1, so [1, 1].

**A move's rate.** Moving ₹50,000 that saves ₹15,000 is worth 15,000 × 10,00,000 ÷ 50,000 = **₹3,00,000 per ₹10,00,000 moved**. A move
of ₹1,00,000 saving ₹20,000 is worth ₹2,00,000, so the greedy fill takes the first.

## 6. Configuration

None of the engine's own. The packs it compiles are the classpath directory `rulepacks`, and the years are `shipped-years.json`'s
(`config/TaxEngineConfig.java:24`, `:26` – `:29`).

## 7. Tests

| Class | Cases |
|---|---|
| `TaxCalculatorOptimiseTest` | 46 |
| `ExplanationRendererTest` | 43 |
| `NodeRegistryTest`, `NodeContextRuleSetTest` | 23 each |
| `TaxComputationTest` | 21 (its record has no date component, asserted structurally) |
| `ItrFormChoiceTest`, `TraceLineTest`, `TopologicalSortTest`, `PipelineRunnerTest` | 20, 19, 18, 18 |
| `NodeValueTest`, `Form130LayoutTest`, `AllocationSpaceTest` | 13, 13, 11 |
| `ComputationProjectorTest`, `RegimeVerdictTest`, `PipelineRecomputationTest` | 7 each |
| `TaxCalculatorUnshippedYearTest`, `TaxCalculatorDeclarationsTest`, `NodeContextDeclarationsTest` | 6 each |
| `ReturnFactsProjectionTest`, `TaxCalculatorPackIdentityTest`, `CitedSectionsTest`, `NodeResultTest`, `IndexedGainTest`, `RegimeElectionTest`, and nine more | 1 – 5 each |

**The nodes** (76 files under `taxengine/node` and `taxengine/golden`):

| Class | Cases |
|---|---|
| `OtherSourcesNodeTest`, `RetirementNodeTest`, `SetOffNodeTest`, `CapitalGainsNodeTest` | 82, 53, 37, 35 |
| `MotorCarPerquisiteTest`, `ChapterEightNodeTest`, `SurchargeNodeTest`, `PerquisiteNodeTest` | 28, 27, 24, 22 |
| `LeaveTravelExemptionTest`, `HousePropertyInterestTest`, `HealthInsuranceDeductionTest`, `TaxByBasisNodeTest`, `SalaryHeadPipelineTest` | 22, 22, 22, 21, 21 |
| the goldens `T1` – `T6` (`T1SlabAndRebateTest` and the rest) | `DR 08`'s figures, copied into the case files, never computed |
| about fifty more | per node and per helper |

**The inputs** (49 files under `taxengine/input`, 206 cases):

| Class | Cases |
|---|---|
| `InputsFingerprintTest`, `AssesseeTest` | 25 each |
| `RegimeElectionSnapshotTest`, `EngineInputsTest` | 19, 8 |
| one test per input record | its refusals, through `InputRefusals` |

No test in this part cites a plan or review (grep).

**The rules vocabulary** (50 files under `taxengine/rules`'s twelve vocabulary packages, 518 cases; the packs, JSON and validation
tests are W-357b9e's):

| Package | Files | Cases |
|---|---|---|
| `cap` | 8 | 134, `CapShapeTest` 40 of them |
| `condition` | 9 | 116, `ConditionTest` 65 |
| `regime` | 13 | 73, `RegimeLifecycleTest` 43 |
| `money`, `definition`, `id`, `temporal`, `component`, `relief`, `declaration` | 2, 10, 2, 2, 2, 1, 1 | 50, 46, 32, 30, 22, 11, 4 |

`rounding` and `reason` have no test class of their own: `MoneyTest`, `RuleJsonRoundingTest` and `RoundingNodesTest` cover the
rounding, and 44 test files assert reasons. `ReliefShapeTest` cited `plans/W-048.md` for its figures; they are now §11's W-048 block.

**Rule packs and filing** (59 files, 705 cases):

| Package | Files | Cases |
|---|---|---|
| `json` | 11 | 182 (`PackJsonTest` 56, `RuleJsonDefinitionTest` 42) |
| `timeline` | 14 | 111 (`PackReviewTest` checks the committed review file) |
| `pack` | 10 | 108 (`RulePackLoadTest`, `TaxYear2025RulePackTest` load both shipped years and run `PackValidator`) |
| `filing` | 7 | 106 |
| `validate` | 8 | 82 (`PackValidatorTest` 44) |
| `catalogue`, `coverage`, `registry`, `generated` | 2, 3, 2, 2 | 60, 23, 18, 15 |

Five tests cited plans; their figures and surveys are now §11's W-052c, W-055, W-079a, W-139 and W-139a blocks.

**Elections and the optimiser** (10 files, 68 cases):

| Package | Files | Cases |
|---|---|---|
| `election` | 3 | 25 (`ElectionResolverTest` 15, `RegimeOffersTest` 7, `HouseElectionTest` 3) |
| `optimise` | 7 | 43 (`RentRoomPricingTest` 12, `DeductionRoomPricingTest` 10, `SaturationTest` 7, `AllocationPricingTest` 5, `ClaimPricingTest` 4, `ClaimFinalAmountTest` 3, `FollowingTest` 2) |

The elections run whole pipelines in `golden/` too: `RegimeElectionPipelineTest`, `HouseElectionPipelineTest`,
`SetOffOrderElectionPipelineTest`. No test in this part cites a plan or review (grep).

Counts are `@Test` / `@ParameterizedTest` annotations per file (grep), under `taxengine/api`, `pipeline`, `result` and `trace`.

## 8. Known gaps (W-389 – W-394)

1. **A legal list in Java.** `dueBy` accepts an election deadline only "under" s.263(1) or s.139(1), the return's due date, from a Java
   constant (`api/TaxCalculator.java:310` – `:320`, `:651` – `:652`). CLAUDE.md §4: every qualifying list is tax-year-versioned rule
   data. It is not in the law-in-code audit (grep of `reviews/2026-10-21-law-in-code-audit.md`).
2. **The `result` named interface is used by no other module** (grep: no import of `taxengine.result` outside the module). Its reason,
   "so taxplanning can store and serve results" (`result/package-info.java`), no longer holds: `taxplanning` stores `api.ComputedTax`.
   **`FilingPosition`** is built nowhere in `src/main` (grep), and its Javadoc says the types it waits for do not exist, though
   `InterestAndFees` does (`result/FilingPosition.java:20` – `:31`).
3. **Javadoc that is no longer true:**
   - "Today `input` and `result`" for the named interfaces, where there are five (`package-info.java:11` – `:14`).
   - I3 "is not yet wired to the shipped pack … no `ComputationNode` implementations until Phases 5 to 9"
     (`pipeline/NodeRegistry.java:128` – `:132`), where 30 nodes ship and `T1SlabAndRebateTest:106` runs it.
   - `ComputationNode#rules()`, which does not exist (`pipeline/NodeContext.java:50` – `:53`).
   - "For now … building those is W-043" (`PipelineRunner.java:48` – `:54`).
   - "No named interface exposes it until a module first calls the engine" (`config/TaxEngineConfig.java:13` – `:14`).
   - The receipt example `TXC-2026-9F3A21`, which its own pattern refuses: 9 and 1 are not base32 (`ComputationReceipt.java:8`, `:27`).
4. **`TaxCalculator` has many reasons to change:** 691 lines, about thirty public methods across computing, pricing, reading law and
   words, and auditing (CLAUDE.md §4: one reason per class). It spells `java.util.HashMap` and `java.util.Comparator` in full
   (`TaxCalculator.java:161`, `:171`, `:185`, `:200`, `:218`, `:236`, `:245`, `:467`).

**The nodes (W-390):**
5. **The business and profession head is not computed.** No node produces it (`GrossTotalIncomeNode.java:17`), and advance tax assumes
   none in applying s.403(3)(a) / s.207(2)(a) (`AdvanceTaxNode.java:41`). A person with business income gets a bill without it, and no
   refusal says so. Pack rows already defer to "the business head" (W-078c, W-078j3b).
6. **Javadoc that is no longer true:**
   - "The Chapter VIII deductions built so far: the s.123 family … and the pension scheme" (`ChapterEightNode.java:43`): the node also
     runs health insurance, disability, donations, the loans, rent paid, deposit interest and the Agniveer fund.
   - `PerquisiteNode`'s "Built so far" list (`PerquisiteNode.java:46`) likewise.
7. **Two plans stay** after W-357b9b1 moved the nodes' hand-worked figures into §11 (D-918) and W-357b10b re-homed the three
   `taxplanning` tests: `plans/W-112b.md`, cited by two pack files and a synthetic pack (kept by W-357b9e; see gap 13, W-393 (4)), and
   `plans/W-089.md` (tag `docs-history-m3`), still cited by `plans/W-112b.md:58`.

**The inputs (W-391):**
8. **Javadoc that is no longer true:**
   - `InputsFingerprint` says "today `EnteredFacts` is empty and the income side fills it over Phases 5 to 9"
     (`input/InputsFingerprint.java:35` – `:38`), where it has 40 components.
   - `EngineInputs` says `EnteredFacts` is "declared and empty", and that there is no `fingerprint()` method because
     `InputsFingerprint` is "W-038's … next" (`input/EngineInputs.java:39` – `:43`). `InputsFingerprint` exists.
   - `asOfDate()` is documented as "the as-of instant" but returns a date, written `java.time.LocalDate` in full (`:99` – `:102`).

**The rules vocabulary (W-392):**
9. **`CapShape`'s Javadoc is out of place.** `countsRead`'s comment ("The ids whose counts this cap reads …") sits at
   `rules/cap/CapShape.java:58` – `:62`, directly above `raisingInput`'s own (`:63` – `:66`). So `countsRead` (`:108`) has none, and
   a reader of `raisingInput` meets two.

**Rule packs and filing (W-393):**
10. **`PackValidator` runs only in the build,** in `RulePackLoadTest`, `TaxYear2025RulePackTest` and the synthetic pack's test.
    `ShippedRulePacks.compile` verifies the lock and assembles, and never validates. Yet three comments speak of load or startup:
    - `PackValidator`'s Javadoc: "none of these throws at load unless something looks", "one startup at a time"
      (`rules/validate/PackValidator.java:39`, `:42` – `:43`);
    - `PackValidationException`'s: "Thrown at load time" (`rules/validate/PackValidationException.java:6`).
11. **Every compiled pack writes an empty `axes.bindings`** into `exec/pipelines.json` (`rules/timeline/RulePackCompiler.java:108`),
    which nothing reads. `RulePackLayer` lists "temporal-axis bindings" as `EXEC`'s (`rules/pack/RulePackLayer.java:33`), but each rule
    declares its own axis. Removing the field moves both years' `EXEC` checksums.
12. **`Timeline`'s Javadoc on `pipelineNodes` is stale** (`rules/timeline/Timeline.java:48` – `:49`). It says the item that generates
    node ids from the catalogue "gives it a type". `NodeIds` is generated from it (`ConstantGenerator.java:48`, under `test`), and it
    is still each node's file verbatim.
13. **Pack data still cites two plans** (a proposal). `INT-REFUND`'s `provenance.sourceDescription` cites `plans/W-112b.md`, and that
    field is inside the rule's checksum, so re-pointing it needs a relock and a `LAW` bump in both years. Its `_note`,
    `SEC-INTEREST-REFUND`'s `_sources` and the synthetic pack's copy cite the same plan. `DED-DONATION-UNREPORTED`'s `_note` (shipped
    and synthetic) cites `plans/W-078f.md`, which `plans/W-156a.md` (tag `docs-history-m3`) also cites. Both plans therefore stay. The proposal: move W-112b's
    act-chain record into `sources/` and re-point all four, in one relock.

**Elections and the optimiser (W-394):**
14. **Two elections are built and never run in the app.** `HouseElection`, `SetOffOrderElection` and
    `ElectionResolver.resolveTogether` are called only by tests (`golden/HouseElectionPipelineTest`,
    `SetOffOrderElectionPipelineTest`, `EngineBenchmarkTest`, `GoldenCaseRunner`, `election/`).
    - `TaxCalculator.compute` runs one pipeline under the inputs' regime (`api/TaxCalculator.java:100` – `:102`).
    - Only the regime election is resolved: by `compareRegimes` (`:267` – `:272`) and inside the optimiser
      (`optimise/AllocationPricing.java:482` – `:490`).

    So the app computes a person's houses as entered and the loss absorption order as declared, never priced, though D-230, D-221 and
    D-236 built both elections to choose the cheaper. The owner decides whether to wire them in or remove them.

## 9. Not covered here

- What `taxplanning` does with an answer: `docs/modules/taxplanning.md` (W-357b10).

## 11. Hand-worked figures the tests cite

Moved here from their plans by W-357b9b1 (D-918), W-357b9d (D-920, W-048) and W-357b9e (D-921: W-052c, W-055, W-079a, W-139, W-139a), so each test cites this doc rather than a plan file. Each block is the plan's own text,
verbatim: its numbers, cases and derivations, as the plan stated them before the build. A build may have departed from a plan; where it
did, the test's own comments and the D-row say so. The plans' full text, choices and build records stay in git history:
`git show 88e5bd2d:docs/taxplanning-build/plans/<plan>.md` (W-048: `87e9e60f`; W-357b9e's five: `79f8111b`).

### W-046 — TaxByBasisNode + TaxByBasisNodeTest (Phase 5 opens)

Cited by `TaxByBasisNodeTest`.

#### Numbers (each derived by hand here, not by running the code)

| Case | Figure | Derivation | Source |
|---|---|---|---|
| New regime, TI ₹12,00,000 | **₹60,000** | 4L–8L at 5% = 20,000; 8L–12L at 10% = 40,000 | S2 s.202(1) Table; `DR 23` A11 |
| New regime, TI ₹4,00,000 | **₹0** | at the top of the nil band | S2 s.202(1) |
| New regime, TI ₹24,00,001 | **₹3,00,000.30** | 20,000 + 40,000 + 60,000 + 80,000 + 1,00,000 = 3,00,000; plus 30% of ₹1 = 0.30. No rounding in the node (`RATE-ROUND-003`) | S2 s.202(1) |
| First Schedule rates, TI ₹10,00,000 | **₹1,12,500** | 2.5L–5L at 5% = 12,500; 5L–10L at 20% = 1,00,000 | FA 2026 First Sched. Part I-B Para A (I): *"₹ 112500 plus 30% of the amount by which the total income exceeds ₹ 1000000"* |

### W-048 — the `ReliefShape` sealed family

Cited by `ReliefShapeTest`.

#### Numbers (each derived by hand; law figures from S2 s.156)

| Case | Expected relief | Derivation |
|---|---|---|
| `FlatWithCeiling(60000, 1200000)`, TI ₹12,00,000, tax ₹60,000 | **₹60,000** | TI ≤ ceiling; min(60,000, 60,000) |
| same, TI ₹12,00,001 | **₹0** | above the ceiling |
| `FlatWithCeiling(12500, 500000)`, TI ₹4,90,000, tax ₹12,000 | **₹12,000** | min(12,000, 12,500): "100% … or ₹12500, whichever is less" |
| `ExcessOverThreshold(1200000)`, TI ₹12,10,000, tax ₹61,500 | **₹51,500** | 61,500 − (12,10,000 − 12,00,000) = 51,500. Tax = 60,000 + 15% × 10,000, by the s.202(1) slab |
| same, TI ₹13,00,000, tax ₹75,000 | **₹0** | the excess 1,00,000 ≥ tax 75,000 |
| same, TI ₹12,00,000 | **₹0** | not above the threshold |
| `ThresholdLadderCeiling([5000000])`, TI ₹50,00,100, tax + surcharge **₹11,88,033**, `U` = **₹10,80,000** | **₹1,07,933** | ceiling = 10,80,000 + 100 = 10,80,100; 11,88,033 − 10,80,100. **Constructed inputs:** U is the new-regime tax at ₹50L (`RulePackLoadTest`'s pinned figure); 11,88,033 = (10,80,000 + 30% × 100) × 1.10. The 10% rate is illustrative, since surcharge ladders are `W-054`'s |
| same, no `unrelievedAt` | **refused** | A2 |
| `NoRelief` | **₹0** + reason | |

### W-050 — `RoundTotalIncomeNode` and `RoundNetPayableNode`

Cited by `RoundingNodesTest`.

#### Numbers

**Copied from `DR 08` / `DR 13`** (payable → rounded):

| Case | In | Out | Source line |
|---|---|---|---|
| T1-N2 | 10.40 | **10** | "RATE-ROUND-002: ignore paise → 10; already a multiple of 10" |
| T1-N4 (a) | 73,403.20 | **73,400** | "round: → 73,403 → last figure 3 < 5 → down" |
| T1-N4 (b) | 73,412.04 | **73,410** | "→ 73,412 → last figure 2 < 5 → down" |
| T1-O2 | 13,002.08 | **13,000** | "→ 13,002 → last figure 2 < 5 → down" |
| T2-2 (a) | 12,90,837.60 | **12,90,840** | "→ 12,90,837 → last figure 7 ≥ 5 → up" |
| T2-2 (b) | 12,90,843.84 | **12,90,840** | "→ 12,90,843 → last figure 3 < 5 → down" |
| T13-14 | refund 1,234.99 | **1,230** (sign restored for −1,234.99) | "Paise ignored → 1,234 → last figure 4 → ₹1,230" |

**Total income, derived by hand from s.516** (`DR 08`'s T1 incomes are all already round):

| In | Out | Derivation |
|---|---|---|
| 12,00,004.60 | **12,00,000** | paise ignored → 12,00,004; last figure 4 < 5 → down. Rounding to the rupee first would give 12,00,010 |
| 12,00,005 | **12,00,010** | last figure 5 ≥ 5 → up |
| 12,00,000 | **12,00,000** | already a multiple of ten; the line is still emitted |

### W-051 — `CessNode`

Cited by `CessNodeTest`.

#### Numbers (copied from `DR 08`; post-rebate tax → cess, surcharge nil in every case)

| Case | Tax after rebate | Cess | `DR 08` line |
|---|---|---|---|
| **T2-1** | 10,80,000 | **43,200** | "cess 4% × 10,80,000 = 43,200" (surcharge 0 at the threshold) |
| T1-N1 | 0 | **0** | "RATE-CESS-001: 4% × 0 = 0" |
| T1-N2 | 10.00 | **0.40** | "cess 4% × 10 = 0.40" — charged on 10, not on the pre-rebate 60,001.50 |
| T1-N3 | 50,000 | **2,000** | "cess 4% = 2,000" |
| T1-N4 | 70,580.00 / 70,588.50 | **2,823.20 / 2,823.54** | "cess 4% = 2,823.20"; "= 2,823.54" |
| T1-O2 | 12,502 | **500.08** | "cess 4% = 500.08" |

T1-N2 is the `REBATE-ORDER-001` check: charging the cess before the rebate gives 2,400.06 (4% × 60,001.50),
not 0.40.

**By hand (finding 3):** after the rebate, slab tax 0 plus special-rate tax 1,00,000 (an arbitrary
figure) → cess **4,000**: 4% of the whole income-tax, not of the slab part alone.

### W-052 — `NetPayableNode`

Cited by `NetPayableNodeTest`.

#### Numbers (copied from `DR 08`: tax after rebate + cess = "total", before rounding)

| Case | Tax after rebate | Cess | Net payable | `DR 08` line |
|---|---|---|---|---|
| **T1-N1** | 0 | 0 | **0** | "tax after rebate = 0 … RATE-CESS-001: 4% × 0 = 0" |
| **T1-N3** | 50,000 | 2,000 | **52,000** | "total = 52,000" |
| T1-N2 | 10.00 | 0.40 | **10.40** | "total = 10.40" |
| T1-N4 | 70,580.00 / 70,588.50 | 2,823.20 / 2,823.54 | **73,403.20 / 73,412.04** | "total = 73,403.20"; "= 73,412.04" |
| T1-O2 | 12,502 | 500.08 | **13,002.08** | "total = 13,002.08" |
| T2-1 | 10,80,000 | 43,200 | **11,23,200** | "total = 11,23,200" |

**By hand:** after rebate, slab 0 plus special-rate 1,00,000, with cess 4,000 → **1,04,000** (the whole
tax, not the slab part).

### W-052c — TY 2025-26 on the timeline for the Phase 5 rules

Cited by `TaxYear2025RulePackTest`.

- **Sources read this session:**

  | Rule(s) | TY 2025-26 law | Same figures as 2026-27? |
  |---|---|---|
  | `RATE-SLAB-NEW-TAX-REGIME` | **FA 2025 s.25** (p.25) inserts s.115BAC(1A)(iii), "for any previous year relevant to the assessment year beginning on or after the 1st April, 2026": the same seven bands | yes |
  | `RATE-SLAB-FIRST-SCHEDULE`, `-AGE-60TO79`, `-AGE-80` | **FA 2026 First Schedule Part I-A Para A (I)–(III)** (p.80): the same bands; the age test says "previous year" where Part I-B says "tax year" | yes |
  | `REBATE-NEW-TAX-REGIME`, `-EXCESS` | **FA 2025 s.20** (p.23–24), w.e.f. 1-4-2026: 1961 s.87A first proviso (a)/(b) → 12 lakh / ₹60,000; the second proviso limits it to s.115BAC(1A) rates | yes |
  | `REBATE-RESIDENT-INDIVIDUAL` | **1961 s.87A** main text (thc.nic.in copy): "individual resident in India … does not exceed five hundred thousand … twelve thousand and five hundred … whichever is less". The figures were set by FA 2019 s.8 and are unamended since (`W-045n` Q1) | yes |
  | `RATE-ROUND-TOTAL-INCOME`, `-PAYABLE` | **1961 ss.288A, 288B** (thc.nic.in p.822, SOURCE_CHECKED at `W-050`) | yes |
  | `RATE-HEALTH-EDUCATION-CESS` | **FA 2026 s.2(6)** (p.9), "four per cent." | yes |

- **The 1961 Act instrument** (thc.nic.in copy): *"ACT NO. 43 OF 1961 [13th September, 1961.]"*; s.1(3)
  *"it shall come into force on the 1st day of April, 1962"*. No gazette reference was read. **FA 2025:**
  *"[29th March, 2025.]"*, CG-DL-E-29032025-262125.

#### Numbers (copied from `DR 08`, which runs T1 in both years; node-level, TY 2025-26)

| Case | Node | Expected |
|---|---|---|
| T1-N1 new, TI 12,00,000 | tax by basis → rebate | 60,000 → **0** |
| T1-N3 new, TI 12,50,000 | tax by basis → rebate | 67,500 → **50,000** |
| T1-O2 First Schedule, TI 5,00,010 | tax by basis → rebate | 12,502 → **12,502** |
| T1-N3 | cess on 50,000 | **2,000** |
| T1-N2 | round 10.40 | **10** |

### W-055 — surcharge marginal relief as pack rows, both regimes, both years

Cited by `SurchargeReliefTest`.

#### Numbers

**Thresholds, copied from FA 2026 (data, not computed):**

| Row | `thresholds` | `limitedToSlabRateTax` |
|---|---|---|
| `RATE-SUR-FIRST-SCHEDULE-CEILING` | 5000000, 10000000, 20000000, 50000000 | false |
| `RATE-SUR-NEW-TAX-REGIME-CEILING` | 5000000, 10000000, 20000000 — **no 5Cr** | false |

**Test expectations, derived by hand here (never from the code).** U is supplied by hand, as the node
will in `W-056`.

- **First schedule, TY 2026-27, TI ₹1,00,05,000** (slabs FA 2026 Part I-B Para A (I), p.84: nil to
  2.5L, 5% to 5L, ₹12,500 + 20% to 10L, ₹1,12,500 + 30% above):
  - tax = 1,12,500 + 0.30 × 90,05,000 = 28,14,000;
  - surcharge 15% (band > 1Cr) = 4,22,100; unrelieved = **32,36,100**;
  - U at 1,00,00,000 = tax (1,12,500 + 0.30 × 90,00,000 =) 28,12,500 + surcharge 10% (band
    (50L, 1Cr]) 2,81,250 = 30,93,750;
  - V = 5,000; W = 30,98,750; **relief = 32,36,100 − 30,98,750 = 1,37,350**.
  - This proves it picks 1Cr, not 50L. At 50L, U = 13,12,500 and V = 50,05,000, so W = 63,17,500 and
    the relief would be 0.
- **New regime, TY 2026-27, TI ₹2,00,10,000, no special income** (slabs from the shipped
  `RATE-SLAB-NEW-TAX-REGIME`, the same in both versions, and rulebook `:381-389`: ₹3,00,000 at 24L,
  30% above):
  - tax = 3,00,000 + 0.30 × 1,76,10,000 = 55,83,000;
  - surcharge 25% (the greater of the ladders' rates: excluding > 2Cr → 25%) = 13,95,750;
    unrelieved = **69,78,750**;
  - U at 2,00,00,000 = tax 55,80,000 + surcharge 15% 8,37,000 = 64,17,000;
  - V = 10,000; W = 64,27,000; **relief = 5,51,750**.
- **New regime, TI ₹5,00,10,000:** the greatest threshold exceeded is **2Cr** (there's no 5Cr), so U is
  asked for at 2,00,00,000. The test asserts U's argument, not an amount.
- Each case runs in **both years**: the same row, since the version spans them.

### W-079a — startup wiring (the Boot JAR probe)

Cited by `ClasspathRulePackProviderTest`.

#### A probe I ran (scratch only, nothing in the repo)

I packaged the JAR (`./mvnw package -pl backend -am -DskipTests`) and ran a 20-line class inside it through
`PropertiesLauncher` (`-Dloader.path`):

```
url=jar:nested:/Users/…/backend/target/wealthlens.jar/!BOOT-INF/classes/!/rulepacks/timeline
Path.of worked: class jdk.nio.zipfs.ZipPath
files=288                      (288 files under src/main/resources/rulepacks/timeline — equal)
coverage bytes=163195 rel=content/coverage.json sep=/
```

So inside the Boot JAR, `Path.of(url.toURI())` already gives a walkable zip path, and the reader needs no change.
A plain (non-Boot) JAR has no filesystem open for its `jar:file:` URI, so there `Path.of` throws
`FileSystemNotFoundException` until `FileSystems.newFileSystem(uri, Map.of())` opens one.

### W-088b — special-rate tax on capital gains

Cited by `TaxByBasisNodeTest`.

#### Numbers I will use, and where each comes from

| Value | Source |
|---|---|
| 20% (s.196 / s.111A part) | S2 s.196(1)(i), Gazette 265620 p.220; 1961 s.111A(1)(i)(b) as substituted by FA (No. 2) 2024 s.29, Gazette 256436 p.26 |
| 12.5% (s.197 / s.112 part) | S2 s.197(1)(b), p.221; 1961 s.112(1)(a)(ii)(B), (d)(ii)(B), FA (No. 2) 2024 s.30, pp.26–27 |
| 12.5% above ₹1,25,000 (s.198 / s.112A part) | S2 s.198(2)(a), p.222; 1961 s.112A(2)(i)(b), FA (No. 2) 2024 s.31, pp.27–28 |
| The maximum amount not chargeable | **Not a new number.** It is the upper bound of the zero-rate band of the ladder `TaxByBasisNode` already chooses (`RATE-SLAB-NEW-TAX-REGIME`, `RATE-SLAB-FIRST-SCHEDULE*`) |

#### How I will verify

`./mvnw test -pl backend -Punit -Dtest='TaxByBasisNodeTest,ChapterEightNodeTest,TotalIncomeNodeTest'`, then the unit
tier, the integration tier, and `spotless:apply`. Expected values are worked by hand here:

- **A** (TY 2025-26, old regime, resident aged 40):
  - slab income 1,50,000; s.111A part 50,000; s.112 part 80,000; s.112A part 2,00,000;
  - basic exemption 2,50,000, so the shortfall is 1,00,000. Taken in order: s.111A to nil (50,000 left), s.112 to
    30,000;
  - tax: slab 0; s.111A 0; s.112 3,750; s.112A (2,00,000 − 1,25,000) × 12.5% = 9,375.
- **B:** the same facts, non-resident. No shift: s.111A 10,000; s.112 10,000; s.112A 9,375; slab 0.
- **C:** T13-1's facts (TY 2026-27, new regime): slab 0; s.198 part 84,375 (OQ-33 default).
- **D** (Chapter VIII, TY 2025-26, old regime):
  - salary income 1,00,000; s.112A part 5,00,000; s.80C claims 1,50,000;
  - the deduction is limited to 1,00,000 (reduced GTI), not 1,50,000;
  - total income 5,00,000.
- **E:** a slab-only case returns exactly what it does today, with no special entries.

**Bite probes** (each predicted to fail, then reverted):
1. the shift disabled → A fails;
2. Chapter VIII bounded by plain GTI → D fails;
3. the ₹1,25,000 band removed → A, B and C fail.

### W-088c — the surcharge split

Cited by `SurchargeNodeTest`.

#### Numbers

| Value | Source |
|---|---|
| 15% limit | FA 2026 s.2(4)(b), s.3(4)(b) Table Sl. 10 (v) (pp.7, 14); First Schedule Part I-A, I-B Para F Table 1 Sl. 1 (vi) (pp.83, 87) |
| Ladders, thresholds | existing `RATE-SUR-*` rows (unchanged) |

#### Cases (worked by hand; each in both years unless marked)

- **S1 — T2-6** (old regime): TI 2,50,00,000 = slab 2,00,00,000 + s.198 / s.112A 50,00,000.
  - Tax at slab: 12,500 + 1,00,000 + 30% × 1,90,00,000 = 58,12,500. Special: (50,00,000 − 1,25,000) × 12.5% =
    6,09,375.
  - Excluding = 2,00,00,000, which does not exceed 2Cr, so nil. Total-income ladder at 2.5Cr: 15%.
  - Surcharge: 15% × 58,12,500 + 15% × 6,09,375 = 8,71,875 + 91,406.25 = **9,63,281.25**, citing
    `RATE-SUR-FIRST-SCHEDULE`.
  - Relief: U at 2Cr = (43,12,500 + 6,09,375) × 1.15 = 56,60,156.25; W = 1,06,60,156.25 ≥ 73,85,156.25, so nil.
- **S2** (old regime): TI 3,00,00,000 = slab 2,50,00,000 + s.198 50,00,000.
  - Slab tax: 1,12,500 + 30% × 2,40,00,000 = 73,12,500. Excluding 2,50,00,000, so 25%.
  - Surcharge: 25% × 73,12,500 + 15% × 6,09,375 = 18,28,125 + 91,406.25 = **19,19,531.25**. Applying 25% to the
    whole would give 19,80,468.75.
  - Relief: W = 56,60,156.25 + 1,00,00,000 ≥ 98,41,406.25, so nil.
- **S3** (new regime, TY 2026-27): TI 3,00,00,000 including 30,00,000 of dividends, no special gains.
  - Slab tax: 3,00,000 + 30% × 2,76,00,000 = 85,80,000. Excluding 2,70,00,000, so 25%.
  - Slice: 85,80,000 × 30,00,000 ÷ 3,00,00,000 = 8,58,000.
  - Surcharge: 25% × 77,22,000 + 15% × 8,58,000 = 19,30,500 + 1,28,700 = **20,59,200**.
  - Relief at 2Cr: (3,00,000 + 30% × 1,76,00,000) × 1.15 = 64,17,000; W = 1,64,17,000 ≥ 1,06,39,200, so nil.
- **The five former refusals:** each gets a figure worked the same way in its test comment. Their facts stay as
  they are.

**Bite probes** (each predicted to fail, then reverted):
1. Apply the chosen rate to the whole tax (no split): S2 and S3 fail; S1 does not (rates equal).
2. Leave the special parts in the excluding income: S1 fails (2.5Cr → 25%).
3. Use total income, not aggregate income, as the slice's denominator: a case with both dividends and special gains
   fails. I add one: **S4** (old regime, TI 3,00,00,000 = slab 2,50,00,000 incl. dividends 25,00,000 + s.198
   50,00,000; slice = 73,12,500 × 25,00,000 ÷ 2,50,00,000 = 7,31,250; surcharge = 25% × (73,12,500 − 7,31,250) +
   15% × (7,31,250 + 6,09,375) = 16,45,312.50 + 2,01,093.75 = **18,46,406.25**).

### W-088f2a — D-241's STT mapping as rule rows

Cited by `CapitalGainsNodeTest`.

#### Numbers I will use

None. The rows carry conditions and a part — no amount, rate or date.

#### Cases (by hand from sources rows 1–4; each equals `CapitalGainsAdapter:79–98` today)

| # | Term | Asset | STT buy | STT sale | Part | Today |
|---|---|---|---|---|---|---|
| 1 | short | EQUITY | NOT_PAID | PAID | SHORT_TERM_LISTED_EQUITY | `SPECIAL_RATE_LISTED_EQUITY` |
| 2 | short | EQUITY | PAID | NOT_PAID | SHORT_TERM_SLAB | `SLAB` |
| 3 | long | EQUITY | PAID | PAID | LONG_TERM_LISTED_EQUITY | same |
| 4 | long | EQUITY | PAID | NOT_PAID | LONG_TERM_OTHER | `SPECIAL_RATE_OTHER` |
| 5 | long | EQUITY | NOT_PAID | NOT_PAID | LONG_TERM_OTHER | `SPECIAL_RATE_OTHER` |
| 6 | long | EQUITY | NOT_PAID | PAID | refused, naming the facts | refused (W-088f2) |
| 7 | short | MUTUAL_FUND | any | PAID | refused | refused (`requireMapped`) |

Both tax years for cases 1–4 and 6 (the rows' two versions).

### W-088i — the house-reinvestment exemption (S2 s.86 / 1961 s.54F)

Cited by `ReinvestmentExemptionTest`, `ReinvestmentExemptionTest`.

**Numbers I will use, and where each comes from:**

The legal figures first; the case's own inputs are then derived by hand from them, never from a run.

| Value | Source (file · section) |
|---|---|
| bought within **one year before or two years after** the transfer, or built within **three years after** | 2025 s.86(1)(b) (Gazette 265620 p.130); 1961 s.54F(1) (thc p.292) |
| exempt **in the proportion the cost of the new asset bears to the net consideration**, where the net consideration is more | 2025 s.86(1)(i); 1961 s.54F(1)(b) |
| the **whole** gain exempt where the cost of the new asset is equal to or more than the net consideration | 2025 s.86(1)(ii); 1961 s.54F(1)(a) |
| **₹10,00,00,000** cap on the cost of the new asset | 2025 s.86(8); 1961 s.54F(1) second proviso — Finance Act, 2023 s.30, p.28, w.e.f. 1-4-2024 |
| the bar: more than one other residential house on the transfer date, or another bought within a year or built within three, if its income is chargeable under house property | 2025 s.86(5); 1961 s.54F(1) proviso — Act 10 of 2000, s.28, w.e.f. 1-4-2001 |
| **net consideration** = full value less expenditure incurred wholly and exclusively in connection with the transfer | 2025 s.86(10) (p.131); 1961 s.54F Explanation (p.293) |
| individual or Hindu undivided family | 2025 s.86(1); 1961 s.54F(1) |
| 12½ % above ₹1,25,000 on long-term listed equity | `RATE-CG-LONG-LISTED-EQUITY`, both versions (`SlabLadder`, `SPECIAL_RATE_GAINS`, 0 → 1,25,000, 12.5 above); 2025 s.198(2)(a) |
| 12 months, the holding period for a listed security | `CG-TERM-LISTED-SECURITY`, both versions (`ShortTermHoldingPeriod`, 12) |
| rebate nil where total income exceeds ₹12,00,000 | `REBATE-NEW-TAX-REGIME`, both versions (`FlatWithCeiling`, 60,000, ceiling 12,00,000) |
| surcharge 0 up to ₹50,00,000 | `RATE-SUR-NEW-TAX-REGIME`, both versions (0 → 50,00,000) |
| cess 4 % of tax and surcharge | `RATE-HEALTH-EDUCATION-CESS`, `PercentOfBase`, 4 |

**The golden case, worked by hand** — a resident individual, TY 2026-27, the default regime, no income but a sale:
one lot of listed equity bought 2024-05-01 for ₹40,00,000 and sold on NSE 2026-09-01 for ₹1,20,00,000, STT paid both
sides, no contract-note charges recorded; a house bought 2027-01-15 for ₹60,00,000.

| Step | Figure | Working |
|---|---|---|
| gain | ₹80,00,000 | 1,20,00,000 − 40,00,000 |
| term | LONG_TERM | 2024-05-01 + 12 months = 2025-05-01, before 2026-09-01 |
| net consideration | ₹1,20,00,000 | the full value; no charge recorded, so nothing is deducted from it |
| the new house is in the window | yes | bought 2027-01-15, after 2026-09-01 and within two years of it |
| exemption | **₹40,00,000** | 80,00,000 × 60,00,000 / 1,20,00,000, the cost being less than the net consideration |
| chargeable gain | ₹40,00,000 | 80,00,000 − 40,00,000 |
| tax at the special rate | ₹4,84,375 | 12½ % × (40,00,000 − 1,25,000) = 12½ % × 38,75,000 |
| rebate | ₹0 | total income 40,00,000 is above the row's ₹12,00,000 ceiling |
| surcharge | ₹0 | 40,00,000 is below ₹50,00,000 |
| cess | ₹19,375 | 4 % × 4,84,375 |
| **net payable** | **₹5,03,750** | 4,84,375 + 19,375 |

Every step is exact at paise: 12½ % of 38,75,000 is 4,84,375.00 and 4 % of that is 19,375.00, so no rounding mode is
in play. The other cases assert at the exemption and the head, not at net payable:

| Case | Figures | Expected |
|---|---|---|
| the whole gain, limb (ii) | consideration ₹40,00,000, cost ₹10,00,000, new house ₹45,00,000 | gain ₹30,00,000, exempt ₹30,00,000, chargeable nil |
| the cap | consideration ₹20,00,00,000, cost ₹5,00,00,000, new house ₹12,00,00,000 | gain ₹15,00,00,000, cost capped to ₹10,00,00,000, exempt ₹7,50,00,000, chargeable ₹7,50,00,000 |
| the bar | the golden's figures with two other houses owned on 2026-09-01 | no exemption, ₹80,00,000 charged |
| a window missed | the golden's figures with the house bought 2028-12-01 | no exemption, ₹80,00,000 charged |
| bought before the sale | the golden's figures with the house bought 2025-12-01 | exempt ₹40,00,000 — the one-year-before limb |

### W-089 — T13-1: the rebate with special-rate income

Cited by `T13RebateAndSpecialRateTest`, `DefermentReliefTest`, `RefundInterestTest`.

**Numbers I will use, and where each comes from:**

The design states the case's inputs and its outcome, not the rupee figure — so the figure below is worked from the
pack rows the case's own terms name, and every input is the design's.

| Value | Source (file · section) |
|---|---|
| salary ₹1,00,000 after standard deduction | `DR 13` T13-1 (`13-edge-cases.md:506`) |
| long-term capital gain ₹11,00,000 (s.198 — listed equity, STT paid) | the same line |
| total income ₹12,00,000 | the same line; and 1,00,000 + 11,00,000 |
| standard deduction ₹75,000, so gross basic pay ₹1,75,000 | `SAL-STANDARD-DEDUCTION-NEW-TAX-REGIME`, both versions: `LeastOf(Fixed 75000, 100 % of SALARY)`; 2025 s.19(1) Table Sl. 2(a) |
| nil band ₹4,00,000 | `RATE-SLAB-NEW-TAX-REGIME`, both versions, first band 0–4,00,000 at 0; 2025 s.202(1) |
| shortfall ₹3,00,000 | 4,00,000 − 1,00,000, the reduction of the gain by the unused basic-exemption limit — `TaxByBasisNode:105` and `:189-200`; 2025 s.198(3) / 1961 s.112A(2) proviso, read in `sources/2026-10-18-capital-gains-basis.md` row 19 |
| chargeable gain ₹8,00,000 | 11,00,000 − 3,00,000 |
| exemption ₹1,25,000 and rate 12½ % | `RATE-CG-LONG-LISTED-EQUITY`, both versions: 0 up to 1,25,000, 12.5 above; 2025 s.198(2)(a); 1961 s.112A(2)(i)(b) |
| special-rate tax ₹84,375 | 12½ % × (8,00,000 − 1,25,000) = 12½ % × 6,75,000 |
| rebate ceiling ₹60,000 at total income ≤ ₹12,00,000 | `REBATE-NEW-TAX-REGIME`, both versions (`FlatWithCeiling`, `limitedToSlabRateTax: true`); 2025 s.156(2)(a), capped by s.156(3) |
| **rebate ₹0** | s.156(3): the rebate "shall not exceed income-tax payable as per the rates provided in section 202(1)" — and the slab-rate tax on ₹1,00,000 is nil. `RebateNode:99-102` |
| surcharge ₹0 | `RATE-SUR-NEW-TAX-REGIME` (and its `-CEILING`/`-EXCLUDING`/`-LIMIT` companions), both versions: 0 up to ₹50,00,000 |
| cess ₹3,375 | 4 % × 84,375 — `RATE-HEALTH-EDUCATION-CESS`; FA 2026 s.2(6) / s.3(15) |
| **net payable ₹87,750** | 84,375 + 3,375; the design's "naive answer of nil tax" is the failure this guards |

The arithmetic is exact at every step (no rounding is needed: 12½ % of 6,75,000 is 84,375.00, 4 % of that is
3,375.00), so the golden does not depend on a rounding mode.

### W-089b — `CreditsNode`: tax already paid, credited

Cited by `CreditsAndBalanceTest`.

#### Tests, by hand (T13's bill, 87,750)

| Credits | Balance before rounding | Balance |
|---|---|---|
| none | 87,750 | 87,750 (equals `round.netpayable`) |
| TDS 50,000 + advance 20,000 + self-assessment 5,000 = 75,000 | 12,750 | 12,750 |
| 74,997 | 12,753 | 12,750 |
| 74,994 | 12,756 | 12,760 |
| 1,00,004 | −12,254 | −12,250 (refund) |
| 1,00,010 | −12,260 | −12,260 |
| 1,00,005 | −12,255 | −12,260 |

Both years. An election's choice is unchanged by credits. **Bite checks:** a credit left out of the sum; rounding the raw negative;
the node reading the rounded bill. Targeted run, then each tier once. Existing goldens must not move.

### W-089c — family B, second part — W-089c (advance tax) and W-112 (interest and the late-return fee)

Cited by `AdvanceTaxAndInterestTest`.

#### Worked by hand — TY 2025-26 (1961 Act)

Bill ₹1,00,000; TDS ₹20,000; advance tax ₹30,000 on 15-9-2025 and ₹20,000 on 15-3-2026; due 31-7-2026; filed and paid
31-8-2026; total income above ₹5,00,000; under 60.
- **Liable:** 1,00,000 − 20,000 = 80,000 ≥ 10,000. Schedule: 12,000 / 36,000 / 60,000 / 80,000 cumulative.
- **234B:** assessed 80,000; paid 50,000 < 72,000 (90 %); 30,000 × 1 % × 5 months (April – August) = **1,500**.
- **234C:** June 12,000 short, below 12 % (9,600) → 12,000 × 3 % = 360; September paid 30,000 ≥ 36 % (28,800) → nil; December
  30,000 × 3 % = 900; March 30,000 × 1 % = 300 → **1,560**.
- **234A:** 1,00,000 − 50,000 − 20,000 = 30,000 × 1 % × 1 month (August) = **300**. **234F:** **5,000**.
- **Payable:** 30,000 + 3,360 + 5,000 = **38,360** (already a multiple of ₹10).
- **Not filed, as of 6-11-2026 (C2a):** 234A 1-8 to 6-11 = 4 months → 1,200; 234B April to 6-11 = 8 months → 2,400; 234C 1,560;
  fee 5,000; payable **40,160**.
- **Edges:** 60 during the year, no business → not liable, 234B and 234C nil, payable **35,300**; bill − TDS ₹9,999 → not
  liable, ₹10,000 → liable; base ₹30,050 → interest on 30,000; filed on the due date → no 234A, no fee; income ₹5,00,000 →
  fee 1,000 (TY 2026-27 fixed; TY 2025-26 "at most", shown as that maximum with a notice). **TY 2026-27:** the same facts a year on give the same figures; filed 5-9-2027 → s.423 2 months under C3a.

### W-112a — the s.425(4) / s.234C relief from deferment interest

Cited by `DefermentReliefTest`.

**Numbers I will use:**

| Value | Source |
|---|---|
| 15/6, 15/9, 15/12, 15/3; 15 / 45 / 75 / 100 %; 3 % / 1 % | existing `INT-DEFERMENT` data (D-446) |
| 31 March, the paid-by day | 2025 s.425(4)(b), Gazette 265620 p.448; 1961 s.234C first proviso, thc p.697 |
| the four kinds; dividend except (e) | s.425(4)(a), (6) p.448; 1961 proviso (a) – (d), FA 2021 s.59 |

### W-112b — interest on a refund, both ways

Cited by `RefundInterestTest`.

**Rule data (facts, not rules):** a new rule `INT-REFUND` (set `RS-INT-REFUND`) in both versions, definition
`RefundInterest(perMonth 0.5 %, noInterestBelowSharePercent 10)`. The periods' anchors (the due date, furnishing,
payment, grant) are the section's structure, as `INT-LATE-RETURN`'s are. Before naming, I'll check the id grammar
(`rules/id` PATTERN).

**Numbers I will use:**

| Value | Source |
|---|---|
| 0.5 % per month or part | 2025 s.437(1), 265620 p.451; 1961 s.244A(1)(a), (aa), thc p.704 |
| 10 % of the tax determined | s.437(2); s.244A(1) proviso |
| ₹100 base, part month whole | r.269(b), (c), 271092 p.1769 (TY 2026-27); TY 2025-26 as `INT-CALCULATION` holds it (UNVERIFIED, r.119A) |

### W-139 — pack linter and cross-year review

Cited by `PackLintTest`.

#### What I read (session 48)

- **`DR 15` C4** (`taxplanning-design/15-review-findings.md:405`): `PackDiff` plus lint findings; eight lints; "run the
  diff as a build step that writes a human-readable review file … require a reviewer to sign that file off".
  **`DR 18` V7** (`18-temporal-versioning.md:356`) adds seven shape lints.
- **W-045h erratum** (`03-WORK-ITEMS.md:514`): `PackDiff` (`taxengine/rules/timeline/PackDiff.java`) has `RuleAdded`,
  `RuleRemoved`, `VersionAdded`, `VersionRemoved`, `ValueChanged`, reading only compiled `law/rates.json`; W-139 adds
  the lint findings, the magnitude, and **regimes** (not diffed today). `PackDiff` has no caller in `main`, only
  `PackDiffTest`.
- **Already enforced, so not rebuilt** (`validate/PackValidator.java`, build-time per memory): reachability (D-252,
  :103) = C4's I3 lint; I6 (:207); I7 (:258); I13 gap/overlap (:324) = V7's last two; I14 mid-year declared (:380) =
  V7's `UNDETERMINED` lint. The slab contiguity lint (H-5) is a `SlabLadder` construction rule, not a pack check.
- **The design moved under C4.** C4 assumed a year is `cp -r` of the last; packs are now compiled from **one timeline**
  of versioned rules (`rulepacks/timeline/law/rules/*.json`, 243 files; `shipped-years.json`: 2025-26, 2026-27). So the
  H-2 copy lint becomes: a version that repeats its predecessor's `introducedBy` and `provenance.sourceUrl`.
- **Survey of the shipped timeline today** (a script over the JSON, run once, not committed):
  - copied versions: **3** — `STT-PURCHASE-DELIVERY`, `STT-SALE-DELIVERY`, `STT-SALE-NONDELIVERY` (2025-04-01 →
    2026-04-01, same definition, same instrument);
  - rules valid in 2025-26 and absent in 2026-27: **7**; 5 say `provision2025.presence = ABSENT_IN_THIS_ACT`; **2 do
    not**: `PERQ-CLUB-BUSINESS`, `PERQ-CLUB-UNIFORM` (their 2026-27 successor is `PERQ-CLUB-BUSINESS-UNIFORM`);
  - verification older than its instrument (`accessedOn` < `introducedBy.notifiedOn`, level not `UNVERIFIED`): **0**;
  - definition type or list length changed between consecutive versions: **0**.
- **No law is involved:** no figure, rate or date is read or changed.

#### Tests, by hand
- `TenfoldChange`: 250000 → 2500000 flagged; 250000 → 300000 not; 0.04 → 0.4 flagged; 0 → 5 not; "NEW" → "OLD" not.
- `UnexplainedDrop` on the shipped pair → exactly `PERQ-CLUB-BUSINESS`, `PERQ-CLUB-UNIFORM`; the 5 `ABSENT_IN_THIS_ACT`
  rows not listed.
- `CopiedVersion` on the shipped timeline → exactly the 3 STT rows.
- `ShapeChanged`: a copy with one band added to a 2026 slab version → one finding; the shipped timeline → none.
- Regimes: a copy with `NEW_TAX_REGIME`'s first band rate edited → one `ValueChanged` naming the regime.
- Guard: a copy with `accessedOn` set to the day before `notifiedOn` → refused; shipped packs → pass.
- **Probes:** drop the ≤ 1/10 side → the 0.04 case fails; ignore `ABSENT_IN_THIS_ACT` → 7 drops, test fails; edit one
  shipped rule's value without regenerating → `PackReviewTest` fails.

### W-139a — succession detail in the pack review

Cited by `PackReviewTest`.

#### What I read (session 48)

- **The row** (`03-WORK-ITEMS.md`, W-139a) and **D-452**: 220 of the review's 235 change lines read "version 2025-04-01
  removed; version 2026-04-01 added". `PackLint` already pairs each new version with its predecessor
  (`lintVersions` → `lintSuccession`); `PackDiff.compare` already walks two nodes leaf by leaf.
- **Survey of the 220 successions at 2026-04-01** (a script over `rulepacks/timeline/law/rules/*.json`, run once,
  not committed), changed leaves by top-level field:

| Field | Changes | Characters |
|---|---|---|
| `introducedBy` | 1072 | 133,016 |
| `provenance` | 594 | 176,991 |
| `validity` | 440 | 11,880 |
| `cap`, `definition`, `conditions`, `provision2025`, `regimes`, `provision1961` | 70 | ≈ 2,700 |

  **31** successions change a law field; **189** change only `validity`, `introducedBy` and `provenance`
  (re-sourced: the same law read from the 2025 Act).
- **No law is involved:** no figure or rate is read or changed; this is the review's presentation.

#### Tests, by hand
- Shipped pair: 31 rules under "Law changed", 189 ids under "Re-sourced only" (the survey's counts).
- `PERQ-LOAN-PETTY`: its line holds `cap.amount 20000 → 200000` (W-139's tenfold finding, same leaf) and its 2026
  `introducedBy.instrumentCitation`, copied from the rule file by hand at build time.
- A re-sourced rule picked from the survey's 189 appears there and nowhere under "Law changed".
- `PackReviewTest` fails until the file is regenerated (the guard W-139 built).
- **Probe:** count `introducedBy` as law → the 189 / 31 split test fails.

### W-146 — the synthetic pack test

Cited by `SyntheticPackTest`.

#### Tests, by hand
- Every expected figure from step 2. **Probes:** change `SYN_FLAT`'s rate to 9 % in the resource → its case fails;
  make the perturbation reach a rate field → the I27 test fails; both reverted.

#### Revision 1 — the build after D-464 – D-466 (session 50; for approval)

W-243 (lone-row regime test) and W-244 (condition sets in the timeline) are built; W-245 is decided (a): the pack
carries a row for every set it needs; E8 has no lock-in. Three things the resumed build must settle:

**R1 — how the ~122 other rows are carried.** (a) **Committed test resources under `rulepacks-synthetic/`, written
once by `tools/w146_synthetic_pack.py`: the TY 2026-27 rows, components and disallowance sets copied with their regime
references renamed** (`NEW_TAX_REGIME` → `SYN_DEFAULT` and `SYN_FLAT`; `FIRST_SCHEDULE_RATES` → `SYN_CLASSIC`), each
file's `_note` saying it is a copy; the synthetic year's own rows (rate tables, standard deduction, eligibility) written
fresh; locks committed; loaded through the production boot path `ShippedRulePacks.compile` with lock verification —
so the pack is a year of data authored as a real one would be. (b) Generated at test time into a temporary directory —
always in step with the shipped law, but loaded around the classpath and lock check, which is half of "loads".

**R2 — `SYN_FLAT`'s eligibility.** *"No business income"* (D-466's erratum) cannot fail when offers are made: the
condition context carries no heads of income (`pipeline/NodeContext.java:335` – `345`), so E8's "2 offers for one who
fails" cannot be shown. (a) **`SYN_FLAT` open to residents only (`IsResident`)** — a resident is offered three
regimes, a non-resident two; a second E8 erratum. (b) An age condition — equally decidable, less natural for a regime.

**R3 — "diffs each against `SYN_DEFAULT`".** `compareRegimes` returns each regime's bill (`api/RegimeComparison.java`),
not a per-rule diff; drivers by rule id are PRD F3's, not in the engine's API. (a) **Assert the ranking and each
bill's difference from `SYN_DEFAULT`'s**, and say so in the report. (b) Treat the missing per-rule diff as a finding
under C2.

**Also, forced by W-243:** the standard-deduction set takes exactly one row per regime, so `SYN_FLAT` gets its own
row of nil (`SYN-STANDARD-DEDUCTION-FLAT`, `Fixed 0`); the 1 July row stays `ExceptIn(SYN_FLAT)` and must be absent
from `SYN_FLAT`'s trace — E8's `ExceptIn` assertion.

**Hand figures (TY 2099-00, salary ₹20,00,000, resident, computed 2099-04-01, filing due 2100-07-31):**

| Regime | Working | Bill |
|---|---|---|
| `SYN_DEFAULT` | standard deduction 50,000 × 91/365 + 80,000 × 274/365 = 12,465.75 + 60,054.79 = 72,520.55; TI 19,27,479.45 → 19,27,480; tax 10 % × 14,27,480 = 1,42,748; rebate nil (TI above 12,00,000 and the tax below the 7,27,480 excess, copied `REBATE-NEW-TAX-REGIME-EXCESS`); cess 4 % 5,709.92 | 1,48,457.92 → **1,48,460** |
| `SYN_CLASSIC` | TI 19,27,480 (the same deduction); 20 % × 16,27,480 = 3,25,496; rebate nil (above 5,00,000); cess 13,019.84 | 3,38,515.84 → **3,38,520** |
| `SYN_FLAT` | TI 20,00,000 (deduction nil); 8 % = 1,60,000; rebate nil; cess 6,400 | **1,66,400** |

Ranked `SYN_DEFAULT` < `SYN_FLAT` < `SYN_CLASSIC`; differences from `SYN_DEFAULT`: +17,940 and +1,90,060. TY 2100-01:
one version of the deduction (80,000), TI 19,20,000; `SYN_DEFAULT` 1,42,000 × 1.04 = **1,47,680**. TY 2101-02:
`SYN_FLAT` not in the pack; a resident is offered two regimes and the comparison prices two. A non-resident in TY
2099-00 is offered two (`SYN_FLAT` barred, naming its set). The "bill" here is `ComputedTax.bill` rounded as the copied
`RATE-ROUND-PAYABLE` rounds the net payable (to ₹10) — the build record confirms which field carries the rounding
before any test asserts it.

**Build, revised:** step 1 as R1a; step 2 these figures in the build record before any run; step 3 `SyntheticPackTest`
(load and validate the three years; offers 3 / 2; three bills; ranking and differences; the `ExceptIn` row in two traces
and absent from the third; the two deduction segments in TY 2099-00 and one in TY 2100-01; TY 2101-02 without
`SYN_FLAT`); step 4 `ContentPerturbationTest` (I27) unchanged; step 5 rows: E8 erratum (R2), I27, T23-1, PRD P3
marked, W-146a / W-146b already placed. The probes stand (rate 8 → 9 % fails `SYN_FLAT`'s case; perturbing a rate
field fails I27).

#### Build record — Revision 1 (session 50)

**Pack:** `backend/src/test/resources/rulepacks-synthetic/` (331 files, three locks), written by
`tools/w146_synthetic_pack.py`: three regimes, their rate tables, the 1 July standard deduction and `SYN_FLAT`'s nil
row, `ELIG-RESIDENT`; every other rule, the components, rule sets and disallowance sets copied from TY 2026-27 with
regimes renamed, each `_note` saying so. Locks written by the spike (`tools/zz_synthetic_spike_test.java.txt`), which
was then deleted. All three years load through `ShippedRulePacks.compile` (lock verified) and pass `PackValidator`.

**Which field rounds (confirmed before any test):** `ComputedTax.bill` and the comparison's prices are `tax.netpayable`,
before rounding (`election/ElectionResolver.java:101`, `PricedAlternative`); `finalAmount` is `round.netpayable`
(`api/TaxCalculator.java:64`). So the bills are 1,48,457.92, 3,38,515.84 and 1,66,400.00, the final amounts 1,48,460,
3,38,520 and 1,66,400, and the differences from `SYN_DEFAULT` **17,942.08** and **1,90,057.92** (the revision's 17,940 and
1,90,060 were on rounded figures).

**`SyntheticPackTest` (11 cases), all agreeing with the hand figures on the first run:** three years validate; a
resident is offered three regimes, a non-resident two (`SYN_FLAT` barred naming *"a resident"*); `SYN_DEFAULT` TI
19,27,480, bill 1,48,457.92, final 1,48,460, its deduction line naming both segments; `SYN_CLASSIC` 3,38,515.84 /
3,38,520; `SYN_FLAT` TI 20,00,000, 1,66,400, `SYN-STANDARD-DEDUCTION` absent from its trace and its nil row present; the
comparison chooses `SYN_DEFAULT` with the two differences; TY 2100-01 TI 19,20,000, 1,47,680; TY 2101-02's pack holds no
`SYN_FLAT` and the comparison prices two.

**`ContentPerturbationTest` (I27, T23-1; 4 cases):** every text in `content/` rewritten (65 section files, two Acts,
the register), or `content/` removed — each shipped year compiles to the same files and rule checksums.

**Probes, each reverted:** `SYN_FLAT` at 9 %, pack relocked → exactly its case and the comparison fail; restored
(`diff -r` identical). One law text (`NEW_TAX_REGIME`'s `displayName`) rewritten alongside content → both I27 rewrite
cases fail on the compiled files; restored (`cmp`). (A first version of that probe rewrote whole regime files and
errored on parsing rather than failing — narrowed to one text field.)

**No production source changed** for any of it: `git diff --stat -- backend/src/main` is empty (C4a).

**Not asserted, by R3a:** a per-rule diff between regimes — `compareRegimes` gives bills only; drivers by rule are PRD F3's.

**Rows:** E8 erratum (residents only, R2a); I27 ✅ (`02-TECH-SPEC.md`, Changes logged); PRD P3 ✅; T23-1 is this test.

### W-153b — the deductions' reach within the head by rule data

Cited by `OtherSourcesNodeTest`.

#### Numbers I will use, and where each comes from

None new; no cap changes. The kind lists are copied from today's code (`OtherSourcesNode:148–159`, `:169`, `:192`,
`:210`, `DividendInterestAllowance:74`) and match the `_note`s quoted above.

#### Cases (by hand)

| # | Input | Expected |
|---|---|---|
| 1 | the 125 existing `OtherSourcesNodeTest`, `DepositInterestDeductionTest`, `SurchargeNodeTest` and golden tests | unchanged |
| 2 | partition, both years: for each of the 15 built kinds, one item received 10,000, commission 100, interest 200, other 400 | table below |
| 3 | `INTEREST_ON_SECURITIES` received 10,000, commission 100, interest 200, other 400, payable outside India untaxed | as today: both expense lines nil, "payable outside India untaxed" (unchanged path through `lines.deduction`) |

Case 2. "Commission" and "Expenditure" are the out on the `OS-BANKER-COMMISSION` and `OS-OTHER-EXPENDITURE` lines;
"—" means no line on that row. These figures come from the law in the rows' `_note`s, not from a run. Each kind's
amounts are distinct (100, 200 and 400 in sums), so a wrong reach changes a figure.

| Kind | Commission | Expenditure | `OS-DIVIDEND-INTEREST` (2025 / 2026) |
|---|---|---|---|
| `INTEREST_ON_SECURITIES` | 100 | 600 | — |
| `SAVINGS_DEPOSIT_INTEREST`, `DEPOSIT_INTEREST`, `OTHER_INTEREST` | 0 (nil) | 600 | — |
| `FAMILY_PENSION`, `KEYMAN_POLICY`, `FORFEITED_ADVANCE`, `TERMINATION_COMPENSATION`, `OTHER` | 0 (nil) | 600 | — |
| `DIVIDEND`, `UNIT_INCOME` | — | — | 200 → 200 / 200 → 0 (20% of 10,000 = 2,000 ≥ 200; 0%) |
| `DEEMED_DIVIDEND` | — | — | — (its 700 a nil line on `OS-DEEMED-DIVIDEND`) |
| `INTEREST_ON_COMPENSATION`, `COMMUTED_FUND_PENSION`, `DEATH_GRATUITY` | — | — | — (their 700 a nil line on their own row) |

For dividend and unit income, commission and other expenditure (500) are a nil line on `OS-DIVIDEND-INTEREST`, as
today.

### W-153c1 — income rows by kind (s.92)

Cited by `OtherSourcesNodeTest`.

#### Numbers I will use, and where each comes from

None new. The kind → row assignments come from the statute (`sources/2026-10-22-unit-income.md` U1 – U11, and the
rows' existing provisions). The new row's 100% and provenance are copied from `OS-COMPENSATION-INTEREST`'s
`introducedBy` and its s.92(2)(i) / s.56(2)(viii) citations.

#### Cases (by hand)

| # | Input | Expected |
|---|---|---|
| 1 | the existing `OtherSourcesNodeTest`, `DepositInterestDeductionTest`, `SurchargeNodeTest` and golden tests | values unchanged; trace changes only those listed in "Trace changes" |
| 2 | partition, both years, every kind: the `RS-OS-INCOME` row its facts select | interest kinds → `OS-INTEREST`; dividend, deemed → `OS-DIVIDEND`; Keyman, advance, termination → their rows; other, unit, family pension, commuted, gratuity → `OS-RESIDUAL`; interest on compensation → `OS-COMPENSATION-INTEREST-INCOME`; winnings, employee contributions, both lettings → none |
| 3 | a `WINNINGS` item (and each business-head kind), both years | refused: "no row of RS-OS-INCOME charges WINNINGS in TY 2025-26" (`:351–355`'s "D-202" assertion becomes the kind and the set) |
| 4 | `UNIT_INCOME` 1,00,000 with interest 10,000, TY 2025-26 | income line on `OS-RESIDUAL` 1,00,000; value 90,000 (20% of 1,00,000 = 20,000 ≥ 10,000), as today |

**Trace changes (D-321's exception, proven by the dump):**
- unit income's income line moves `OS-DIVIDEND` → `OS-RESIDUAL`;
- the four in-full kinds' income words become "item n: <KIND>" (from "Keyman policy sums" and the like);
- the refusal message.

Nothing else moves.

### W-153c2 — share deductions as reach rows (s.93)

Cited by `OtherSourcesNodeTest`.

#### Numbers I will use, and where each comes from

None new. The kinds each share row reaches come from today's arms (`:150–157`) and the rows' `_note`s:
- s.93(1)(f) interest on compensation;
- (g) commuted fund pension;
- (h) death gratuity;
- (d) family pension.

#### Cases (by hand)

| # | Input | Expected |
|---|---|---|
| 1 | every existing test | values unchanged; the trace assertions at `:179`, `:220`, `:237`, `:472`, `:494`, `:527`, `:540`, `:553` rewritten to the new lines (D-321 exception), each value kept |
| 2 | W-153b's partition (10,000; commission 100, interest 200, other 400), both years, updated | securities: commission 100 → 100, expenditure 600 → 600. Other interest, family pension, in-full: commission 100 → 0, expenditure 600 → 600. Dividend, unit: commission 100 → 0, expenditure 400 → 0, dividend interest 200 → 200 / 200 → 0 (TY 2025-26 / 2026-27). Deemed, interest on compensation, commuted, gratuity: commission 100 → 0, expenditure 600 → 0 |
| 3 | share partition, both years, each kind | interest on compensation → `OS-COMPENSATION-INTEREST` 10,000 → 5,000 (50%); commuted → `OS-COMMUTED-PENSION` 10,000 → 10,000; gratuity → `OS-DEATH-GRATUITY` 10,000 → 10,000; family pension, old regime → `OS-FAMILY-PENSION`, new regime → `-NEW-TAX-REGIME`, 10,000 → 3,333.33 (one-third, under both limits, ₹15,000 / ₹25,000 per the rows); every other kind → none |
| 4 | interest on compensation 2,00,000, commission 1,000, both years | income line 2,00,000 on `OS-COMPENSATION-INTEREST-INCOME`; share 1,00,000 on `OS-COMPENSATION-INTEREST`; commission nil on `OS-BANKER-COMMISSION`; value 1,00,000 |

Case 3's one-third: `FamilyPensionShare.deduction` is `min(pension.apportioned(1, 3), limit)`, divided once and
rounded half-up to paise (`rules/money/Money.java:85–90`). So 10,000 ÷ 3 = 3,333.33, which the existing
`…FamilyPensionsThirdIsNotWhole_dividesOnceToPaise` (`OtherSourcesNodeTest:508–514`, 10,000 − 3,333.33 = 6,666.67)
already pins.

### W-153d — dividend income for the surcharge by rule data

Cited by `SurchargeNodeTest`.

#### Numbers I will use, and where each comes from

None new. The kinds come from S2 s.2(40) / 1961 s.2(22) (U3, U5) and D-245. No figure changes.

#### Cases (by hand)

| # | Input | Expected |
|---|---|---|
| 1 | every existing `SurchargeNodeTest` case, including `:411` (units stay in the excluding income), `:423` (deemed taken out) and `:462–500` (D-234) | unchanged |
| 2 | partition, both years × both regimes: for each `OtherSourceKind`, whether the regime's limit row reaches it | `DIVIDEND`, `DEEMED_DIVIDEND`: yes; every other kind, unit income included: no |

### W-162a — the engine nets the gain

Cited by `CapitalGainsChargesTest`.

#### Numbers I will use, and where each comes from

None from the law. The test amounts are my own and are derived by hand above. The 263.00 and 390.00 are W-086's
fixtures (`CapitalGainsSummaryServiceTest:106–109`).

#### How I will verify

```
./mvnw clean test -pl backend -Dtest='CapitalGainsChargesTest,CapitalGainsNettingTest,ChargeCatalogueCoverageTest,CapitalGainsNodeTest,CapitalGainsSummaryServiceTest,CapitalGainsSummaryTest,CapitalGainsAdapterTest,InputsFingerprintTest,RegimeElectionSnapshotTest,EngineInputsTest,GeneratedConstantsTest,PackJsonTest,RulePackLoadTest,TaxYear2025RulePackTest,TimelineReaderTest,ArchitectureTest,WealthLensModulithTest'
./mvnw clean test -pl backend -Punit
DOCKER_HOST=unix://$HOME/.orbstack/run/docker.sock TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock ./mvnw test -pl backend -Pintegration
grep -rn "getDeductibleBuyCharges\|getDeductibleSellCharges" backend/src/main --include=*.java   # expect only ChargeViewAssembler (W-162b's)
```

Probes, each restored and checked byte for byte:

1. **Rule data:** `CG-CHARGE-STT`'s limb changed to `COST_OF_ACQUISITION` (`bite_probe_rule_data.py`). Prediction:
   `CapitalGainsChargesTest`'s gain falls by 180.00 to 19,764.40, and `CapitalGainsNettingTest`'s A + B bucket falls
   by 1.80 to 261.20, and its C bucket by 1.00 (C's STT) to 389.00. `ChargeCatalogueCoverageTest` still passes.
2. **Rule data:** `BROKERAGE` removed from `CG-CHARGE-TRANSFER`. Prediction: `ChargeCatalogueCoverageTest` fails for
   `BROKERAGE` on `TRANSFER`, in both years; `CapitalGainsChargesTest` and `CapitalGainsNettingTest` refuse.
3. **Code:** `CapitalGainsCharges` deducts regardless of `deducts()`. Prediction: probe 1's same two failures, plus
   misc — 19,764.40 − 10 = 19,754.40, and 261.20 − 10 = 251.20 (C, with no misc, stays at 389.00).

- **`CapitalGainsNettingTest`** (`taxplanning/portfolio`, the proof that figures are unchanged):
  - It runs `rowsAtoC` through the real summary service (repository mocked), the adapter and `CapitalGainsNode`.
  - The bucket gains are W-086's hand-derived **263.00** and **390.00**:
    - A + B: 1,800 − 1,520 − (5 + 2) − (7 + 3) = 263. STT 1.80 and misc 10 are not deducted.
    - C: 1,000 − 600 − 4 − 6 = 390.
- **`ChargeCatalogueCoverageTest`** (`taxplanning/portfolio`):
  - It reads `data/charges/charge-catalogue.json` as JSON and, for every shipped year, every code not in category
    `SUBSCRIPTION` (account-level), and both sides, runs the node through `NodeHarness` without a refusal.
  - It also asserts the catalogue holds no code `MISC`, so the name the adapter uses cannot collide.
- **Existing tests:**
  - `CapitalGainsSummaryServiceTest` keeps every case. Its gain assertions become assertions on the amounts, with
    the same hand arithmetic in each `// Then` (for example A + B: consideration 1,800.00, cost 1,520.00, acquisition
    `BROKERAGE` 7.00, transfer `BROKERAGE` 10.00 and `STT` 1.80, misc 10.00). The netting itself is proven in
    `CapitalGainsNettingTest`.

### W-258 — net agricultural income in the rates (partial integration)

Cited by `AgriculturalIntegrationTest`.

#### Numbers I will use, and where each comes from

| Value | Source |
|---|---|
| ₹5,000 (exceeding) | FA 2026 s.2(2)(a) p.2 "five thousand rupees"; s.3(2)(a) p.9 "₹ 5000" |
| ₹2,50,000 / ₹3,00,000 / ₹5,00,000 | FA 2026 s.2(2)(a) Table Sl. 1 – 3 p.2; s.3(2)(a) Table p.10 |
| ₹4,00,000 | FA 2026 s.2(2)(a) Table Sl. 4 (s.115BAC(1A)) p.2; s.3(2)(a) Table Sl. 4 (s.202) p.10 |
| Z = X − Y; AI = income + net agricultural income | FA 2026 s.2(2)(b) pp.2 – 3; s.3(2)(b) p.10 |
| The ladders' bands | existing rows `RATE-SLAB-NEW-TAX-REGIME`, `RATE-SLAB-FIRST-SCHEDULE` (unchanged) |

**Hand-derived cases** (TY 2026-27, resident under sixty, slab income as given, no special-rate income):

| Case | Regime | Slab income | Net agri | Xn | Yn | Zn | Without W-258 |
|---|---|---|---|---|---|---|---|
| A | new | 12,00,000 | 10,00,000 | tax(22L) = 20,000 + 40,000 + 60,000 + 80,000 + 50,000 = 2,50,000 | tax(14L) = 20,000 + 40,000 + 30,000 = 90,000 | 1,60,000; 87A ₹60,000 → 1,00,000; cess 4% → **1,04,000** | 0 |
| B | new | 15,00,000 | 3,00,000 | tax(18L) = 1,60,000 | tax(7L) = 15,000 | 1,45,000; no 87A (over 12L, excess relief nil); cess → **1,50,800** | 1,09,200 |
| C | old | 8,00,000 | 2,00,000 | tax(10L) = 12,500 + 1,00,000 = 1,12,500 | tax(4.5L) = 10,000 | 1,02,500; cess → **1,06,600** | 75,400 |
| D | new | 4,00,000 | 5,00,000 | — slab income not above ₹4,00,000 | — | no integration, 0 | 0 |
| E | new | 15,00,000 | 5,000 | — not above ₹5,000 | — | 1,05,000 (plain) | same |
| F | new | 15,00,000 | 5,001 | tax(15,05,001) | tax(4,05,001) | the formula applies, by ₹1 over | — |

Case A's 87A: s.156 rebate of up to ₹60,000 where total income ≤ ₹12,00,000 (`REBATE-NEW-TAX-REGIME`,
FlatWithCeiling 60000 / 1200000). The bands come from the shipped rows.

**Hand-derived cases** (TY 2026-27, resident under sixty, slab income as given, no special-rate income):

| Case | Regime | Slab income | Net agri | Xn | Yn | Zn | Without W-258 |
|---|---|---|---|---|---|---|---|
| A | new | 12,00,000 | 10,00,000 | tax(22L) = 20,000 + 40,000 + 60,000 + 80,000 + 50,000 = 2,50,000 | tax(14L) = 20,000 + 40,000 + 30,000 = 90,000 | 1,60,000; 87A ₹60,000 → 1,00,000; cess 4% → **1,04,000** | 0 |
| B | new | 15,00,000 | 3,00,000 | tax(18L) = 1,60,000 | tax(7L) = 15,000 | 1,45,000; no 87A (over 12L, excess relief nil); cess → **1,50,800** | 1,09,200 |
| C | old | 8,00,000 | 2,00,000 | tax(10L) = 12,500 + 1,00,000 = 1,12,500 | tax(4.5L) = 10,000 | 1,02,500; cess → **1,06,600** | 75,400 |
| D | new | 4,00,000 | 5,00,000 | — slab income not above ₹4,00,000 | — | no integration, 0 | 0 |
| E | new | 15,00,000 | 5,000 | — not above ₹5,000 | — | 1,05,000 (plain) | same |
| F | new | 15,00,000 | 5,001 | tax(15,05,001) | tax(4,05,001) | the formula applies, by ₹1 over | — |

Case A's 87A: s.156 rebate of up to ₹60,000 where total income ≤ ₹12,00,000 (`REBATE-NEW-TAX-REGIME`,
FlatWithCeiling 60000 / 1200000). The bands come from the shipped rows.
