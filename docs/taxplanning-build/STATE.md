# STATE — the only mutable file

**Read this first, write it last, every session.**

```
Last checkpoint           : W-046 — "taxplanning W-046: add TaxByBasisNode — tax at slab rates from the regime's own rate table, one traced line (D-071)"
Last updated              : 2026-09-25 (session 6 — Phase 4b complete; W-046 done; build GREEN, 1495 tests)
Current position          : W-046b — old-regime age ladders and ladder selection
Session count             : 6
```

**"Last checkpoint"** records the **item id and the commit subject** of the last accepted item — not
a hash. A commit cannot contain its own hash, so storing one here is self-referential and was found
to be unworkable in session 1.

Prompt **P1** compares this line against the most recent **item** commit:

```
git log --oneline --grep='^taxplanning W-' -1
```

- the subject **matches** → the tree is where the protocol left it. Proceed.
- the subject **differs** → an item was committed outside the protocol. Stop, report
  `git log --oneline -10`, and wait.

Commits that do **not** start `taxplanning W-` — documentation, tooling, unrelated repository work —
are ignored by the check. Only item commits move the checkpoint.

This survives amends, rebases and cherry-picks, which a hash does not.

---

## Current position

| | |
|---|---|
| **Next item** | `W-046b` — the old-regime age ladders, ladder selection and the `ConditionContext` (D-071); then `W-048`. The 2025-Act backlog gaps (STATE) await the owner. Startup wiring is UNOWNED (D-054). `W-045d2` any time |
| **Status** | `READY` — nothing in flight, nothing blocked. Two owner questions open (OQ-1, OQ-2 below; OQ-3 decided by D-063, OQ-4 by D-051); nothing depends on them |
| **Phase** | 5 — tax nodes: the walking skeleton (Phase 4b complete) |
| **Next milestone** | `W-053` — a correct slab-and-rebate calculator, no database |

---

## Status board

Legend: `·` not started · `E` explained, awaiting approval · `P` in progress ·
`R` awaiting review · `D` done · `B` blocked

```
Phase 0    W-001 D  W-002 D  W-003 D   ← COMPLETE
Phase 1    W-004 D  W-005 D  W-006 D  W-007 D  W-008 D  W-009 D  W-010 D  W-011 D  W-012 D   ← COMPLETE
Phase 2    W-013 D  W-014 —  W-015 D  W-016 —  W-017 D  W-018 D  W-019 D  W-020 —  W-021 D  W-021b D  W-021c D  W-022 —   ← COMPLETE
Phase 3    W-023 D  W-024 —  W-025 D  W-026 D  W-026b D  W-026c D  W-027 D  W-028 D  W-029 D  W-030 D  W-031 D  W-031b D  W-032 D  W-032b D  W-033 D  W-033b D  W-033c D  W-034 D  W-034b D   ← COMPLETE
Phase 4    W-035 D  W-036 D  W-037 D  W-037b D  W-037c D  W-038 D  W-039 D  W-040 D  W-041 —  W-042 D  W-043 D  W-044 D  W-045 —   ← COMPLETE (W-041, W-045 RETIRED)
Phase 4b   W-045a D  W-045b D  W-045b2 D  W-045b3 D  W-045c D  W-045c1 D  W-045d D  W-045d2 D  W-045d3 D  W-045e D  W-045f D  W-045g D  W-045g2 D  W-045g3 D  W-045h D  W-045i D  W-045j D  W-045k D  W-045k2 D  W-045l D  W-045m D  W-045n D  W-045o D  W-045o2 D  W-045o3 D   ← COMPLETE (added by D-043)
Phase 5    W-046 D  W-046b ·  W-047 ·  W-048 ·  W-049 ·  W-050 ·  W-051 ·  W-052 ·  W-052b ·  W-052c ·  W-053 ·
Phase 6    W-054 ·  W-055 ·  W-056 ·  W-057 ·  W-058 ·
Phase 7    W-059 ·  W-060 ·  W-061 ·  W-062 ·  W-063 ·  W-064 ·  W-065 ·  W-066 ·  W-067 ·  W-068 ·  W-069 ·  W-070 ·
Phase 8    W-071 ·  W-072 ·  W-073 ·  W-074 ·  W-075 ·  W-076 ·  W-077 ·  W-078 ·  W-079 ·  W-080 ·
Phase 9    W-081 ·  W-082 ·  W-083 ·  W-084 ·  W-085 ·  W-086 ·  W-087 ·  W-088 ·  W-089 ·
Phase 10   W-090 ·  W-091 ·  W-092 ·  W-093 ·  W-094 ·  W-095 ·
Phase 11   W-096 ·  W-097 ·  W-098 ·  W-099 ·  W-100 ·  W-101 ·  W-102 ·  W-103 ·  W-104 ·  W-105 ·  W-106 ·  W-107 ·  W-108 ·  W-109 ·  W-110 ·  W-111 ·
Phase 12   W-112 ·  W-113 ·  W-114 ·  W-115 ·  W-116 ·  W-117 ·  W-118 ·  W-119 ·  W-120 ·  W-121 ·  W-122 ·  W-123 ·
Phase 13   W-124 ·  W-125 ·  W-126 ·  W-127 ·  W-128 ·  W-129 ·  W-130 ·  W-131 ·  W-132 ·  W-133 ·
Phase 14   W-134 ·  W-135 ·  W-136 ·  W-137 ·  W-138 ·  W-139 ·  W-140 ·  W-141 ·  W-142 ·  W-143 ·  W-144 ·  W-145 ·  W-146 ·
```

Counts — done **75** / 178 live · in flight **0** · blocked **0**  ← computed by script; 178 = 146 base ids − 8 retired + 40 suffixed

---

## Completed items

<!-- One line per DONE item, appended in order. Never edit an existing line.
     Format:  W-0nn | YYYY-MM-DD | <one-line summary> | <verify command that passed> -->

W-001 | 2026-09-19 | Four modulith modules declared, all Type.CLOSED; taxplanning OPEN→CLOSED | `./mvnw test -pl backend -Dtest=WealthLensModulithTest`
W-002 | 2026-09-19 | Money ArchUnit rule amended for ..taxengine.money..; 2 guards added (9→11 rules) | `./mvnw test -pl backend -Dtest=ArchitectureTest`
W-003 | 2026-09-19 | rulepacks resource directory created — Phase 0 complete | `test -d backend/src/main/resources/rulepacks`
W-004 | 2026-09-19 | 4 engine-purity rules + the withdrawal gate (11→16 tests) | `./mvnw test -pl backend -Dtest=ArchitectureTest`
W-005 | 2026-09-19 | Money — BigDecimal, scale 2, s.516 rounding. No test yet (W-006) | `./mvnw test -pl backend -Dtest=ArchitectureTest`
W-006 | 2026-09-19 | MoneyTest — 17 tests incl. the 1,234.99→1,230 discriminator | `./mvnw test -pl backend -Dtest=MoneyTest`
W-007 | 2026-09-19 | Rate + RateTest — 9 tests; cess case sourced from T2-1 | `./mvnw test -pl backend -Dtest=RateTest`
W-008 | 2026-09-19 | 7 identifier records + shared validator; 2 spec bugs found | `./mvnw test -pl backend -Dtest=IdentifierTest`
W-009 | 2026-09-19 | IdentifierTest — 73 tests, incl. both erratum regressions | `./mvnw test -pl backend -Dtest=IdentifierTest`
W-010 | 2026-09-19 | Provenance, Instrument, GoverningInstruments + 2 enums; I1 enforced at construction | compiles
W-011 | 2026-09-19 | TemporalAxis, DateRange, Validity (3 variants) | `./mvnw test -pl backend -Dtest=ValidityTest`
W-012 | 2026-09-19 | ValidityTest — 16 tests — Phase 1 complete | `./mvnw test -pl backend -Dtest=ValidityTest`
W-046 | 2026-09-25 | `TaxByBasisNode` (D-071): `produces tax.bybasis`, `requires round.totalincome`, rules the two shipped ladders; applies `regime.rateTable()`'s in-force `SlabLadder`; `TaxByBasis.atSlabRatesOnly`; one `TAX` line with the version's provenance; no rounding (RATE-ROUND-003). Hand-derived: new regime 12L → 60,000; 4L → 0; 24,00,001 → 3,00,000.30; First Schedule 10L → 1,12,500. Age ladders + selection → `W-046b` | `TaxByBasisNodeTest` 6/6 + bite probe; clean unit 1495/1495
W-045d3 | 2026-09-25 | Code half of the naming (D-070): `ElectionRoute` `S202_4_B` → `NO_BUSINESS_OR_PROFESSION` in the shipped regime file, 5 broken roots, javadoc, tests; example/test codes renamed (`SPECIAL_SETOFF`, `FIRST_SCHEDULE_RATES`, `ALSO_DEFAULT`, `SUCCESSOR_REGIME`, `FUTURE_REGIME`, `NOVEL_ELECTION`); `RuleIdNamingTest` refuses OLD/NEW fused with REGIME; shipped lock law checksum regenerated → `2026-27.law.5`; `dangling-rate-table` and `rule-edited-without-lock` locks follow (each root still fails for its one defect); `git grep -E 'S[0-9]{3}_' -- backend` = only the deliberate `S202_DEFAULT` | 13 classes 254/254 + 2 bite probes; clean unit 1489/1489
W-045d2 | 2026-09-25 | Rule-ID mapping (D-069, docs only): 52 rulebook IDs → 46 concept names, each from its own provision (S2 ss.93, 156, 202; Rules 2026 r.15 Tables I–IV, r.279, r.280; FA 2026 ss.2–3), all chosen by the owner (M1); one erratum under `01-rulebook.md` R3; `tools/check_rule_id_mapping.py` (every old ID once, every new ID passes `RuleIdNamingTest`) | check 52/52, 0 problems + 3 bite probes (exit 1); clean unit 1488/1488
W-045o3 | 2026-09-25 | FA 2026 applied to the 2025-Act list (D-068): s.354A inserted (FA 2026 s.81), ss.443, 447 omitted (ss.100, 102), all in force 1-4-2026 (s.1(2)(a)); other whole-section changes are same-number substitutions; 354A → DEFERRED/BUILT_LATER; 2025 in force 535; `parse_act_2025.py` gains `AMENDMENTS`. Found, not applied: the Income-tax (Amendment) Ordinance, 2026 (Schedule IV only) and the Taxation and Other Laws (Amendment) Bill, 2026 (unread, enactment unconfirmed) | `CoverageRegisterTest` 16/16 + bite probe; clean unit 1488/1488
W-045o2 | 2026-09-25 | Coverage register completed for the 1961 Act as in force for AY 2026-27 (D-067): 951 numbers (131 omitted, 820 in force, 52 chapters) from the thc.nic.in arrangement (to Act 7 of 2019) + a chain of 67 insertions (1 unsourced: 245-OA) and 11 omissions (4 unsourced: 76, 77, 186, 269S), each other one read in the amending Act (`tools/build_act_1961.py`); 5 entries UNSOURCED (R1); classified by the owner-approved table — 101 PLANNED, 1 MODELLED, 363 NOT_COMPUTATIONAL, 355 DEFERRED (22 BUILT_FIRST); `SectionRef` takes hyphenated numbers; Act lists carry `omitted`. Found: FA 2026 amends the 2025 Act → `W-045o3` | `CoverageRegisterTest` 16/16 + bite probe; clean unit 1488/1488
W-045o | 2026-09-25 | Coverage register completed for the 2025 Act (D-038, D-066): 536 sections parsed from S2 (`tools/parse_act_2025.py` → `content/acts/INCOME_TAX_ACT_2025.json`, continuous 1–536, 23 chapters); classified by the owner-approved chapter table — 83 PLANNED, 1 MODELLED, 237 NOT_COMPUTATIONAL, 215 DEFERRED (18 BUILT_FIRST backlog gaps, 1 BUILT_SECOND, 196 BUILT_LATER); `Coverage.Deferred`; register checked against the Act list; counts logged. 1961 Act → `W-045o2` | `CoverageRegisterTest` 14/14 + 2 bite probes; clean unit 1486/1486
W-045n | 2026-09-25 | TY 2025-26 sources for the Phase 5 rules (research; D-065): Q1 `REBATE-OLD-001` figures sourced (FA 2019 s.8 + every Finance Act to 2025, only provisos amended); Q2 `RATE-CESS-001` 1961 side corrected to FA 2026 s.2(6); Q3 1961 ss.288A/288B and Q4 D-052 not reached (403/504, a scanned PDF) → UNVERIFIED with whatToCheck; per-rule table for `W-052c` in `plans/W-045n.md` Findings | rulebook diff additions only (2 errata); no code
W-045m | 2026-09-25 | Known-errors notice on the old compute endpoint (OQ-3 → D-063 warn; D-064): `taxplanning/salary/dto/KnownErrorsComputation` (`@JsonUnwrapped` computation + `knownErrors`), returned by `TaxPlanningController.computeTax`; response shape otherwise unchanged. The PDF report endpoint is not warned (proposal below) | `TaxPlanningControllerIntegrationTest` 14/14 + bite probe (bare entity → `knownErrors` null); unit 1481/1481
W-045l | 2026-09-25 | Assessee family (D-039, D-062): `input/{Assessee,Individual,Huf}` replace `TaxpayerFacts` (deleted; `TaxpayerFactsTest` → `AssesseeTest`, family/ledger tests kept); `PersonType.HUF`; `EngineInputs.assessee`; I5 counts only person types the pack's regimes name (no regime serves HUFs — s.202's heading is all the rulebook sources); fixtures folded into `NodeHarness.plainTaxpayer()` | Verify 6 classes green + I5 bite probe (8 fail with the old loop); clean unit 1481/1481
W-045k2 | 2026-09-25 | 80C and HRA catalogue entries (D-061): `SEC-INVESTMENT-DEDUCTION` (1961 s.80C / 2025 s.123; 23 routes from `DED-80C-ITEMS-001` + an "other Schedule XV items" route, C1a) and `SEC-HOUSE-RENT-ALLOWANCE` (1961 s.10(13A) / 2025 Sch. III Sl. 11); every link `NotModelled` (rules come with `W-076`/`W-067`); no tenure, cap or count in text (C2a); `coverage.json` + 3 `PLANNED` rows; ratchet 2 → 5 sections; no Java change | `SectionCatalogueTest` 20/20 + 2 bite probes; clean unit 1476/1476
W-045k | 2026-09-25 | Section catalogue (D-042, D-060): `catalogue/{SectionCatalogue,SectionCatalogueEntry,Route,Disqualifier,DisqualifierKind,EnforcedBy}`; `content/sections/SEC-DEFAULT-TAX-REGIME.json` (s.202 / s.115BAC; routes link both slab rules, disqualifiers link `HasIncomeUnderHead` or say `NotModelled`); `TimelineReader` carries `content/sections/` verbatim, no lock change; SC-1 full for `MODELLED` + `PLANNED` ratchet, SC-2 text scan, SC-3 rules resolve, SC-4 derived fields refused; split — 80C/HRA entries → `W-045k2` | `SectionCatalogueTest` 20/20 + 10 bite probes; clean unit 1476/1476
W-045j | 2026-09-24 | Coverage register (D-038, D-059): `coverage/{SectionRef,Coverage,CoverageRegister}`; `content/coverage.json` with the 2 sections the timeline cites (2025 s.202, 1961 s.115BAC, `MODELLED`); `verify` refuses unregistered citations and unpublished `MODELLED` rules; not compiled into year packs; "105 sections" found to be 116 mixed-Act citations → seeding moved to `W-045o` (erratum) | `CoverageRegisterTest` 9/9 + 2 bite probes; clean unit 1456/1456
W-045i | 2026-09-24 | `NodeIds` generated from the timeline's node catalogue (D-058, STATE Q1 answered): 9 constants; stale file fails the build; every regime `nodeRef` must have a constant (a check nothing made before); `PipelineRunnerTest` `components` → `INPUTS_COMPONENTS` (drift gone), real slots in `NodeHarnessTest` switched | 43/43 + 2 bite probes; clean unit 1447/1447
W-045h | 2026-09-24 | `PackDiff` (D-057) in `timeline/`: between two compiled packs — `RuleAdded`/`RuleRemoved`/`VersionAdded`/`VersionRemoved`/`ValueChanged` by path; a version keyed by start date; notes ignored; regimes not diffed (→ `W-139`, erratum) | `-Dtest=PackDiffTest` 7/7 + 2 bite probes; unit 1445/1445
W-045g3 | 2026-09-24 | Per-year copy removed (D-056): `rulepacks/2026-27/` deleted; `ConstantGenerator`, `RuleIdNamingTest` read the timeline; drift guard → hand-listed identities; 2 equality tests retired; `PackJsonWellFormedTest` → timeline files + lock; `PackJsonTest` → compiled 2026-27 files, mutation regexes for canonical JSON | 6 test classes green; 2 bite probes; clean unit 1438/1438 (−2 retired, −7 parse cases)
W-045g2 | 2026-09-24 | Provider loads from timeline + lock (D-055): `ClasspathRulePackProvider` moved to `timeline/` (no package cycle), reads `<year>.lock.json`, compiles, verifies, assembles; JAR timeline refused naming the gap; fixture → one-node timeline + 3 locks (incl. a real wrong-year case); broken packs → 5 roots, one defect each (new: rule edited without lock); 42 per-year files removed; split → `W-045g3` | 59/59 + lock.verify probe (4 fail); unit 1447/1447
W-045g | 2026-09-24 | Rule-pack compiler (D-054): `RulePackCompiler`, `CompiledPack`, `RulePackLock`; canonical JSON (sorted keys, no whitespace, `_` notes dropped); `rulepacks/2026-27.lock.json` (law.4/exec.2/forms.2/content.2, per-rule checksums); compiled 2026-27 == today's pack layer by layer; `RulePackLayer.files()`, `RulePackRef.checksumOf` shared; split → `W-045g2`; startup wiring UNOWNED | `-Dtest=RulePackCompilerTest` 8/8 + 4 bite probes; unit 1446/1446
W-045f | 2026-09-24 | Timeline (D-034, D-053): `rulepacks/timeline/` — 2 rules, 2 regimes, 9 nodes, one file per identity; `Timeline` + `TimelineReader` (refuses unparseable, misnamed, overlapping, unknown-directory, non-JSON files, naming each); `Validity.window()` moved from `RuleRow`; test asserts timeline == 2026-27 pack; `W-045g` erratum | `-Dtest=TimelineReaderTest` 10/10 + 3 bite probes; unit 1438/1438
W-045e | 2026-09-24 | Three status axes (D-036, D-052): `LegalStatus`/`PublicationStatus` on `RuleVersion`, `VerificationLevel` on `Provenance`/`Instrument`/`AmendmentRef`; `VerificationStatus`, `DraftOrFinal` deleted and gated; `RulePack` refuses non-`PUBLISHED`; PENDING_NOTIFICATION ⇔ `Validity.PendingNotification`; packs migrated, law `2026-27.law.3`; `W-045n` erratum (date meaning of WITHDRAWN/STRUCK_DOWN) | `-Dtest=RuleStatusTest,RulePackLoadTest,ArchitectureTest` 47/47; unit 1428/1428
W-045d | 2026-09-24 | OQ-4 (D-051): `S202_DEFAULT`→`NEW_TAX_REGIME`, `S202_OPTED_OUT`→`FIRST_SCHEDULE_RATES`, `RATE-SLAB-NEW-2026`→`RATE-SLAB-NEW-TAX-REGIME`, `RATE-SLAB-OLD-2026`→`RATE-SLAB-FIRST-SCHEDULE` (the 2025 Act heads s.202 "New tax regime", S2 p.226); law layer `2026-27.law.2`; broken packs re-checksummed; `RuleIdNamingTest`; rulebook R3 erratum (7 IDs); `W-045d2` split out (51 IDs + `S202_4_B`) | `-Dtest=RuleIdNamingTest,GeneratedConstantsTest,RulePackLoadTest` 29/29; unit 1412/1412
W-045c1 | 2026-09-24 | Deleted L1 legacy dead code: `FbpOptimizer`, `ItrFormAdvisor`, `HrLetterContentBuilder`, `ParsedCtcDto` (package `parsing/` gone), `MarginalReliefCalculator` + its 7-test class, empty `_review/` dir; TS:121 name-collision list drops `ItrFormAdvisor` (Changes row) | `./mvnw test -pl backend -Punit` 1407/1407
W-045c | 2026-09-23 | Appendix D (Phase 5–6 audit), Appendix E (slices→phases; legacy deletions in compiling order); W-048 spec fixed; W-049–W-052 Produces/Verify + law rows; pack is **2** rule rows not 15 (my error, corrected); T13-1 citation fixed; 4 corrections + W-045c1 accepted (D-050) | six checks; unit 1414/1414
W-045b3 | 2026-09-23 | Deleted `taxengine_does_not_swallow_failures` (D-049, OQ-5 closed); `IllegalCatch` proven on `taxengine` main code | probe + `./mvnw test -pl backend -Punit` 1414/1414
W-045b2 | 2026-09-23 | Two tax modules (D-048): `taxrules` → `taxengine.rules` (134 files, `git mv`), `taxdocs` removed; `taxengine` CLOSED with named interfaces `input`, `result`, `money`, `id` (`propagate` did not carry `Money`/`TaxYearId` — probe); `taxplanning` → `taxengine :: *`; `rules_do_not_depend_on_the_runner`; swallow rule kept at prior scope, found miswritten (**OQ-5**, before W-105) | 6 probes; `./mvnw test -pl backend -Punit` 1415/1415
W-045b | 2026-09-23 | Status board and counts corrected (51/168); START-HERE §11 ID glossary (~35 families, citation rule); R7 note; Appendix C ownership sweep — 37 multi-owner types classified, 3 created-twice rows fixed (W-055, W-112, W-067–069); Appendix C part 2 lists the unowned with **proposed** owners (not accepted — still UNOWNED); `O-n`→`OQ-n`, `B-n`→`BP-n` | six scripted checks; `./mvnw test -pl backend -Punit` 1414/1414
W-045a | 2026-09-23 | Strict Checkstyle 10.26 failing the build (main + test), shrink-only baseline (99 entries, `CheckstyleBaselineTest`), FileLength 1500 by the owner; ArchUnit: no generic suffixes, unique simple names (DateRange, ExcelParser grandfathered); spotless:check on validate; commit-gate hook; 7 tax-module violations fixed; placeholder `taxengine/TaxEngine` deleted; all gates proven to bite | `./mvnw test -pl backend -Punit` — 1414/1414, 0 Checkstyle violations
W-043 | 2026-09-21 | TaxComputation/FilingPosition/ComputationId/ComputationReceipt + 21 tests; A6's lawAndExec corrected to a Map (one ref cannot hold two layers); FilingPosition ships 3 of 6 per **D-032**; found ComputationReceipt owned by two items. **Phase 4 complete** | `./mvnw test -pl backend -Dtest=TaxComputationTest`
W-042 | 2026-09-21 | PipelineRunner + nested Result + 16 tests; **found and fixed B-004** (two committed items contradicted each other — no graph could pass both W-039 and W-040); runner renumbers the trace; no try/catch, asserted on the source; T9-20 | `./mvnw test -pl backend -Dtest=PipelineRunnerTest`
W-044 | 2026-09-21 | NodeHarness (test scope) + 13 tests; runs a node with no pipeline against the **real shipped pack**; refuses both a missing stub and an unused one; owns `plainTaxpayer()`, the one EngineInputs fixture; T23-6 inherited from W-039's scoped context | `./mvnw test -pl backend -Dtest=NodeHarnessTest`
W-040 | 2026-09-21 | TopologicalSort — canonical order (ties on NodeId, D-030), cycle named as a path, missing producer named; found my own arrow direction contradicted B3's mermaid and fixed the code not the test; 17 tests | `./mvnw test -pl backend -Dtest=TopologicalSortTest`
W-039 | 2026-09-21 | ComputationNode/NodeContext/NodeResult/NodeRegistry + **I3 built (inert, D-029)** and **I18 shown already enforced**; per-node context scoping fixes B3's unimplementable sketch; rule() scoped too; 22 tests. 2 errata | `./mvnw test -pl backend -Dtest=NodeRegistryTest`
W-038 | 2026-09-21 | InputsFingerprint — structural walk of EngineInputs by reflection, **property proven by probe**; F6's hand-written list wrong 4 ways (erratum); pack stays out per G-11 (**D-028**); 26 tests incl. a 14-case perturbation suite | `./mvnw test -pl backend -Dtest=InputsFingerprintTest`
W-037c | 2026-09-21 | EngineInputs (10 components) + RegimeBehaviour.adjustPipeline + **L5 guard 3, proven to bite** + 8 tests; no inputs parameter per **D-027** (it would be a module cycle); component-location test makes B-003 a regression guard. **D-025 split closed** | `./mvnw test -pl backend -Dtest=EngineInputsTest,ArchitectureTest`
W-037b | 2026-09-21 | ElectionState + RateBasisHint (both moved by erratum) + RegimeElectionSnapshot/FilingFacts/CapitalGainsInput/EnteredFacts + 20 tests; EnteredFacts is a zero-component seam per **D-026**; dated advance-tax instalments; B-003's module trap hit a 4th time (`CapitalGainsType`) | `./mvnw test -pl backend -Dtest=RegimeElectionSnapshotTest`
W-037 | 2026-09-21 | TaxpayerFacts/FamilySnapshot/LedgerSnapshot + 21 tests; first slice of the D-025 split, projections per D-024, **date of birth not a derived age**; 4 types nested to hold Produces; found the enum-constant audit gap | `./mvnw test -pl backend -Dtest=TaxpayerFactsTest`
W-036 | 2026-09-21 | TraceLine/TraceAnnotation/Notice/StepKind/Severity + 19 tests; A8's two-layer split, `TraceAnnotation` reduced and `label` omitted per **D-023**; found the third type-audit blind spot; 2 errata on A8 | `./mvnw test -pl backend -Dtest=TraceLineTest`
W-035 | 2026-09-21 | NodeValue/MoneyValue/TaxByBasis/SpecialRateId + 12 tests. Supersedes the blocked row below: B-002 resolved by **D-022** (`taxrules` → `Type.OPEN`), T1 erratum written | `./mvnw test -pl backend -Dtest=NodeValueTest` and `-Punit` (1225/1225)
W-035 | 2026-09-19 | **NOT DONE — BLOCKED on B-002.** NodeValue/MoneyValue/TaxByBasis/SpecialRateId + 12 tests written and green; the modulith test fails. Committed as WIP, not as an item | `./mvnw test -pl backend -Dtest=NodeValueTest` passes; `-Punit` does not
W-034b | 2026-09-19 | PackJson.construct + 19 wrap sites; E4's property holds and is a 5-mutation property test; found SlabLadder's own pack error had no path | `./mvnw test -pl backend -Dtest=PackJsonTest,RulePackLoadTest`
W-034 | 2026-09-19 | RulePackLoadTest — T9-1..T9-5, T9-7 end to end + 4 broken fixture packs; found E4's property broken (→ W-034b); 12 tests. **Phase 3 complete** | `./mvnw test -pl backend -Dtest=RulePackLoadTest`
W-033c | 2026-09-19 | RegimeJson, RegistryJson, JsonPackAssembler; the shipped pack assembles and computes T2-1; removed the empty DIS-S202-2A that W-029's invariant refused; 47 tests | `./mvnw test -pl backend -Dtest=PackJsonTest`
W-033b | 2026-09-19 | ConditionJson (13 variants) + RuleJson; EvidenceRecorded reads `evidenceType` (discriminator collision); 36 tests | `./mvnw test -pl backend -Dtest=PackJsonTest`
W-033 | 2026-09-19 | PackJson/ValueJson/ShapeJson — leaves, Validity, RegimeApplicability, all 6 CapShapes; real ladder parses and computes T2-1; Jackson 2/3 split fixed; 24 tests | `./mvnw test -pl backend -Dtest=PackJsonTest`
W-032b | 2026-09-19 | RuleIds/ComponentCodes committed + ConstantGenerator + verification test; both DR 19 properties tested; no build plugins added; 12 tests | `./mvnw test -pl backend -Dtest=GeneratedConstantsTest`
W-032 | 2026-09-19 | The real 2026-27 pack: manifest + 6 layer files, real checksums; RATE-SLAB-NEW-2026 and RATE-SLAB-OLD-2026; 21 tests | `./mvnw test -pl backend -Dtest=PackJsonWellFormedTest`
W-031b | 2026-09-19 | RegimeBehaviour SPI + registry + DataDrivenRegimeBehaviour; invariant I9 enforceable both ways; guard 3 moved to W-037; 7 + 4 tests | `./mvnw test -pl backend -Dtest=RegimeBehaviourRegistryTest`
W-031 | 2026-09-19 | TaxYearRegistry + Entry + UnsupportedTaxYearException; dropped stale `Regime defaultRegime`, fixed the `RulePackRef` collision; 10 tests | `./mvnw test -pl backend -Dtest=TaxYearRegistryTest`
W-030 | 2026-09-19 | I13/I14/I15 + MidYearCombination; 8 invariants redistributed by D-016; I15 caught 3 of my own fixtures; 23 tests | `./mvnw test -pl backend -Dtest=PackValidatorTest`
W-029 | 2026-09-19 | PackValidator (I2 generalised, I5, I6, I7) + DisallowanceSet/ConditionSet; I1/I4 shown enforced elsewhere, I3 deferred with a reason; 13 tests | `./mvnw test -pl backend -Dtest=PackValidatorTest`
W-028 | 2026-09-19 | RulePackProvider SPI + ClasspathRulePackProvider + PackAssembler seam; synthetic 2099-00 fixture + tampered 2098-99; 9 tests. TJsonMapper.readValue made public | `./mvnw test -pl backend -Dtest=ClasspathRulePackProviderTest`
W-027 | 2026-09-19 | RulePack + RulePackLayer + RulePackRef; cache key on law+exec only (G-11 now a test); 14 tests | `./mvnw test -pl backend -Dtest=RulePackLayerTest`
W-026c | 2026-09-19 | RegimeSpec + PipelineSpec + 3 id types; isDefault implies DEFAULT_NO_ACTION; 35 tests. Regime work complete | `./mvnw test -pl backend -Dtest=RegimeLifecycleTest`
W-026b | 2026-09-19 | ElectionProtocol, BindingSpec, AfterWithdrawal + 5 vocabulary types; rule 136 enforced at construction; 26 tests | `./mvnw test -pl backend -Dtest=RegimeLifecycleTest`
W-026 | 2026-09-19 | RegimeLifecycle (two windows) + ContinuationPolicy + InvalidationPolicy; 13 tests. Also restored 6 Produces lists D-014 had collapsed | `./mvnw test -pl backend -Dtest=RegimeLifecycleTest`
W-025 | 2026-09-19 | Component registry: ComponentDefinition (union of E4.1 + V6 + informationalOnly), lifecycle, DeprecationPolicy, 3 enums; 15 tests | `./mvnw test -pl backend -Dtest=ComponentLifecycleTest`
W-023 | 2026-09-19 | Temporal core: RuleVersion, RuleRow, AmendmentRef, Provision/Presence, TemporalContext, 2 exceptions, RegimeApplicability; 18 tests. Merged W-024; swept 11 `compiles` rows | `./mvnw test -pl backend -Dtest=RuleRowTest`
W-021c | 2026-09-19 | AllOf/AnyOf/Not; short-circuit semantics; Condition permits all 13 — algebra closed; 47 tests. **Phase 2 complete** | `./mvnw test -pl backend -Dtest=ConditionTest`
W-021b | 2026-09-19 | HeadCode, EvidenceType + 6 amount/structure predicates; ConditionContext grows 5 facts; 32 tests | `./mvnw test -pl backend -Dtest=ConditionTest`
W-021 | 2026-09-19 | Condition + ConditionContext + 4 person-fact predicates + PersonType/DisabilitySeverity/AgeSubject; AgeAtLeast gains a subject; 17 tests | `./mvnw test -pl backend -Dtest=ConditionTest`
W-019 | 2026-09-19 | PerUnitPerPeriod; Money.times(int); CapShape permits all six — family closed; 17 + 4 tests | `./mvnw test -pl backend -Dtest=PerUnitPerPeriodTest`
W-018 | 2026-09-19 | AggregateWithCeiling + AggregateOutcome; CapContext front-loaded to 3 maps; 15 tests incl. T5-1/T5-2/T5-3 | `./mvnw test -pl backend -Dtest=AggregateWithCeilingTest`
W-017 | 2026-09-19 | LeastOf + Limb; first-declared wins on tie; no flooring; 12 tests incl. the HRA worked example | `./mvnw test -pl backend -Dtest=LeastOfTest`
W-015 | 2026-09-19 | SlabLadder + Band + validate() (4 checks), PackValidationException; 20 tests; all 7 rulebook figures | `./mvnw test -pl backend -Dtest=SlabLadderTest`
W-013 | 2026-09-19 | CapShape (permits 2 of 6), CapResult, CapContext, Fixed, PercentOfBase, BaseId + 11 tests; Money/Rate moved to `taxrules.money` per B-001/D-012 | `./mvnw test -pl backend -Dtest=CapShapeTest`

---

## ✅ READ FIRST — the build is GREEN

```
$ ./mvnw test -pl backend -Punit
Tests run: 1225, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS   (8.0 s — NFR N12 budgets 20 s)
```

**B-002 is closed** (decision **D-022**, session 3): `taxrules` is now `Type.OPEN`;
`taxengine`, `taxdocs` and `taxplanning` remain `CLOSED`. One line in one `package-info.java`.
`02-TECH-SPEC.md` T1 carries the erratum recording why it could not be satisfied as written.

`W-035`'s code was carried in a `wip(taxplanning)` commit across the block. It is now committed
properly as `taxplanning W-035:` and the checkpoint has advanced off `W-034b`.

---

## Blockers

### B-004  (W-042)  — 2026-09-21  — **RESOLVED 2026-09-21**: option (A), a node may require nothing (**D-031**)

**What I needed:** to run any graph at all through `PipelineRunner`.

**What I looked at:** `NodeRegistry.validate()` (`W-039`); `TopologicalSort.of()` (`W-040`);
`NodeRegistryTest.aNodeRequiringNothingIsRejected`; `TopologicalSortTest` lines 45, 84, 170, 213;
`DR 15` B2/B3; `DR 23` A11.

**What is wrong — two committed items contradict each other, and no graph satisfies both.**

| | |
|---|---|
| `W-040` **requires** a source | `TopologicalSort` rejects a required slot with no producer. Every acyclic graph must therefore contain at least one node that requires nothing. Its own tests rely on this: `orderOf(List.of(node(COMPONENTS)))` passes with empty `requires()` |
| `W-039` **forbids** a source | `NodeRegistry.of` throws on empty `requires()`. Its own test asserts it: `aNodeRequiringNothingIsRejected` |

Together: a graph with a source is refused by the registry; a graph without one is refused by the
sort. **`W-042` is the first item that passes a graph through both**, which is why it surfaced only
now — the same vacuous-until-first-used shape as **B-003**, where `taxengine` had no classes for 34
items.

`W-039`'s stated reason was *"everything the engine reads arrives through a slot, so a node with no
edges is reading something it did not declare."* That premise is wrong: there are **two** legitimate
sources of data, upstream slots **and** `EngineInputs` (reachable as `ctx.inputs()`, covered by the
fingerprint), and only the first is an edge. A source node reads the second.

**What I would need to proceed — a decision.**

- **(A) Relax `NodeRegistry`: a node may require nothing.** *Recommended.* One condition removed,
  one test inverted. The check it gives up is already made, and made more strongly, by
  `NodeContext.get`, which throws on reading any undeclared slot at the moment it happens — so the
  registry rule is redundant with a runtime guarantee, not the only line of defence.
- **(B) Input-backed slots.** The runner pre-seeds slots from `EngineInputs` and nodes require
  those. Closer to A11, which writes `ctx.get(NodeId.FILING_FACTS, FilingFacts.class)`. But it needs
  `TopologicalSort` to know which ids are seeded (a signature change to `W-040`), it needs the
  **`NodeId` anchors** decision that has now been open for four items, and `FilingFacts` is not a
  `NodeValue` — the sealed family permits only `MoneyValue` and `TaxByBasis`, so A11's line does not
  type-check either. Three unresolved things at once.
- **(C) (A) now, (B) when a real node needs an input slot.** Unblocks today and keeps (B) a
  deliberate design decision rather than one taken under pressure from a blocked item.

Either (A) or (B) edits `W-039`'s or `W-040`'s `Produces`, which is why this stopped.

**Resolution (2026-09-21, D-031): option (A), as recommended.** `NodeRegistry` now requires
`requires()` to be non-null and permits it to be empty. `NodeRegistryTest.aNodeRequiringNothingIsRejected`
is replaced by `aSourceNodeMayRequireNothing`, plus `aNullRequiresIsStillRejected`. Erratum written
on `W-039`'s Verify column in `03-WORK-ITEMS.md`, which is where the "non-empty" wording came from.
The first `PipelineRunnerTest` was deleted and rewritten on a valid graph. Build green, 1389 tests.

Nothing was lost by relaxing it: reading an **undeclared** slot is still refused by
`NodeContext.get`, at the moment it happens, and `PipelineRunnerTest.aNodeReachingForAnUndeclaredSlotFailsThroughTheRunnerToo`
shows that guarantee survives assembly.

### B-003  (W-037)  — 2026-09-21  — **RESOLVED 2026-09-21**: option (A), `taxengine` owns its input projections (**D-024**)

**No code was written.** The stop is at stage 2, which is where `00-START-HERE.md` §7 and R6 want it.

**What I needed:** to build `EngineInputs` **verbatim** from `DR 23` A3, as `W-037`'s Spec column
requires.

**What I looked at:** `DR 23` A3 in full; `11-filing-drafts.md` F4 and F4.1; `14-operational-design.md:282`;
`17-regime-lifecycle.md:213`; `09-build-plan.md:99`; `00-SUMMARY.md` R5; `03-WORK-ITEMS.md` rows
`W-038`, `W-082`, `W-085`, `W-099` and Appendix A; `ArchitectureTest.taxengine_fetches_nothing`;
`taxengine/package-info.java`. A per-type existence grep over all ten `EngineInputs` components.

**What is wrong — A3 fails its own rule.** Three of the ten components name types that live, or will
live, in modules `taxengine` may not depend on:

| Component | Type | Owner | Module | `taxengine` may depend on it? |
|---|---|---|---|---|
| `entered` | `DraftInputs` | `W-099` (Phase 11) | `taxplanning` | **No** — forbidden twice over |
| `capitalGains` | `CapitalGainsSummary` | `W-085` (Phase 9) | `portfolio` (a published read-model) | **No** — not in T1's list |
| `elections` | `ElectionState` (inside `RegimeElectionSnapshot`) | `W-082` (Phase 9) | undecided | sequencing only, no conflict |

`taxengine` declares `allowedDependencies = {"shared", "taxrules"}`, and `ArchitectureTest`'s
`taxengine_fetches_nothing` — **green in the build since `W-004`** — forbids any `..taxengine..`
class from depending on `..taxplanning..`, *"because everything the engine may read arrives in
EngineInputs"*. A3 proposes that very rule, three code blocks below the record that violates it.

A3 also **contains its own answer**: its javadoc says `EngineInputs` is *"Assembled by taxplanning
from persistence; consumed by taxengine"* and *"This is deliberately **NOT** `DraftInputs`"* — then
names `DraftInputs` as a component, re-importing the thing it just said it was not.

**Why this cannot be deferred the way `W-036`'s gap was.** `W-038`, the **very next item**, builds
`InputsFingerprint` over this record, and A3's own INFERENCE is that the fingerprint works *because*
`EngineInputs` is one complete object — *"a field added to `EngineInputs` is in the key
automatically, which is the only way that invariant survives."* `W-038`'s Verify demands **any**
field change produce a different fingerprint. Ship `EngineInputs` without capital gains and the
cache key silently omits them: two computations differing only in capital gains collide. That is a
wrong number served from cache, the same class of defect as R-5.

**What I would need to proceed — a decision between three options.**

- **(A) `taxengine` owns input projections; `taxplanning` maps into them.** `EngineInputs` holds
  `EnteredFacts` and a `taxengine`-owned capital-gains input, both declared here; `taxplanning`
  converts `DraftInputs` and `portfolio`'s read-model at assembly. Faithful to A3's *javadoc* and to
  D-PURE, and it keeps `EngineInputs` complete, so `W-038` is sound. Cost: two extra types in this
  item, and a mapping layer owed at `W-099`/`W-085`. My recommendation.
- **(B) Widen `taxengine`'s `allowedDependencies` to `portfolio` and `taxplanning`.** Faithful to
  A3's *code*. It deletes D-PURE, fails `taxengine_fetches_nothing`, and makes the engine's
  reproducibility guarantee unenforceable. Recorded for completeness; I would argue against it.
- **(C) Build `EngineInputs` partial now, complete it at `W-085`/`W-099`.** Cheapest today. But it
  puts `W-038`'s fingerprint on a knowingly incomplete key for ~48 items, and a stale cache key is
  invisible until it serves a wrong number.

All three edit things outside `W-037`'s `Produces` — (A) adds types to it, (B) edits `W-001`'s
`package-info.java`, (C) changes `W-038`'s contract — which is why this stopped rather than
choosing itself.

**Also owed by this item, and unaffected by the above:** L5 guard 3, moved here by `W-031b` —
`RegimeBehaviour.adjustPipeline` plus the ArchUnit rule listing every overriding class against a
reviewed file. It needs `EngineInputs` to exist first, so it is downstream of this decision.

**Resolution (2026-09-21, D-024): option (A), as recommended.** `taxengine` declares its own input
projections; `taxplanning` maps into them at assembly. This is what A3's javadoc already says
(*"assembled by taxplanning from persistence; consumed by taxengine"*) and what its component list
contradicted. `EngineInputs` stays **complete**, which is the property `W-038`'s fingerprint rests
on. No module declaration changes; `taxengine_fetches_nothing` keeps biting.

**Debts this creates, to be honoured by the items named — record them, they are easy to lose:**

| Owed at | What |
|---|---|
| `W-085` | map `portfolio`'s published `CapitalGainsSummary` read-model → `taxengine`'s capital-gains input projection |
| `W-099` | map `DraftInputs` → `EnteredFacts`; this is the boundary A3's javadoc describes |
| `W-082` | `ElectionState` must be visible to `taxengine` — place it in `taxrules` (regime vocabulary, and `RegimeElectionSnapshot` needs it) rather than `taxplanning` |

A3 needs an erratum recording that its `EngineInputs` cannot compile in `taxengine` as written. It
is **not yet written** — it belongs in `W-037`'s report, with the built code as its proof.

### B-002  (W-035)  — 2026-09-19  — **RESOLVED 2026-09-21**: option (B), `taxrules` is `Type.OPEN` (**D-022**)

**What I needed:** `NodeValue`, `MoneyValue` and `TaxByBasis` in `taxengine.pipeline` to use `Money`,
which lives in `taxrules.money`.

**What I looked at:** `02-TECH-SPEC.md` T1; `backend/src/main/java/com/thiru/wealthlens/taxengine/package-info.java`;
a grep for `NamedInterface` across `backend/src/main/java` — **zero occurrences**.

**What is wrong.** `taxengine` declares `allowedDependencies = {"shared", "taxrules"}`, exactly as
T1 specifies, and it still fails:

```
Module 'taxengine' depends on module 'taxrules' via
  com.thiru.wealthlens.taxengine.pipeline.MoneyValue -> com.thiru.wealthlens.taxrules.money.Money.
  Allowed targets: shared, taxrules.
```

The dependency is allowed; the **package** is not. `taxrules` is `Type.CLOSED`, so only its root
package is visible to other modules, and `Money` lives in the internal `taxrules.money`.

T1 declares all four tax modules `CLOSED` **and** has them depend on each other's types — but every
shared type lives in a sub-package. Spring Modulith permits that only through `@NamedInterface`,
which T1 never mentions and nothing in this repository uses. **T1 as written cannot be satisfied.**
It was invisible until now because `taxengine` had no classes; `W-035` is its first.

**What I would need to proceed — a decision between two options.**

- **(A) `@NamedInterface` on every `taxrules` package that forms its API.** Faithful to T1's
  `CLOSED`. But `taxrules` exists to be read by `taxengine`, so this means annotating roughly ten
  packages — `money`, `id`, `cap`, `condition`, `temporal`, `pack`, `regime`, `component`,
  `registry`, `validate` — and writing `"taxrules :: money"`-style entries in three modules.
- **(B) `taxrules` becomes `Type.OPEN`; `taxengine`, `taxdocs` and `taxplanning` stay `CLOSED`.**
  One line. It states the actual design: `taxrules` is the shared vocabulary and its types are
  deliberately public. T1's reason for `CLOSED` — stopping `taxplanning` reaching into engine
  internals — is untouched, because the three modules that have internals keep it.

**My recommendation was (B)**, with an erratum on T1. (A) produces the same visible surface with
more ceremony, and ten "expose this too" annotations tend to become nine plus one forgotten.

Either option edits `package-info.java` files produced by `W-001`, outside `W-035`'s `Produces`,
which is why this stopped rather than deciding itself.

**Resolution (2026-09-21, D-022): option (B), as recommended.** The developer chose it after the
cost of (A) was measured rather than estimated: `taxrules` has **twelve** sub-packages, not ten
(`cap` `component` `condition` `generated` `id` `json` `money` `pack` `regime` `registry`
`temporal` `validate`, 107 files). (A) is worse than the blocker recorded, because Spring Modulith's
`allowedDependencies = "taxrules"` grants only the **unnamed** root interface — so (A) is ~12
`@NamedInterface` annotations **plus** a per-package dependency list in each of `taxengine`,
`taxdocs` and `taxplanning`, and it forces a judgment now on whether `json`, `generated` and
`validate` are public API, a question no item has yet needed to answer.

Changed: `taxrules/package-info.java` only — `Type.CLOSED` → `Type.OPEN`, with a javadoc paragraph
naming D-022 and stating why the other three modules stay `CLOSED`. No consumer changed; all three
still read `allowedDependencies = {"shared", "taxrules", ...}` exactly as T1 draws them.
Verified by `./mvnw test -pl backend -Punit` — 1225/1225, 0 errors.

### B-001  (W-013)  — 2026-09-19  — **RESOLVED**: option (A), `Money`/`Rate` moved to `taxrules.money` (**D-012**)

**What I needed:** `CapShape.apply` to return a `CapResult` carrying a `Money`, and `PercentOfBase`
to hold a `Rate`. Both are `02-TECH-SPEC.md` T3 types living in `..taxengine.money..`; the cap
shapes are T2 types living in `..taxrules.cap..`.

**What I looked at:** `02-TECH-SPEC.md` T1 and T2; `05-domain-model.md` M1.3;
`16-getting-started.md:103` (`SlabLadder.Band(Money lowerExclusive, ...)`).

**What is ambiguous or missing — a contradiction, not a gap.** T1 declares

    taxrules   -> {"shared"}
    taxengine  -> {"shared", "taxrules"}

so the dependency runs **taxengine → taxrules**. But T2 puts `Money` in `taxengine.money` and the
cap shapes in `taxrules.cap`, and a cap shape cannot express a rupee limit without `Money`. That
requires **taxrules → taxengine**, the opposite arrow. The two cannot both hold.

Proof, after writing the item exactly as planned:

    $ ./mvnw test -pl backend -Dtest=WealthLensModulithTest
    - Module 'taxrules' depends on module 'taxengine' via
      com.thiru.wealthlens.taxrules.cap.CapResult -> com.thiru.wealthlens.taxengine.money.Money.
      Allowed targets: shared.       (and 3 more)

This is not specific to `CapResult`. **Every** rule pack holds rupee figures — slab band bounds,
the 80C ceiling, the standard deduction — so `taxrules` needs a money type by its nature, and
`16-getting-started.md:103` already writes `Band(Money lowerExclusive, ...)` inside a `taxrules`
type. The placement of `Money` in `taxengine`, not the cap work, is what is wrong.

**What I would need to proceed:** a decision on where `Money` and `Rate` live. Both options move
files that `W-005` and `W-007` produced and are already committed DONE, which is outside W-013's
`Produces` — hence this blocker rather than an erratum.

- **(A) move to `taxrules.money`** *(recommended)* — `taxengine` already allows `taxrules`, so the
  arrow stays as T1 draws it. Costs: move 2 production + 2 test files; amend `W-002`'s ArchUnit rule
  from `..taxengine.money..` to `..taxrules.money..`; errata in TS T2, T3, T10 and `DR 19` E1.
- **(B) move to `shared`** — needs no dependency change at all, since both modules already allow
  `shared`. But `shared` holds the `double`-based `TMoney`, and two money types in one package is
  the same two-vocabularies failure that `02-lessons.md` L1 describes and `ComputationStep` already
  demonstrated once. `shared` also depends on `portfolio`, which would put a pure value type in a
  module that reaches the domain centre.
- **(C) add `taxengine` to `taxrules`** — a cycle; Spring Modulith rejects it. Not viable.
- **(D) move the cap shapes into `taxengine`** — contradicts T2 and the rules-are-data split that
  the whole four-module design rests on.

The reverted code is complete and passes nothing but compilation; it is kept at
`scratchpad/w013-reverted/` and can be replayed into whichever package you choose.


<!-- Use the exact shape from 00-START-HERE.md §8. Never delete a blocker; mark it RESOLVED. -->

No open blockers — B-001 … B-004 above are all resolved.

---

## Decisions taken during the build

<!-- Anything the design did not settle and a human decided. Format:
     D-00n | YYYY-MM-DD | <question> | <decision> | <who> -->

Recorded on the design's own recommendations (`docs/taxplanning-design/16-getting-started.md` §S1).
**All are reversible at zero cost today; none has been built on yet.**

| ID | Date | Question | Decision | Who |
|---|---|---|---|---|
| D-001 | 2026-09-19 | Q1 — ship TY 2025-26 at launch, or TY 2026-27 only? | **TY 2026-27 first.** It is fully verified; TY 2025-26 detail rules are `NEEDS VERIFICATION` and would put Slice 0 on the critical path | provisional — on recommendation |
| D-002 | 2026-09-19 | Q2 — four modules or one? | **Four** — `taxrules`, `taxengine`, `taxdocs`, `taxplanning` — **superseded by D-048 (two)** | provisional — on recommendation |
| D-003 | 2026-09-19 | Q3 — modulith `Type.CLOSED` or `OPEN`? | **CLOSED.** Greenfield has no flat-layout legacy to accommodate; CLOSED is what makes "the engine cannot reach a repository" enforced rather than conventional | provisional — on recommendation |
| D-004 | 2026-09-19 | Q4 — advisor path within 12 months? | **Seam only** — `AccessPolicy` from day one with a trivial body | provisional — on recommendation |
| D-005 | 2026-09-19 | Q5 — document ingestion in release 1? | **Fast-follow**, behind `app.taxplanning.documents.enabled=false` | provisional — on recommendation |
| D-006 | — | Q6 — who signs off a rule-pack change? | **OPEN — needs a human name.** Does not block any item before `W-032` | — |
| D-071 | 2026-09-25 | `W-046`: the node, and the age-band rows D-050 added to it | **Split:** `W-046` = `TaxByBasisNode` over `regime.rateTable()` (both ladders already in the pack; no pack/lock change); new **`W-046b`** (before `W-053`) = the age ladders `RATE-SLAB-FIRST-SCHEDULE-AGE-60TO79` / `-AGE-80` (not "`RATE-SLAB-AGE-001/002`", which are rulebook statements: resident-only + age at any time during the year; no age variants in the new regime), ladder selection (B1: a conditional ladder that holds replaces `rateTable`; two holding is an error), a `ConditionContext` from `EngineInputs` (B2 age on the last day of the tax year; B3 RNOR as resident — each to be sourced), the lock bump | owner: "approved — A as recommended" |
| D-070 | 2026-09-25 | `W-045d3`: the code half of the naming | `ElectionRoute` `S202_4_B` → **`NO_BUSINESS_OR_PROFESSION`** (S2 s.202(4)(b): *"a person not having income from business or profession"*); example/test codes renamed to concepts (`SPECIAL_SETOFF`, `FIRST_SCHEDULE_RATES`, `ALSO_DEFAULT`, `SUCCESSOR_REGIME`, `FUTURE_REGIME`, `NOVEL_ELECTION`); `RuleIdNamingTest`'s deliberate `S202_DEFAULT` kept; its OLD/NEW pattern widened to `(^|-)(OLD|NEW)(REGIME)?(-|$)`; shipped lock regenerated and bumped to `2026-27.law.5`; the two broken roots that reach the lock check get compiler-regenerated locks, the other three unchanged — each root still fails for its one defect | owner: "approved — B1–B4 as recommended" |
| D-069 | 2026-09-25 | `W-045d2`: scope and shape of the remaining rule-ID mapping | **Split:** `W-045d2` stays docs only — the mapping of **52** IDs (the 51 `RuleIdNamingTest`'s patterns find outside R3, plus `DED-NEWREGIME-001`, which its OLD/NEW pattern misses) as one rulebook erratum, checked by a script; new **`W-045d3`** takes the code half (16 files, incl. the shipped `FIRST_SCHEDULE_RATES.json` + lock), keeping `RuleIdNamingTest`'s deliberate `S202_DEFAULT`. Names = existing head + the provision's own words (sources: S2, Rules 2026 r.15/279/280, FA 2026); statutory `NEW-TAX-REGIME` only for new-regime concepts; year pairs collapse to one ID; two candidates where the wording does not decide; full table stops for owner choice | owner: "approved — A1a, A2, A3 as recommended" |
| D-068 | 2026-09-25 | `W-045o3`: how the 2025-Act list reflects the Finance Act, 2026 | FA 2026 (s.1(2)(a): in force 1-4-2026) inserts s.354A (s.81) and omits ss.443, 447 (ss.100, 102); its other whole-section changes are same-number substitutions. The list gains 354A and keeps 443/447 marked `omitted` with source (no code); `tools/parse_act_2025.py` applies an `AMENDMENTS` table after the parse; 354A → `DEFERRED`/`BUILT_LATER`; other amending instruments are searched for and reported, not applied without approval | owner: "approved — A1–A4 as recommended" |
| D-067 | 2026-09-25 | `W-045o2`: how the 1961 Act's sections are listed and registered | The Act list carries **every** section number of the arrangement plus later insertions, omitted ones marked `"omitted": true` with date and source, so it stays continuous; the register must equal the list minus the omitted. Base: thc.nic.in copy, current to Act 7 of 2019; every amending Act since found by a logged search and read for insertions/omissions and their effective dates (in force for AY 2026-27). One item, two stops (the list and chain; the chapter table). Existing 1961 rows kept; a 2025 classification is reused only where the rulebook states the mapping | owner: "approved — A1a, A2a, A3 as recommended" |
| D-066 | 2026-09-25 | `W-045o`: how the coverage register is completed to every section of both Acts | **Split:** `W-045o` = the 2025 Act (sections parsed from S2 into `content/acts/income-tax-act-2025.sections.json`, continuity-checked); new `W-045o2` = the 1961 Act as in force for AY 2026-27, reconstructed from the thc.nic.in copy (current to ~2019) plus every section inserted/omitted by amending Acts since. A fifth classification **`DEFERRED`** (designed for, not scheduled; D-033 tier `BUILT_SECOND`/`BUILT_LATER`) — brief §7.1 erratum. Classification by a **chapter table the owner approves** before it is applied, with per-section exceptions stated. Omitted 1961 sections not registered. One JSON entry per line. Counts in code (the row's "no code" cannot meet its own Verify) | owner: "approved — A, B1a, B2–B5 as recommended" |
| D-065 | 2026-09-25 | `W-045n`: how TY 2025-26 sources for the Phase 5 rules are researched and recorded | Only Q1 (`REBATE-OLD-001` main limb, 1961 s.87A), Q2 (`RATE-CESS-001` 1961-side sub-section), Q3 (`RATE-ROUND-001`, 1961 ss.288A/288B) and Q4 (D-052: the date meaning of `WITHDRAWN`/`STRUCK_DOWN`) are open; the rest are VERIFIED for 2025-26 in the rulebook. Upgrade only on a primary source quoted with URL and page; otherwise `UNVERIFIED` + `whatToCheck`. Results: rulebook errata, the plan's Findings table (for `W-052c`), STATE's unverified list. No code, timeline or lock change. Public government documents fetched with WebFetch/WebSearch | owner: "approved — C1, C2 as recommended" |
| D-064 | 2026-09-25 | `W-045m`: how the known-errors notice reaches the old compute response | A new `taxplanning/salary/dto/KnownErrorsComputation` (`@JsonUnwrapped` computation + `knownErrors`): the body keeps its shape and gains one top-level field; not a header (invisible, and `Warning` is obsolete), not a wrapper (breaks clients). The PDF report endpoint prints the same figures and is **not** warned by this item — a follow-up proposal. The notice goes with the endpoint (`09` B2, after Slice 9) | owner: "approved — A1, B1 as recommended" |
| D-063 | 2026-09-25 | OQ-3 — the old compute endpoint returns known-wrong tax: warn, disable, or leave? | **Warn now** (a visible "known errors — being replaced" notice on the response), remove it when the replacement passes its golden tests (`09-build-plan.md` B2). `W-045m` builds it | owner: "warn — as recommended" |
| D-062 | 2026-09-25 | `W-045l`: `PersonType.HUF` makes I5 (exactly one default per person type) refuse every pack, since no regime applies to an HUF | **I5 counts only the person types some applicable regime applies to** — a pack serving no HUF passes; one with a regime open to HUFs and no HUF default is refused. The rulebook sources only s.202's heading for HUFs, so no pack is widened (that would compute on unverified law). The code that later selects a regime by person type must refuse an assessee type the year's pack does not serve. `ResidentialStatus` nests in `Assessee`; `EngineInputs.taxpayer` → `assessee`; `type()` derived per record; `TaxpayerFacts`' checks and javadoc move to `Individual`/`Assessee`; fixtures folded into `NodeHarness.plainTaxpayer()` | owner: "approved — A1, B1–B5 as recommended" |
| D-061 | 2026-09-25 | `W-045k2`: how the 80C and HRA entries handle what the rulebook does not source | The 80C route list's gap is a route of its own ("Other Schedule XV items not listed here", `NotModelled` with the reason), not a new field; text names an instrument, never its tenure, cap or count — every figure waits for its rule and a placeholder; 1961 s.10 is registered `PLANNED` (→ `W-067`) now, and whether s.10 is registered by clause is `W-045o`'s; the 1961 provisions `s.80C` and `s.10(13A)` are `PRESENT` on the rulebook's 1961 columns. No `LATER_REVERSAL` is written: the rulebook sources none | owner: "approved — C1a, C2a, C3, C4 as recommended" |
| D-060 | 2026-09-25 | `W-045k`: size, and what the section catalogue checks | **Split:** `W-045k` = `catalogue/` types (`SectionCatalogueEntry`, `Route`, `Disqualifier`, `DisqualifierKind`, `EnforcedBy`), `SectionCatalogue` read + checks, `Timeline`/`TimelineReader` carry `content/sections/*.json` verbatim (no lock change), and the **one** entry whose references can resolve (the default regime, s.202 / s.115BAC); new `W-045k2` = the 80C and HRA entries + their `PLANNED` register rows. Entry fields: key, provisions (`Provision`, since HRA's 2025 home is Sch. III, not a section), plainName, summary, routes, disqualifiers — `process` to `W-117`, the rest later. References must resolve in the timeline (rule) or name a `Condition` record; otherwise `NOT_MODELLED` + reason. SC-1 full for `MODELLED`, a ratchet for `PLANNED`; SC-2 a text scan; SC-4 by refusing authored `availability`/`history` | owner: "approved — split as A, B1–B6 as recommended" |
| D-059 | 2026-09-24 | `W-045j`: the "105 sections" are not one Act's (grep gives 116, mixing 1961-Act, 2025-Act and Finance Act sections) | The register, its three checks and `Coverage` types are built now, seeded with only the sections the shipped timeline cites (2025 s.202, 1961 s.115BAC, both `MODELLED`); the rulebook's sections move to `W-045o`. A citation = a `PRESENT` provision of either Income-tax Act whose reference starts `s.<n>`; rules and regimes both cite; the register is not compiled into year packs (no lock change); `CoverageRegister.verify(Timeline)` in `coverage/`, run by `CoverageRegisterTest` | owner: "approved" |
| D-058 | 2026-09-24 | `W-045i` (STATE Q1): where node-id constants live | **Generated** `NodeIds` from the timeline's node catalogue, beside `RuleIds`; mechanical names (`TOTALINCOME`, `TAX_BYBASIS`); every regime `nodeRef` must have one; no catalogue type until something reads `produces`; only the two named tests switch | owner: "approved, go with your recommendations throughout" |
| D-057 | 2026-09-24 | `W-045h`: shape and scope of `PackDiff` | `PackDiff(from, to, findings)` per `DR 15` C4, in `timeline/` (not `validate/` — cycle); diffs compiled canonical JSON; findings `RuleAdded`/`RuleRemoved`/`VersionAdded`/`VersionRemoved`/`ValueChanged` (a version keyed by its start date); `Magnitude` and the three lint findings are `W-139`'s; regimes not diffed yet — recorded on `W-139` | owner: "approved, go with your recommendations throughout" |
| D-056 | 2026-09-24 | `W-045g3`: what replaces the per-year copy for its last readers | `PackJsonTest` parses the **compiled** 2026-27 files (the loader's real input), mutation regexes rewritten for canonical JSON; the timeline==copy and compiled==copy tests are retired (proof stands in `W-045g`, `4e11ab9`); `ComponentCodes` is generated from `timeline/exec/components/` | owner: "approved, go with your recommendations throughout" |
| D-055 | 2026-09-24 | `W-045g2` is again two items; how the provider and test roots move | **Split:** `W-045g2` = provider compiles from timeline + lock, six test roots migrated, a real wrong-year test; new `W-045g3` = remove `rulepacks/2026-27/` and move `RuleIdNamingTest`, `ConstantGenerator`, `TimelineReaderTest`, `RulePackCompilerTest` equality, `PackJsonTest`, `PackJsonWellFormedTest` off it. `ClasspathRulePackProvider` moves to `timeline` (no package cycle); a timeline inside a JAR is refused naming the UNOWNED gap; one broken root per defect; the fixture gets one synthetic node | owner: "approved, go with your recommendations throughout" |
| D-054 | 2026-09-24 | `W-045g` is oversized; how a year is compiled from the timeline | **Split:** `W-045g` = compiler + canonical form + lock + proof of equality with today's 2026-27 pack; new `W-045g2` = provider serves compiled packs, test roots migrated, `rulepacks/2026-27/` removed, naming test/generator/drift guard moved to the timeline. Canonical form: sorted keys, no whitespace, `_` note fields dropped. A year holds PUBLISHED versions overlapping it plus PUBLISHED pending versions; regimes in force in it. Lock: per-layer and per-rule checksums. **Startup wiring + loading the timeline from the JAR: UNOWNED** — no item owns them; owner to name one | owner: "approved, go with your recommendations throughout" (B4 not covered — no recommendation was made) |
| D-053 | 2026-09-24 | `W-045f`: four questions on the timeline | Reader takes a filesystem `Path`; loading from a JAR at runtime is `W-045g`'s; the exec node catalogue is one file per node, carried verbatim until `W-045i` types it; the reader knows only the directories it can read (others arrive with their first file); `TimelineReaderTest` asserts the timeline equals the 2026-27 pack while both exist | owner: "approved, go with your recommendations throughout" |
| D-052 | 2026-09-24 | `W-045e`: five questions on the three status axes | Pending notification stated by both `Validity.PendingNotification` and `LegalStatus.PENDING_NOTIFICATION`, required to agree; a non-`PUBLISHED` version in a pack is **refused**, not filtered (filtering is `W-045g`); legal status does not yet gate `versionAt` — the date meaning of `WITHDRAWN`/`STRUCK_DOWN` is to be sourced by `W-045n`; `SUPERSEDED` = a transcription replaced by a correction, not an older law; no split | owner: "approved, go with your recommendations throughout" |
| D-051 | 2026-09-24 | OQ-4: concept names for rule IDs and regime codes. The 2025 Act itself heads Part C and s.202 "New tax regime" (S2 p.224, p.226); the opted-out regime has no name in either Act | Default regime `NEW_TAX_REGIME`; opted-out `FIRST_SCHEDULE_RATES` (where its rates are — FA 2026 First Schedule Part I-B Para A); slab rules `RATE-SLAB-NEW-TAX-REGIME`, `RATE-SLAB-FIRST-SCHEDULE` (+ `-AGE-60TO79`, `-AGE-80` in the mapping); "NEW" allowed only in the statutory phrase; **OQ-4 closed**. `W-045d` split: new `W-045d2` maps the other 51 rulebook IDs + `S202_4_B`. Brief and PRD amended in place | owner: "approved, go with your recommendations throughout" |
| D-050 | 2026-09-23 | `W-045c` findings: four row corrections and a dead-code deletion item | **All accepted** ("approved all of these"): `W-046` authors age-band rows; `W-048` adds `ReliefResult`/`ReliefContext`; `W-052` requires only `tax.cess` in Phase 5; `W-056` modifies `SlabLadder`; new item **`W-045c1`** deletes legacy dead code. L2 (the legacy compute path) still waits on OQ-3 | owner |
| D-049 | 2026-09-23 | OQ-5 — `taxengine_does_not_swallow_failures` forbids defining exceptions, not only catching them | **Delete it.** Checkstyle `IllegalCatch` (repo-wide since `W-045a`) enforces the intent, proven on `taxengine` main code by a probe. Item `W-045b3` | owner: "sure" |
| D-048 | 2026-09-23 | Are four tax modules necessary? (A module here is a Spring Modulith package boundary inside one JAR — not a service) | **Two modules** — `taxengine` (rules vocabulary + computation, pure, closed) and `taxplanning` (all I/O: persistence, API, documents, jobs, legacy). **Supersedes D-002.** Evidence: B-001, B-002, B-003 and D-027 were caused by the module boundaries, not tax logic; `taxrules/package-info.java:5` claims "this module computes nothing" while `SlabLadder.apply`/`LeastOf.apply` compute; purity is enforced by package-based ArchUnit rules either way; `taxdocs` is empty. Item `W-045b2`. Found while writing it: a closed `taxengine` hides its sub-packages from `taxplanning`, so `W-103` would have repeated B-002 under either design — the item's plan must settle exposure | owner: "agree" |
| D-047 | 2026-09-23 | Applying review C1–C4 found a fifth mistake of mine (C5): the brief's examples `RATE-SLAB-S202-1` and `S202_DEFAULT` put a section number in the identity, contradicting D-035 and D-046 B4 | **Applied C1–C5** to `00-BRIEF.md` and Phase 4b: timeline by layer; "rule pack" / `RulePackCompiler` / `PackDiff` / `<taxYear>.lock.json` ("snapshot" already names input projections); `coverage.json` in `content/`; tax-year-length axis; section-numbered examples removed. **Concept names are not invented** — `W-045d` proposes them quoted from the Acts, the owner chooses (**OQ-4**). Brief gains a Changes section; Phase 4b rows edited in place (no item had started), prior text in git `72c089b` | owner: "sure" |
| D-046 | 2026-09-23 | PRD + tech-spec review (`reviews/2026-09-23-prd-techspec-review.md`), B1–B6 | Owner: *"agreeing with you"* — all six recommendations accepted. **B1** rewrite `01-PRD.md` and `02-TECH-SPEC.md` as the current truth (history → changelog section + git); the 25 design docs keep errata. **B2** one account owns several assessee profiles (`tax_profiles`: unique `{pan}`, index `{email}`). **B3** API follows the repo convention with a distinct resource, `/tax/user/{email}/…`, until the legacy `/tax-planning` is deleted. **B4** regime codes name the concept, provisions per Act as data (as D-035). **B5** `tax_employers` scoped per account: unique `{profile_id, tan}`. **B6** refusals are 400 / `USER`: `UNSUPPORTED_ASSESSEE_TYPE`, `UNSUPPORTED_CASE`. Also accepted: fixes A1–A7 and my corrections C1–C4 | **decided by the developer** |
| D-045 | 2026-09-23 | The owner wants engineering principles applied strictly to every line of code, in any module — naming, no hallucination, no slop — configured, not remembered | **Two layers.** (1) Written: an "Engineering standards" section in `CLAUDE.md` (truth, scope, naming, design, tests), loaded by every session. (2) Enforced: item **`W-045a`** — strict Checkstyle failing the build with a shrink-only legacy baseline, ArchUnit naming rules, `spotless:check` on `validate`, and a commit-blocking Claude Code hook. Measured first: 96 main-code violations (7 in the new tax modules), 24 real test-name violations after matching the documented convention, 10 generic suffixes, 2 duplicate class names | **decided by the developer** |
| D-044 | 2026-09-23 | Sessions may end at any moment (rate limits). The only resume point was the item commit plus an 800-line STATE.md, and an approved plan existed only in the chat | **`CHECKPOINT.md`** (~20 lines, overwritten, updated at every stage change and plan step, before reporting); **`plans/W-0nn.md`** holding the plan, the owner's approval and a ticked step list; **no WIP commits** — the item stays one commit; **P1-quick** resume prompt. `00-START-HERE.md` §5b | **decided by the developer** |
| D-043 | 2026-09-23 | The brief changes the rule format and the input model; the owner asked to "collect every prerequisite and keep them properly" | **Phase 4b** in `03-WORK-ITEMS.md` — 14 items (`W-045b`…`W-045o`) before `W-046`, in dependency order; `W-052b` and `W-052c` before `W-053`; `W-053` amended (both years, I3 switched on as a shrink-only ratchet); **Appendix F** for what is owed later with where each lands; four **standing rules** for every later item (coverage, catalogue, both years, concept IDs). Answers STATE Q1 (`W-045i`), Q2 (`W-053`), Q3 (`W-052b`) | **decided by the developer** |
| D-042 | 2026-09-23 | People need every section explained — what it is, the ways to qualify, the process, what disqualifies — not only the engine's rules | **The section catalogue** (`00-BRIEF.md` §6.3): one entry per section across both Acts, in `content/`; routes with their own conditions; disqualifiers typed by when they bite (`NOT_ELIGIBLE`, `CONDITION_FAILED`, `PROCESS_MISSED`, `THIRD_PARTY_MISSING`, `LATER_REVERSAL`), each linked to the rule or `Condition` that enforces it; numbers only as placeholders rendered from rules; extends `DR 22`'s per-rule claim catalogue rather than duplicating it. Enables feature **F41**, a section explorer annotated from the person's own computation | **decided by the developer** |
| D-041 | 2026-09-23 | OQ-4 (old) — build TY 2025-26, or 2026-27 only (D-001)? | **Both years**, for consistency with `portfolio`. **Supersedes D-001.** 2025-26 is added as versions on the timeline, verified per phase where it is used (`W-045n`, `W-052c`, standing rule 3) | **decided by the developer** |
| D-040 | 2026-09-23 | Naming: commit subjects since `W-013` stopped naming what changed; bare cross-document IDs collide (`T1`, `B2`, `F4`, `D-17` vs `D-017`); rule IDs embed the year and "old/new" | **Standards in `00-BRIEF.md` §10**: item commits `taxplanning W-0nn: <verb> <types> — <why>`; cross-document references prefixed by document (`TS-T1`, `GT-T1`, `DR15-B2`); rule IDs name the concept | **decided by the developer** |
| D-039 | 2026-09-23 | `TaxpayerFacts` is an individual's facts with `Set<PersonType>`, and `PersonType` has one value. HUF is the named second type | **A sealed `Assessee` family** — `Individual`, `Huf`, later others — each with only its own facts; one type per assessee. Replaces `TaxpayerFacts` before any node reads it (`00-BRIEF.md` §8). Per-type rule differences are sourced in the rulebook before code reads them | **decided by the developer** |
| D-038 | 2026-09-23 | "Think of all cases" — ~100 sections, 30–40 in the new regime — how is completeness known? | **A section coverage register** (`law/coverage.json`) classifying every section of both Acts as `MODELLED` / `PLANNED` / `NOT_COMPUTATIONAL` / `OUT_OF_SCOPE`; CI fails on an unclassified section. Seeded from the 105 sections `01-rulebook.md` cites. Plus the case-axis matrix (`00-BRIEF.md` §7) | **decided by the developer** |
| D-037 | 2026-09-23 | Rules must be updatable over time, with managers for new kinds of rule. Runtime-editable or release-only? | **Configurable and maintainable through a publish gate** — validate, diff, golden suite, reviewer approval — before a version is `PUBLISHED`. Authored as repository files first; an admin console later behind the `RulePackProvider` SPI running the **same** gate. A new kind of rule is a new **manager** (existing construct names kept in code); 28 managers catalogued in `00-BRIEF.md` §6.2 | **decided by the developer** |
| D-036 | 2026-09-23 | One `status` field carries legal status, verification and publication at once | **Three axes**: legal (`BILL`…`IN_FORCE`…`STRUCK_DOWN`), verification (`UNVERIFIED` / `SOURCE_CHECKED` / `PROFESSIONALLY_REVIEWED`), publication (`DRAFT` / `IN_REVIEW` / `PUBLISHED` / `SUPERSEDED`). Only `PUBLISHED` enters a snapshot | **decided by the developer** |
| D-035 | 2026-09-23 | The shipped pack names rules `RATE-SLAB-NEW-2026` / `RATE-SLAB-OLD-2026` — the year and "old/new" in the identity, defeating versioning and the glossary | **Rule IDs name the concept**; the year lives in the version, the section in the provision. Rename the 15 existing rows (and `RuleIds`) before more are written | **decided by the developer** |
| D-034 | 2026-09-23 | A regime spans many years, but a new year is `cp -r` of the whole pack (`07` X4.1) — N copies of one regime, free to drift | **One timeline, frozen per year**: each rule, regime and form authored once with its versions; each year's pack is **compiled** from the timeline into an immutable, checksummed snapshot that computations pin | **decided by the developer** |
| D-033 | 2026-09-23 | Who is this for? The original brief is empty in git; PRD scope says individual, salaried, resident, no business income | **Any assessee and every ITR form — designed for all, built for individuals first, HUF second.** Written down as `docs/taxplanning-design/00-BRIEF.md`, which supersedes older scope statements; amendments added to `01-PRD.md` §2/§7 and `00-START-HERE.md`. Also agreed: forms are eligibility + shared schedules (not subclasses), and forms are an axis independent of regimes | **decided by the developer** |
| D-032 | 2026-09-21 | `W-043` — three of `FilingPosition`'s six components name types that do not exist (`ItrSelection` and `CalendarEntry` are in A6 and **nowhere else**; `InterestBreakdown` is `W-112`'s). And `ComputationReceipt` is listed in the `Produces` of **both** `W-043` and `W-107` | **Ship `FilingPosition` with the three components that exist**, the rest arriving with their owners (the D-026 shape). Unlike `EnteredFacts` there is no fingerprint pressure, so this is a plain "grow it later"; `asOf` alone carries A6's point. **`W-043` builds `ComputationReceipt`** (it needs only types available there, and splitting it from `ComputationId` would strand that type for 64 items); `W-107` keeps E6's API-side resolution. Two errata | **decided by the developer** |
| D-031 | 2026-09-21 | B-004 — `W-039`'s `NodeRegistry` rejects an empty `requires()`, `W-040`'s `TopologicalSort` rejects a required slot with no producer. Every acyclic graph needs a source, so **no graph satisfied both**. Both were committed and green; `W-042` is the first item to pass a graph through both | **(A) a node may require nothing.** A source reads `EngineInputs` via `ctx.inputs()` — declared, passed in, fingerprinted — so the original premise ("everything the engine reads arrives through a slot") was wrong: there are two sources of data and only one is an edge. The check given up is redundant with `NodeContext.get`, which refuses an undeclared read at the moment it happens and is the stronger guarantee. Erratum on `W-039`'s Verify | **decided by the developer** |
| D-030 | 2026-09-21 | `W-040` — T9's error catalogue has **no** entry for a pipeline cycle and `TaxError`/`TaxEngineException` do not exist (`W-105`). And a graph admits many valid topological orders; the Verify does not say which | **`IllegalArgumentException`, naming the cycle** — it matches what `NodeRegistry` already throws, and a cycle is a defect in what node *classes* declare, not in pack data or user data (B3's "fails at load" is inconsistent with its own code, which sorts per run). `W-105` may reclassify it into the sealed hierarchy. **The order is canonical**, ties broken on `NodeId`: `TraceLine.sequence` comes from it, the trace *is* the result, so a varying order would make identical inputs produce differing traces — golden tests failing for no reason, scenario diffs reporting changes nobody made | **decided by the developer** |
| D-029 | 2026-09-21 | `W-039` — B2 promises `NodeContext.get` throws on an undeclared read, but B3 builds **one** context for the whole run and passes it to every node, so it cannot know who is asking and cannot make the check. Separately, I3 needs node-to-rule mappings and there are **zero** `ComputationNode` implementations until Phases 5-9 | **Per-node context**: `forRun(...)` once, `ctx.forNode(node)` per node over a shared value store; scoping applies to `rule(RuleId)` too. **I3 (a)**: ship the checker, prove it against fixtures, do **not** wire it to the shipped pack yet — it would report every rule unreachable; the item that first ships a real pipeline enables it. **I18**: already enforced by `TemporalContext.on` / `RuleRow.versionAt`, asserted as a property (the I1 shape), no checker added | **decided by the developer** |
| D-028 | 2026-09-21 | `W-038` — F6 writes the fingerprint as a six-argument hand-written list; A3 says it is "a hash of `EngineInputs` — one object". F6 also lists a `RulePackRef`. Which, and does the pack go in? | **`of(EngineInputs)`, one argument, walked structurally by reflection**; F6's list is wrong four ways and is erratum'd. **The pack stays out** — `RulePack.computationCacheKey()` (`W-027`) already covers it readably and excludes editorial layers, so folding it into an opaque digest would undo G-11; the full key is the two composed. `List` order is significant, `Set`/`Map` order is not: sorting a list could make two different inputs collide (a wrong cache hit), while preserving order can only cause a needless miss | **decided by the developer** |
| D-027 | 2026-09-21 | `W-037c` — L5's `adjustPipeline(PipelineSpec, Inputs)` needs `taxrules` → `taxengine`, and `taxengine` → `taxrules` already exists, so it is a **cycle**, not merely a disallowed edge. Modulith rejects cycles outright (established by B-001 option C) | **(A) ship `adjustPipeline(PipelineSpec declared)` — no inputs parameter.** Follows the precedent `W-031b` set on this same interface: hooks arrive with the items that build their types. L5 recommends shipping **zero** behaviours beyond the default, so the parameter is speculative until a real tier-3 behaviour says what it needs; adding it then is one line on an interface with one implementation. Guard 3 lists overriders and is unaffected by the signature. Also: guard 3 is a plain `@Test`, not an `@ArchTest` — expected overriders is zero and an ArchUnit rule matching nothing fails the build | **decided by the developer** |
| D-026 | 2026-09-21 | `W-037b` — a faithful `EnteredFacts` needs `Employment`, `HouseProperty`, `OtherSourceItem`, `DeclaredClaim`, `ProofOfClaim` and `EntryProvenance`, which have **zero mentions in the backlog** and exist only in `11-filing-drafts.md` F4's code block. `Employment` alone is R-11's per-period machinery. Seam, spec them now, or drop the component? | **(a) a declared seam that grows with the income items** (rule B2). It does not reopen B-003: that hazard was a component *absent* from `EngineInputs` and so invisible to the cache key; a declared-but-thin one is in the key, and A3 requires `W-038` to derive the fingerprint structurally. Today it has **no components**, which is the honest statement — nothing on the income side is built. Also: **dated advance-tax instalments, not a total** (`ADV-MAR31-001` and s.425 both read the payment date — the same lossiness as a derived age), and `RateBasisHint` moves from `W-085` to `taxrules.component` | **decided by the developer** |
| D-025 | 2026-09-21 | `W-037` under D-024 is 8 records + an enum + a defensive-copy test across all of them — 900-1,100 lines against §7's ~300, before L5 guard 3. And `EngineInputs` cannot compile until its ten components exist, so the spine cannot come first. Separately: does the projection carry date of birth or a derived age? | **Split three ways** — `W-037` (`TaxpayerFacts`, `FamilySnapshot`, `LedgerSnapshot`), `W-037b` (`FilingFacts`, `RegimeElectionSnapshot`, `ElectionState`, `EnteredFacts`, `CapitalGainsInput`), `W-037c` (`EngineInputs` + L5 guard 3). Ids suffixed, not renumbered. **Date of birth, not an age** — F2's senior test is "at any time during the previous year", which an age cannot answer; the collapse to `ConditionContext`'s `int` happens downstream where that rule is applied. Also accepted: no PAN in the engine, no `fingerprint()` until `W-038`, `ElectionState` moves from `W-082` to `W-037b` | **decided by the developer** |
| D-024 | 2026-09-21 | B-003 — `DR 23` A3's `EngineInputs` holds `DraftInputs` (`taxplanning`) and `CapitalGainsSummary` (`portfolio`), neither of which `taxengine` may depend on. A3 proposes, three blocks later, the very ArchUnit rule its record breaks. Projections, widened dependencies, or a partial record? | **(A) `taxengine` owns its input projections**; `taxplanning` maps `DraftInputs` and `portfolio`'s read-model into them at assembly. Faithful to A3's javadoc, keeps D-PURE enforceable, and keeps `EngineInputs` complete — which is what `W-038`'s fingerprint depends on. (B) would delete D-PURE; (C) would put the cache key on a knowingly incomplete input for ~48 items. Debts recorded against `W-082`/`W-085`/`W-099` | **decided by the developer** |
| D-023 | 2026-09-21 | `W-036` — `TraceAnnotation` (DR 23 A8) names `ExplanationRef`, `ClaimCatalogueRef` and `PricedAlternative`, which the design declares nowhere and no item produces. Ship a reduced record, or declare the three types now? | **(i) ship `TraceAnnotation(int sequence, List<Notice> notices)`**; the other three components arrive with the items owning their types (rule B2). Needs nothing outside `Produces`; declaring them now would mean inventing `PricedAlternative`'s money fields (R1/R5). Also: **omit M5.1's `label`** from `TraceLine`, taking A8's omission as deliberate — pack text is resolved from `rule()` at render time, so re-wording must not invalidate a cached computation. Two errata on A8 | **decided by the developer** |
| D-022 | 2026-09-21 | B-002 — T1 declares all four tax modules `CLOSED` *and* has them depend on each other's types, but every shared type lives in a sub-package and a `CLOSED` module exposes only its root. T1 as written is unsatisfiable. `@NamedInterface` on ~12 packages, or `taxrules` `OPEN`? | **(B) `taxrules` is `Type.OPEN`**; `taxengine`, `taxdocs`, `taxplanning` stay `CLOSED`. It is the shared vocabulary and has no internals to hide; the three modules that do have internals keep the boundary, so T1's actual objective is untouched. (A) declares the same visible surface twelve times over. Erratum on TS T1 | **decided by the developer** |
| D-021 | 2026-09-19 | Appendix A matched types the design **declares**, so it was blind to types named only in a `permits` clause — the category that *fails to compile*. `W-035` hit it: `NodeValue` permits six, five declared nowhere | **Appendix B** — re-audited: 29 types in `permits` clauses, 19 never declared, 14 with no owner, now tabled. **Rule B2**: a `permits` clause lists only what exists. Flagged `UndeterminedApportionmentException` (needed by `W-066b`, reached before its owner `W-105`) and `MissingRulePackException` (T9-1, currently a plain `PackValidationException`) | **approved by the developer** |
| D-020 | 2026-09-19 | `W-034` found E4's property broken: a pack rejected by a **domain constructor** (blank `whatToCheck`, a contradictory `AmendmentRef`, an empty `OnlyIn`) surfaces as `IllegalArgumentException` with no path, not `PackValidationException`. That is the very thing hand-written parsing was chosen for | **`W-034b`** — wrap domain construction in the readers and rethrow with the path. Its own item because the fix touches `W-033`'s `Produces` | **approved by the developer** |
| D-019 | 2026-09-19 | `W-033` (the pack deserialiser) is ~55 shapes and ~700 lines — 4x the §7 limit. Its stated `Verify` round-trips a fixture whose arrays are empty | **Split three ways** — `W-033` (leaves, `Validity`, `RegimeApplicability`, `CapShape`), `W-033b` (`Condition`, `RuleRow`), `W-033c` (regimes, sets, components, the assembler). **Hand-written parsing**, not Jackson annotations: E4 requires the loader to throw only `PackValidationException`, and Jackson's errors address a Java developer rather than a pack author | **approved by the developer** |
| D-018 | 2026-09-19 | Appendix A's three ownerless types: nothing applies a mid-year change; invariant I9 is unenforceable without a behaviour registry; the `ComponentCodes`/`RuleIds` generator is a missing build step | **Three new items.** `W-031b` — `RegimeBehaviour` + registry + I9. `W-032b` — the Maven generator emitting `RuleIds`/`ComponentCodes` from the packs. `W-066b` — `TemporallyApportionedCap`, placed after `W-066` because `PRORATE_PER_SEGMENT` needs `ProrationBasis`. `RulePackDocument` stays unresolved | **approved by the developer** |
| D-017 | 2026-09-19 | Phases 0–3 hit a missing-type stop at **every** boundary. Rather than continue item by item, audit the rest in one pass | **Appendix A** in `03-WORK-ITEMS.md`: all 157 design-declared types matched against every `Produces`. 21 were nested variants, 3 withdrawn, **62 genuinely unassigned** — now tabled against their owning item. Three have **no owner**: `TemporallyApportionedCap` (nothing applies a mid-year change), the `RegimeBehaviour` registry (invariant I9 is unenforceable without it), and the `ComponentCodes`/`RuleIds` build-time generator | **approved by the developer** |
| D-016 | 2026-09-19 | `W-030` was assigned 12 invariants; **8 constrain types Phase 3 does not build** (ITR forms, the claim catalogue, the node registry, a working engine). I10 and I16 were already enforced | **Ship I13, I14, I15 here** and redistribute the rest to the items that build their subjects — I18→W-039, I19/I20→W-090, I21/I22→W-092, I23→W-117, I27→W-146, each with a test. Add `MidYearCombination` (V5, produced by no item) so I14 is checkable | **approved by the developer** |
| D-015 | 2026-09-19 | `W-026` at ~600 lines was twice the §7 limit, and six types it needs were produced by no item. Separately, `17-regime-lifecycle.md`'s pack JSON names the head `"PGBP"` where the code says `BUSINESS_OR_PROFESSION` | **Split three ways** — `W-026` (lifecycle, L2), `W-026b` (election, L3), `W-026c` (`RegimeSpec`, which binds them). Head name: **`BUSINESS_OR_PROFESSION`** wins, being already in code and in the rulebook's prose; the JSON is erratum'd | **approved by the developer** |
| D-014 | 2026-09-19 | `W-023` could not compile (`RuleVersion` needs `RegimeApplicability`, assigned to the *later* `W-024`); six supporting types produced by no item; and **11 items verified with `compiles` alone**, which passes for a record whose lookup method returns the wrong version | **Merge `W-023`+`W-024`** (`W-024` RETIRED) and **sweep every `compiles`-only row** — each now names a real test and what it must prove. Rule B1 amended: *the test is not the compiler* | **approved by the developer** |
| D-013 | 2026-09-19 | `W-021` (`Condition` + 16 records + test) estimated at ~750 lines, 2.5x the §7 limit; five supporting types produced by no item; `AgeAtLeast` cannot express its own pack example | **Split three ways** — `W-021` (person facts), `W-021b` (amounts and structure), `W-021c` (composition, closes the family). Ids suffixed, not renumbered. `AgeAtLeast` gains an `AgeSubject`. The five supporting types are assigned across the three | **approved by the developer** |
| D-012 | 2026-09-19 | `taxrules` cannot see `Money`, which T2 places in `taxengine.money`, but T1 draws the arrow `taxengine -> taxrules`. Where does `Money` live? | **`taxrules.money`** — option (A) of B-001. A rule pack holds rupee figures by its nature, so `taxrules` needs the type; `taxengine` already allows `taxrules`, so T1's arrow is unchanged. Moved `Money`, `Rate`, `MoneyTest`, `RateTest`; renamed `taxengine_uses_money_not_bigdecimal` to `tax_modules_use_money_not_bigdecimal` and widened it to all three tax modules; errata in TS T3/T10, PRD, WORK-ITEMS W-002/W-004/W-005, DR 19 E1 | **approved by the developer** |
| D-011 | 2026-09-19 | The backlog splits implementation from its test: an item's `Verify` names a test that a **later** item produces. Three pairs occurred in Phase 1 alone. Fix or work around? | **Fix — restructure `03-WORK-ITEMS.md`.** Every item now carries its own test (**rule B1**). Merged `W-014→013`, `W-016→015`, `W-020→013/015/017/018/019`, `W-022→021`, `W-041→040`, `W-045→042`, `W-047→046`; Phase 3's six shared users of `RulePackLoadTest` each got their own test; `W-044` moved before `W-042`. **Ids are not renumbered** — `W-001`…`W-012` are referenced by commit subjects — so seven ids are marked RETIRED. 146 ids, 140 live. This supersedes D-009's errata-only restriction for this one change | **approved by the developer** |
| D-010 | 2026-09-19 | How are `Money` values asserted, given `MoneyAssert` is `double`-based? | **AssertJ `isEqualTo` on `Money` directly.** Record equality compares scale as well as value, so it catches a scale slip too. `MoneyAssert` exists to stop bare `assertEquals(double, double)`; that hazard does not exist for an exact type, and routing through it would discard the exactness and assert with a tolerance | **decided by the developer** |
| D-009 | 2026-09-19 | May `03-WORK-ITEMS.md` carry errata, as the design now can? | **Yes**, on the same terms — append only, proof required, original never edited | **decided by the developer** |
| D-008 | 2026-09-19 | May the design be corrected when building proves it wrong? | **Yes — errata only.** Append a marked block below the wrong text; never edit or delete the original; proof required. R7 relaxed, `00-START-HERE.md` §10 | **decided by the developer** |
| D-007 | 2026-09-19 | Q7 — `SAL-ALW-NEW-SUBSET-001` per section or per r.280(3)? | **Per the section**, flagged visibly. A rule cannot override its parent section | provisional — on recommendation |

⚠ **D-003 is the only one that changes what `W-001` writes.** D-006 must be answered before `W-032`
(the first rule pack).

---

## Rules used that are not fully verified

<!-- Append whenever a completed item relies on a NEEDS VERIFICATION or STATUS UNCLEAR rule.
     Format:  <RuleId> | <status> | <item that used it> | <what to check> -->

`RATE-ROUND-001` (TY 2025-26, 1961 ss.288A/288B, ₹10) | UNVERIFIED | `W-045n` → for `W-050`/`W-052c` | read ss.288A and 288B base text as in force for AY 2026-27 — India Code `a1961-43.pdf` (504 from here) or the Income Tax Department's section pages (403 from here)
`REBATE-OLD-001` (TY 2025-26) | figures SOURCE_CHECKED, wording not | `W-045n` → for `W-049`/`W-052c` | read the 1961 s.87A main limb in the base text ("resident", "individual", "whichever is less"); search non-Finance amending Acts for s.87A
D-052 (`LegalStatus.WITHDRAWN`/`STRUCK_DOWN` and dates) | UNVERIFIED | `W-045n` | read General Clauses Act 1897 s.6 (thc.nic.in copy is a scan); put `STRUCK_DOWN`'s date meaning to the reviewer named in OQ-1 — until then legal status does not change `RuleRow.versionAt`

---

## ▶ How to resume — do these in order

**Session 4 changed direction, not code.** The owner stated the founding brief; it is written at
`docs/taxplanning-design/00-BRIEF.md` and **must be read first**. No Java changed in session 4; the
build is green and the last item is still `W-043`.

1. **Confirm where you are.**
   ```
   git log --oneline --grep='^taxplanning W-' -1     # expect: taxplanning W-043: the half that never moves
   git status --short                                # expect: clean
   ./mvnw test -pl backend -Punit                    # expect: 1410 passing, 0 errors, ~9 s
   ```
   If the first command does not print `W-043`, stop and report `git log --oneline -15`.

2. **Read `00-BRIEF.md`**, then `03-WORK-ITEMS.md` Phase 4b and Appendix F.

3. **Start `W-045b`** at stage 1 (READ). Phase 4b runs `W-045b` → `W-045o` in order, then `W-046`.
   `W-045m` waits on the owner's answer to **OQ-3**; if it is not yet answered when reached, ask.

---

### ⚠ Three things Phase 5 forces, which Phase 4 could defer

**Q1 — `NodeId` anchors.** ✅ **Answered by `W-045i` (D-058):** `NodeIds` is generated from the timeline's node catalogue, beside `RuleIds`; every regime `nodeRef` must have a constant. *(Original question kept below.)*

`W-046` must name the slot it produces and the slot it reads. There is no `NodeIds` class.
`DR 15` B2 declares thirteen well-known anchors as constants on `NodeId` (`GROSS_SALARY`, `GTI`,
`TOTAL_INCOME`, `TAX_BY_BASIS`, `NET_PAYABLE`, …) — but `NodeId` is `W-008`'s, `RuleIds` and
`ComponentCodes` are `W-032b`'s **generated** pair, and B2's own comment says *"the full set is
declared in the pack, not here; these are the well-known anchors"*. Three candidate homes, no
decision. `NodeHarnessTest` and `PipelineRunnerTest` already hard-code `new NodeId("totalincome")`
and friends, and **every Phase 5 node will copy whatever `W-046` does**. Decide before `W-046`, not
after.

**Q2 — invariant I3 is built and switched off, and Phase 5 is its trigger.**
`NodeRegistry.rulesNotEvaluatedByAnyNode` works and is fixture-tested, but it is wired to nothing:
with zero `ComputationNode` implementations it would report every rule in the shipped pack
unreachable. **D-029 says the item that first ships a real pipeline enables it.** That is Phase 5.
If nobody does it, the dead-rule check that `O-19` exists for never runs, and nothing will fail to
remind anyone.

**Q3 — nothing owns the projection from `PipelineRunner.Result` to `TaxComputation`.**
`W-042` produces the `Result`; `W-043` produces the target types; **no backlog item joins them.**
Something must say *which slot is `totalIncome`* and *which is `taxByBasis`* — which is Q1 again,
one level up. Likely needs a new item in Phase 5, probably after `W-046`.

---

### Owner questions open since session 4 (`00-BRIEF.md` §12)

| | Question | Recommendation | Blocks |
|---|---|---|---|
| **OQ-1** | Who checks a changed rule before it goes live? (= **D-006**) | the owner approves each change now; a CA reviews a sample before outside users rely on it | nothing before the first post-launch rule change |
| **OQ-2** | When should a web screen for editing rules arrive? | after `W-111` | nothing now |
| ~~**OQ-3**~~ | ~~The old compute endpoint returns known-wrong tax — warn, disable, or leave?~~ | **Decided (D-063): warn** | — |
| ~~OQ-4~~ | ~~Concept names for rule IDs and regime codes~~ | **Decided (D-051)** — `NEW_TAX_REGIME`, `FIRST_SCHEDULE_RATES` | — |

Session-3 questions Q1–Q3 below are **answered by D-043** (`W-045i`, `W-053` amendment, `W-052b`).

### Also open, blocking nothing today

| | |
|---|---|
| **D-006** | Who signs off a rule-pack change. Needed *before* `W-032`; `W-032` shipped the real 2026-27 pack anyway, so this is **overdue**, not pending |
| **Duplicate-`Produces` sweep** | `W-043` found `ComputationReceipt` owned by **two** items. No audit checks for this. One pass over the backlog table closes it for good |
| **Three `EngineInputs` fixtures** | `NodeHarness.plainTaxpayer()` is the intended home. `NodeRegistryTest` and `InputsFingerprintTest` predate it and keep private copies (left alone under R5). Fold in whenever something next edits either |
| **RNOR** | `TaxpayerFacts` carries three residence states; the built `ConditionContext` has `boolean resident`. RNOR maps cleanly to neither. Lands on whoever assembles the first `ConditionContext` |
| **`ElectionState` richness** | `WITHDRAWN` vs `PERMANENTLY_INELIGIBLE` is readable but nothing sets it; the protocol that decides is `W-082`'s |

## Notes for resume

**Proposal (W-045m, D-064 B1), not scheduled:** `GET /tax-planning/user/{email}/profile/{profileId}/document/tax-report` prints the same known-wrong figures without a notice; warning there needs the PDF template changed. Owner to decide whether it gets an item.

**Rule-authoring notes from the W-045d2 source reading (owner M2) — for the items that author these rules:** (1) Rules 2026 r.280(3) restricts allowance exemptions for an employee *"who has exercised an option under section 202(4)"* — the **opt-out** option — while the rulebook's `SAL-ALW-NEW-SUBSET-001` (now `SAL-ALW-OPTION-SUBSET`) reads it as the new regime: a legal-reading question for the OQ-1 reviewer, before `W-069`; (2) `SAL-ALW-ARMED-2026` (now `SAL-ALW-ARMED-FORCES`) groups r.280(2) Table Sl. 12, the underground-mines allowance, which is not an armed-forces allowance (`W-069`); (3) the other-benefit perquisites are r.15 **Table IV**, not Table III as the rulebook cites (`W-071`/`W-072`); (4) the owned-accommodation percentages the rulebook could not find are in r.15 Table I (2)(a) (`W-071`); (5) `REG-NEW-DISALLOW-002`, `HP-NEW-002` and `SETOFF-NEW-001` rest on the same s.202(2)(b)/(3) — kept 1:1, may be merged when `W-081` authors set-off.

**Unsourced 1961-Act entries (W-045o2, D-067, owner R1):** omissions of ss.76, 77, 186 (absent from the 2019 arrangement, omitting Act not found) and s.269S (cited to the J&K Reorganisation (Adaptation of Central Laws) Order, 2020 — not read); s.245-OA in force though its inserting Act was not found (FA 2021 p.52 refers to it as existing). The chain did not read adaptation Orders or the Jan Vishwas Act, 2023 (owner R2: accepted as the best reconstruction).

**Backlog gaps (W-045o, D-066) — 2025-Act sections in the first scope with no backlog item, classified `DEFERRED`/`BUILT_FIRST` for the owner to itemise:** s.157 (arrears relief), s.266 (self-assessment tax), s.392 (salary TDS) and s.396 (tax deducted is income received) — the credits `CreditsNode` needs, which has no item id; ss.403–410 (advance tax, incl. s.410 credit for tax paid); ss.423–426 (interest); s.428 (late-return fee); s.437 (interest on refunds). **1961-Act counterparts (W-045o2, owner U3), for the same items:** s.89 (arrears relief), s.140A (self-assessment tax), ss.192, 198, 199 (salary TDS, TDS as income, credit), ss.207–219 (advance tax), ss.234A–234D (interest), s.234F (late-return fee), s.244A (interest on refunds).

**Carry forward (W-045l, D-062):** I19 ("at least one ITR form per `(PersonType, ReturnKind)`", `W-090`) meets the same `HUF` problem I5 did — state it per person type the pack serves. Regime selection by person type (not yet built) must refuse an assessee type the year's pack does not serve.

**Errata written in session 6:** `01-rulebook.md` R3 — the W-045d2 mapping of 52 IDs to 46 concept names (`W-045d2`, D-069); `03-WORK-ITEMS.md` — `W-045d2`'s count (52), file count (16) and Verify; code half → `W-045d3` (D-069); `03-WORK-ITEMS.md` — `W-045o` is not "no code" and loses the 1961 Act to new `W-045o2` (`W-045o`, D-066); `00-BRIEF.md` §7.1 — fifth classification `DEFERRED` (`W-045o`, D-066); `01-rulebook.md` R4 — `REBATE-OLD-001` TY 2025-26 figures confirmed (`W-045n`); `01-rulebook.md` R6 — `RATE-CESS-001` 1961 side is FA 2026 s.2(6), not s.2(11)/(12) (`W-045n`); `03-WORK-ITEMS.md` — `W-045k`'s three entries: 80C and HRA move to new `W-045k2` (no rules yet; HRA not a 2025-Act section; Schedule XV quoted in part) (`W-045k`, D-060); `00-BRIEF.md` §6.3 — the example `plainName` types ₹ against SC-2, and provisions are `Provision`s not sections (`W-045k`, D-060).

**Errata written in session 5 (§10 requires them recorded here):** `03-WORK-ITEMS.md` — `W-045j`'s "105 sections" is 116 mixed-Act citations; rulebook seeding → `W-045o` (`W-045j`, D-059); `03-WORK-ITEMS.md` — `W-139` inherits `PackDiff` in `timeline/`, adds lint findings + `Magnitude`, owns the regime-diff gap (`W-045h`, D-057); `03-WORK-ITEMS.md` — `W-045g2` split, `W-045g3` added; T9-21 is not a test; the old wrong-year test passed on a checksum (`W-045g2`, D-055); `03-WORK-ITEMS.md` — `W-045g` split, `W-045g2` added, startup wiring + JAR loading UNOWNED (`W-045g`, D-054); `03-WORK-ITEMS.md` — `W-045g` inherits runtime timeline loading and the `RuleIdNamingTest`/`ConstantGenerator` move (`W-045f`, D-053); `03-WORK-ITEMS.md` — `W-045n` also sources the date meaning of `WITHDRAWN`/`STRUCK_DOWN` (`W-045e`, D-052); `01-rulebook.md` R3 — old → new rule-ID mapping for the 7 slab IDs (`W-045d`); `03-WORK-ITEMS.md` — `W-045d`'s Verify regex allows the statutory "NEW-TAX-REGIME", and its mapping covers R3 only (rest → `W-045d2`).

## Session 3 — what happened (2026-09-21)

**Opened** on a red build with `W-035` blocked on B-002. **Closed** with Phase 4 complete, the build
green at 1410 tests in ~9 s (NFR N12 budgets 20 s), nothing in flight and nothing blocked.

**Eleven items committed**, all reviewed and approved individually: `W-035`, `W-036`, `W-037`,
`W-037b`, `W-037c`, `W-038`, `W-039`, `W-040`, `W-044`, `W-042`, `W-043`. Test count 1225 → 1410.

**Three blockers, all resolved:** B-002 (`taxrules` `CLOSED` → `OPEN`), B-003 (A3's `EngineInputs`
holding types `taxengine` may not see), B-004 (`W-039` and `W-040` contradicting each other).

**Eleven decisions, D-022 … D-032**, each recorded in the table above with its reasoning.

**Nine errata**, all additions-only: TS T1, TS T5, DR 23 A3/A6/A8/B3, DR 15 F6, and two on
`03-WORK-ITEMS.md` (the `W-037` split, the `W-085` and `W-107` type moves).

### The pattern worth carrying forward

**The design hand-lists things, and the lists are stale.** Five separate times this session a
design document named a type, a component or a constant that does not exist or cannot be referenced
from where it is written. The two checks that caught every one of them are cheap and belong in
**stage 1 of every item**:

```
# 1. does each type the spec names exist, and where?
for t in <types the spec names>; do find backend/src/main/java -name "$t.java"; done
# 2. for each that exists: which module owns it, and may the holder depend on that module?
```

The audit categories now number **five**, and only the first two have been swept:
Appendix A (types the design *declares*, no owner) · Appendix B (types in a `permits` clause) ·
types named only as a **record component** in a code block (`W-036`) · a component type whose
**module the holder cannot see** (B-003, four occurrences) · a type owned by **two** items
(`W-043`).

**And two defects were mine, not the design's.** `W-040`'s cycle arrows pointed backwards;
`W-039`'s non-empty `requires()` made every graph unbuildable. Both had passing tests. Both were
constraints added in the same item that invented the concept they constrained — **a guard is only
proven by something independent passing through it**, and one written alongside its only user
proves nothing. `W-039`'s rule was green for two items before `W-042` showed it was unsatisfiable.

**Three guards are now proven to bite** rather than assumed to: the withdrawal gate (`W-004`), L5
guard 3 (`W-039`, proven by injecting an override), and `W-038`'s structural fingerprint (proven by
adding a field to a nested record and watching it reach the cache key). Still unproven and noted
since `W-002`: `taxengine_does_not_use_TMoney` and `only_the_adapter_converts` — the latter first
bites at `W-087` (`MoneyBridge`), which is the natural place to prove it.

---

**A fourth audit gap, found by `W-043`: a type owned by TWO items.** `ComputationReceipt` was in
the `Produces` of both `W-043` and `W-107`. Appendix A checks for types with **no** owner and
Appendix B for types named in a `permits` clause; **neither checks for a type owned twice**, and
nothing would have caught it until `W-107` found the class already written, 64 items later. Nobody
has swept the backlog for duplicate `Produces` entries — it is one pass over the table and would
close the gap for good.

**Two of my own guards have now turned out to be wrong rather than protective** — `W-039`'s
non-empty `requires()` (B-004) and `W-040`'s cycle-arrow direction. Both were constraints that
looked obviously right in isolation, written before anything existed to disagree with them. The
shape to watch for: **a rule added in the same item that invents the concept it constrains, with no
second user yet.** `W-039`'s rule had passing tests and was still unsatisfiable. A guard is only
proven by something independent passing through it.

**`NodeHarness.plainTaxpayer()` is now the one `EngineInputs` fixture.** Before it, that
ten-component fixture had been rebuilt privately inside `NodeRegistryTest` and
`InputsFingerprintTest`. Every node test and `W-042` should take it from the harness rather than
build a fourth copy; tests that care about the taxpayer call `.inputs(...)`. **`NodeRegistryTest`
and `InputsFingerprintTest` still hold their own copies** — not touched, being outside `W-044`'s
`Produces`, and worth folding in whenever something else edits them.

**Arrow direction is now fixed by convention: `->` means "feeds into".** `W-040`'s cycle message
draws the path that way to match B3's mermaid (`C[components] --> GS[salary.gross]`). The first
implementation walked the *waits-for* edges, which is the same cycle drawn backwards and reads as a
contradiction beside the diagrams. Anything else that renders the graph should follow.

**⚠ Two things `W-039` left for a later item to switch on.** (1) **I3 is built but not wired** —
`NodeRegistry.rulesNotEvaluatedByAnyNode` works and is fixture-tested, but running it against the
real 2026-27 pack today reports *every* rule unreachable, because no `ComputationNode` exists yet.
**The item that first ships a real pipeline must enable it** (D-029); if nobody does, the dead-rule
check O-19 exists for silently never runs. (2) **`NodeId`'s thirteen well-known anchors** (B2 —
`GROSS_SALARY`, `GTI`, `TOTAL_INCOME`, `NET_PAYABLE` …) were **not** added: `NodeId` is `W-008`'s
and constants are `W-032b`'s generated-constants territory. B2's own comment says the full set is
declared in the pack. Somebody should decide whether those anchors are generated, hand-written, or
unnecessary — before the first node hard-codes `new NodeId("gti")`.

**✅ `W-038` derived the fingerprint structurally, and the property is proven, not asserted.** The
warning recorded here before the item was carried out: `InputsFingerprint` walks `EngineInputs` by
reflection over record components, all the way down. Proven by adding a component to the nested
`EnteredFacts` and touching nothing else — the canonical form became
`entered=T:EnteredFacts{probeField=V:String:probe}`, so the new field entered the cache key by
itself. All three probe edits reverted; `git diff` on the tracked files is empty.

**F6 was wrong in four provable ways, and finding that out was the point of checking.** Its `of(...)`
hashes an `electionHistory` that is not a parameter (it does not compile); omits the **taxpayer's
own** facts including the DOB its own INFERENCE cites as the motivating bug; omits `TemporalContext`,
`FilingFacts` and `taxYear`; and names two B-003 types. **A hand-maintained list failed to stay
complete inside the paragraph arguing for completeness.** Erratum written. Anywhere else the design
hand-lists "everything that matters", assume the same and check.

**Guard 3 is proven to bite, and that is worth more than it sounds.** STATE.md has carried a
standing complaint since `W-002` that several guards pass **vacuously** — `taxengine_does_not_use_TMoney`,
`only_the_adapter_converts`, and the money guards that sit inside the package they exempt. Guard 3
was tested by injecting an `adjustPipeline` override into `DataDrivenRegimeBehaviour`, confirming
the failure and its message (`Reviewed: [], found: [DataDrivenRegimeBehaviour]`), then reverting.
`git diff` on that file is empty. It joins the withdrawal gate as the second guard in this build
known to fail on violation rather than assumed to.

**B-003's module-placement trap, fourth occurrence — and it is now the most reliable predictor of
where the next stop is.** `W-037b` hit it again: `CapitalGainsType` lives in
`portfolio.dto.enums`, so `CapitalGainsInput` cannot hold it. The pattern across W-036, B-003 and
this item is consistent — **wherever the design writes one record holding another module's types,
the holder's module cannot see them.** The remaining places this is predictable: `DR 23` A6
(`W-043`, `TaxComputation`/`FilingPosition`) and anything in Phases 9-11 that reaches back into
`taxengine`. The check is two greps and belongs in the plan stage of each.

**A fifth audit category, found while building `W-037`: enum constants.** The test was written
against `PersonType.HUF` and `DisabilitySeverity.SEVERE`; neither exists. `PersonType` has **one**
constant (`INDIVIDUAL`) and `DisabilitySeverity` has `DISABILITY` / `SEVERE_DISABILITY`, because
`W-021` applied rule B2 and built only what the packs use. The design's prose names many more
person types. **Read the enum before writing against it** — a constant is exactly as likely to be
aspirational as a type is. Caught by the compiler in seconds, so it is a nuisance rather than a
hazard, but it is the same root cause as B-003 and Appendix B.

**The `W-037` grep pass was run, and it found B-003.** The recommendation logged after `W-036` —
grep A3's component types before starting — was carried out and is what caught this at the plan
stage instead of mid-code. The blind-spot category below is *not* what bit here: Appendix A had
already tabled `CapitalGainsSummary` and `ElectionState`. What bit was **module placement** — a
component type that exists, has an owner, and lives where the consuming module may not look. That is
a fourth thing no audit has checked, and it is cheap to check: for each component type of a design
record, which module owns it, and does the holder's module allow that dependency? `DR 23` A6
(`W-043`) is the next record of this shape.

**A third type-audit blind spot, found by `W-036`.** Appendix A covered types the design
**declares**; Appendix B (D-021) covered types named in a **`permits` clause**. Neither covers a
type named only as a **record component inside a design code block** — and that is exactly where
`ExplanationRef`, `ClaimCatalogueRef` and `PricedAlternative` live (3 occurrences in the whole of
`docs/`, all in DR 23 A8). **Nobody has audited that category.** `DR 23` A3 (`EngineInputs`, six
records, `W-037` — the very next item) and A6 (`W-043`) are both large code blocks of exactly this
shape, so the next occurrence is likely immediate. Worth one grep pass before `W-037` rather than
another mid-item stop. Note the asymmetry that makes this category worse than B2's: a sealed
variant can be added later for free, a record component cannot — it changes every construction site.

**Session 3 (2026-09-21).** Opened on a red build and a blocked item, as session 2 left it. Did not
write new code: confirmed the checkpoint and reproduced the failure exactly (1225 run, 1 error,
`WealthLensModulithTest`, the three `MoneyValue`/`NodeValue`/`TaxByBasis` violations), measured both
of B-002's options, put the decision to the developer, and applied **D-022**. Build green, `W-035`
committed as an item, checkpoint advanced off `W-034b`.

**Erratum written this session (§10 requires it be recorded here):** `02-TECH-SPEC.md` **T1** — "All
four are `@ApplicationModule(type = Type.CLOSED, ...)`" is unsatisfiable, because T1 also has the
modules depend on each other's types and every shared type lives in a sub-package. `git diff` on
that file is 18 insertions, 0 deletions.

**The lesson B-002 carries forward.** T1's defect was invisible for 34 items because `taxengine`
had no classes — a module with no code satisfies any dependency rule. Phase 4 is the first code in
`taxengine`, and `taxdocs` and `taxplanning` are still empty. **Expect the same class of stop when
each of them gets its first class** (`taxdocs` at Phase 11, `taxplanning` at Phase 7). Both are
`CLOSED` and both are depended on by nothing yet, so the direction that bit here — a consumer
reaching a sub-package — is the one to check first.

Fixed two stale lines in this file while here, both bookkeeping only: the Phase 1 status row carried
a duplicated `W-006 · … W-012 ·` tail after its `← COMPLETE` marker, and the counts line read
`blocked **0**` while B-002 was open.

Session 1. Decisions D-001…D-007 recorded on the design's recommendations; **D-006 (Q6) is still
open** and blocks nothing before `W-032`. The plan for `W-001` is written and is **awaiting
approval** — no code has been written.

`W-011` + `W-012` are implemented and verified; **awaiting review**. 16 tests. Approving them
**closes Phase 1**.

`W-011` names `ValidityTest` as its own Verify and that test is `W-012`'s `Produces` — the same
coupling as `W-008`/`W-009`. **That is now three pairs where an item cannot verify itself**
(`W-008`/`W-009`, `W-011`/`W-012`, and `W-005`/`W-006` where the type shipped untested). A systematic
backlog issue rather than three accidents: implementation and its test should be one item.

**Third spec conflict found, erratum applied to the design.** `05-domain-model.md` M1.2 makes
`Instrument.assentedOn` mandatory, but the pack manifest in `16-getting-started.md` §S2 omits it for
the `rules` instrument — correctly, because subordinate legislation is **notified, not assented**.
Now `Optional<LocalDate>`, with `Instrument.assented(...)` and `Instrument.notified(...)` factories
so the call site says which kind it is.

`W-010` has **no test** — its Verify is "compiles". `Provenance`'s invariant I1 check is therefore
unverified behaviour; the first thing that exercises it is the pack loader at `W-029`.

**Two bugs found in `02-TECH-SPEC.md` T3, both corrected by erratum:**
1. The `RuleId` pattern was `{1,3}`, which **rejects 10 real rule IDs** — every one with four
   hyphenated segments, e.g. `RATE-SLAB-OLD-2026-A` and `SAL-ALW-NEW-SUBSET-001`. Widened to `{1,5}`.
2. `TaxYearId.label()` did not zero-pad: the tax year beginning 2099 rendered `"2099-0"`, not
   `"2099-00"`. The synthetic extensibility pack is TY 2099-00, so it is exercised.
The other five patterns were checked against every real value and are correct.

**`W-008` cannot satisfy its own Verify command** — it names `IdentifierTest`, which is `W-009`'s
`Produces`. So the two were done together and are reviewed together. `IdentifierTest` is **73 tests**
and both erratum corrections carry a regression test that provably fails against the original spec:
the ten four-segment rule IDs, and `TaxYearId.of(2099).label()` being `"2099-00"` rather than
`"2099-0"`.

**One file outside `Produces`:** `Identifiers.java`, the shared validator. Proposed before the item
started and approved with "do W-008"; recorded because the `Produces` list does not name it.

The cess case in `RateTest` (4% of ₹10,80,000 = ₹43,200) is taken from `08-golden-tests.md` T2-1,
so the arithmetic the surcharge golden tests will depend on at `W-057` is already pinned at the
unit level.

Session 1 was interrupted by a usage limit between `W-007`'s approval and its commit. State was
recovered from `STATE.md` with nothing lost and nothing repeated — the first real exercise of the
resume procedure.

`Money` is now fully specified and tested. The discriminating assertion is `1234.99 → 1230`: a
wrong implementation using `setScale(0, HALF_UP)` yields 1,240, so the test does bite.

Found in `W-006`: the work item cited **T9-14** for the rounding case, which is actually
*"portfolio read-model unavailable → 503"*. The right source is **T13-14** in `13-edge-cases.md`.
Errata are now permitted in `03-WORK-ITEMS.md` too (**D-009**) and the correction is recorded there.

**D-011 restructured the backlog (2026-09-19).** Read `03-WORK-ITEMS.md`'s header before picking an
item. Three things changed:

1. **Rule B1** — an item's `Verify` may only name a test the item itself produces, or one from an
   item already done. This is now checkable.
2. **Seven ids are RETIRED** (`W-014`, `W-016`, `W-020`, `W-022`, `W-041`, `W-045`, `W-047`). They
   are no work. Do not treat a retired id as skipped — read its line, then move on.
3. **`W-013` was not buildable as written.** It declared `sealed interface CapShape permits` six
   types that `W-014`–`W-019` created, and a sealed type does not compile until every permitted type
   exists (JLS 8.1.1.2). Each cap-shape item now adds its own type to the `permits` clause, so it
   **modifies `CapShape.java`** — that modification is in its `Produces`, and is not a §8 blocker.
4. **`W-044` (`NodeHarness`) now comes before `W-042` (`PipelineRunner`)** — a runner cannot be
   tested without test nodes to run. This is the one place the backlog order changed.

Phases 6–14 were deliberately **not** restructured: they have no `Verify` column and test through
golden-suite milestones. The standing fallback for those is in the note above Phase 6.

`MoneyAssert` cannot assert this `Money` — it is `double`-based with a half-paisa tolerance
(**D-010**). Tests use AssertJ `isEqualTo` on `Money` directly, which compares scale as well as
value and so catches a scale slip too.

`Money` has **no test yet** — that is `W-006`, and it is where the first hand-derived expected values
appear (the s.516 rounding cases). Until then its behaviour is unverified; only that it compiles and
satisfies the ArchUnit guards is known.

**The money guards are still not proven to bite.** `Money` sits *inside* `..taxengine.money..`, which
is exactly the package both guards exempt, so it passes them by exemption rather than by compliance.
Proving they fail on violation needs a deliberate violation in a file outside the item's `Produces`.

Learned in `W-004`:
- A plain `@Test` **does** run inside an `@AnalyzeClasses` class (16 tests = 15 `@ArchTest` + 1).
  The plan's main risk is resolved; no separate test class was needed.
- The withdrawal gate **caught a real violation on its first run — itself**. Its own javadoc names
  the withdrawn abstraction in order to forbid it. Exempted by filename, the same shape as
  `ChargeRounding`'s exemption from the money rule. Unlike `W-002`'s guards, this one is **proven
  to bite**.
- TS T10 words the `BigDecimal` rule as a *field* rule; `taxengine` has no fields, and ArchUnit 1.3
  fails a rule that checks nothing. Written as a dependency rule instead — non-empty input set, and
  strictly stronger (catches parameters, returns and locals too).

**Protocol deviation, recorded.** The developer said "do W-003" rather than approving a plan, so
stage 2's stop point was collapsed for this item only. The review gate at stage 5 was kept. R12 is
otherwise unchanged; this was a one-item instruction, not a standing exception.

Found while planning `W-002`: the existing `money_rounding_lives_in_one_place` carries a clause the
design did not quote — `.and().haveSimpleNameNotEndingWith("ChargeRounding")`, an exemption for the
charges engine's configurable per-rule policy. Preserved. `19-engineering-foundations.md` E1 quotes
the rule without it; anyone reading E1 alone would drop it and break the charges build.

**Resolved (D-008).** Errata are now permitted in the design — append only, proof required, original
never edited. `19-engineering-foundations.md` E1 carries the first one.

Both `W-002` guards pass **vacuously** today: `taxengine_does_not_use_TMoney` first bites at `W-005`
(`Money`), `only_the_adapter_converts` at `W-087` (`MoneyBridge`). Neither has been proven to fail on
violation — proving it needs a deliberate violation outside the item's `Produces`.

Learned in this session:
- `taxplanning` moving `OPEN` → `CLOSED` was the risk in `W-001`; `modulith verify()` passes, so
  nothing outside the module reaches into it. Decision **D-003** stands.
- The three marker classes (`TaxRules`, `TaxEngine`, `TaxDocs`) may be unnecessary — `package-info`
  alone might suffice for module detection. Not tested either way; they are harmless and each
  carries a note to delete it once the module has real public types.
- Baseline before any taxplanning work: **730 unit tests green, 7.5 s**. NFR N12 budgets 20 s.
- **Protocol gap found while committing.** The design and build docs were untracked, so `STATE.md`
  could not be committed without either orphaning it or dragging in the whole specification. Handled
  with a **baseline commit** of `docs/taxplanning-design/` and `docs/taxplanning-build/` immediately
  before the `W-001` commit. `P4` should say so; it currently assumes the spec is already tracked.
- **Second protocol gap.** `P4` says commit `STATE.md`, *then* write the new hash into it — which
  dirties the tree again. Resolved by committing, reading the hash, updating `STATE.md`, then
  `git commit --amend`. `P4` should spell that out.

Reminders that matter most:
- `W-002` exists because the design would otherwise **fail the existing build on the first commit** —
  `ArchitectureTest` forbids `BigDecimal.setScale` outside `..shared.util.money..`.
- `W-032` (the first pack JSON) is the highest-risk item in Phase 3. Slab bands are `400000`, **not**
  `400001`.
- `W-049` (the rebate node) contains the single most dangerous line in the engine: the rebate must
  read `TaxByBasis.atSlabRates` **only**.
