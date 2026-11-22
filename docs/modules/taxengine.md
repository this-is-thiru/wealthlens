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

Counts are `@Test` / `@ParameterizedTest` annotations per file (grep), under `taxengine/api`, `pipeline`, `result` and `trace`.

## 8. Known gaps (this part: placed as W-389, D-916)

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

## 9. Not covered here

- What each node computes: W-357b9b.
- What `EngineInputs` holds: W-357b9c.
- How a rule, a cap or a condition is written: W-357b9d.
- Packs, the JSON form, locks, validation, the catalogue and the return forms: W-357b9e.
- The regime election, other elections and the optimiser's search: W-357b9f.
- What `taxplanning` does with an answer: `docs/modules/taxplanning.md` (W-357b10).
