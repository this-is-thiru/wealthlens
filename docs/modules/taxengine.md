# Module `taxengine` — income tax as data, and the engine that computes it

> Written from the code at `review/taxplanning`; every claim cites the line it was read from, and a bare path is under
> `backend/src/main/java/com/thiru/wealthlens/taxengine/`. Template: `docs/taxplanning-build/plans/W-357b.md` C2a. The module is
> written in six parts (D-915): **boundary and run** (W-357b9a, D-916, here), the nodes (W-357b9b), the inputs (W-357b9c), the rules
> vocabulary (W-357b9d), rule packs and filing (W-357b9e), elections and the optimiser (W-357b9f). A section a later part fills says so.

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

## 6. Configuration

None of the engine's own. The packs it compiles are the classpath directory `rulepacks`, and the years are `shipped-years.json`'s
(`config/TaxEngineConfig.java:24`, `:26` – `:29`).

## 7. Tests (this part)

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

Counts are `@Test` / `@ParameterizedTest` annotations per file (grep), under `taxengine/api`, `pipeline`, `result` and `trace`.

## 8. Known gaps (boundary and run: W-389, D-916; the nodes: W-390, D-917)

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
7. **Twenty plans are cited by seventeen of this part's tests** for their hand-worked figures (`taxengine/node`, `taxengine/golden`;
   grep). Re-homing them is W-357b9b1 (D-917): read each plan's tables, move what a test relies on into this doc or the test, re-point
   the test, then delete the plan. Two are cited outside this part too: `plans/W-089.md` by three `taxplanning` tests, and
   `plans/W-112b.md` by two pack files and a synthetic pack.

## 9. Not covered here

- What `EngineInputs` holds: W-357b9c.
- How a rule, a cap or a condition is written: W-357b9d.
- Packs, the JSON form, locks, validation, the catalogue and the return forms: W-357b9e.
- The regime election, other elections and the optimiser's search: W-357b9f.
- What `taxplanning` does with an answer: `docs/modules/taxplanning.md` (W-357b10).
