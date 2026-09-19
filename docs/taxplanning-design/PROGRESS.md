# Design progress — do not delete
Commit at start: 7aecba8   Last updated: 2026-09-19T17:30Z

## Phase 1 — Rulebook (01-rulebook.md)
[x] R1  Tax-year model: FY/AY vs "tax year", governing Act per year, transition rules
[x] R2  Regime framework: default regime, opt-out conditions and deadlines for salaried
[x] R3  Slabs, both regimes, both years
[x] R4  87A rebate + rebate marginal relief (which regimes), both years
[x] R5  Surcharge bands, new-regime cap, surcharge marginal relief
[x] R6  Cess; rounding of income and tax (288A/288B or 2025-Act equivalent)
[x] R7  Salary definition(s): for HRA, PF, NPS, gratuity (basic, DA, commission)
[x] R8  Standard deduction, professional tax, entertainment allowance
[x] R9  HRA: formula, metro list per year, rent/PAN conditions
[x] R10 LTA: block, journeys, carry-over, regime
[x] R11 Special allowances (children education, hostel, transport, uniform, etc.) per year
[x] R12 Perquisites: car, driver, meals, gifts, accommodation, loans, phone, ESOP/RSU, per year
[x] R13 Employer PF / NPS / superannuation, the aggregate cap, taxable interest on excess
[x] R14 Retirement / termination: gratuity, leave encashment, VRS, commuted pension, notice pay
[x] R15 Chapter VI-A (or 2025-Act equivalents): 80C, 80CCC, 80CCD(1)/(1B)/(2), 80CCE, 80D, 80DD, 80DDB, 80E, 80EE/EEA, 80G, 80GG, 80TTA/TTB, 80U — regime availability
[x] R16 House property: self-occupied vs let-out, 24(a)/(b), pre-construction, loss set-off cap, new regime
[x] R17 Other sources: interest, dividends, family pension, deductions
[x] R18 Inter-head set-off and carry-forward relevant to D3
[x] R19 Arrears relief (Section 89 / Form 10E or equivalent)
[x] R20 TDS on salary: computation method, 12BB, 12B, perquisite TDS, employer-paid tax
[x] R21 Form 16: Part A and Part B structure and fields (from an official source only)
[x] R22 ITR form applicability for D3 scope
[x] R23 Advance tax applicability for salaried users with other income
[x] R24 Pitfall check: confirm/refute H-1..H-16 below, each linked to rule IDs

## Phase 2 — Lessons (02-lessons.md)
[x] L1 Table: old-code mistake (O-IDs) -> design rule that prevents it
[x] L2 Reusable ideas worth keeping (with verification status)

## Phase 3 — Advisory logic (03-advisory.md)
[x] A1 Regime comparison + break-even deduction level
[x] A2 Salary structuring optimizer (formulation, constraints, algorithm)
[x] A3 Deduction gap analysis and next-best-action ranking
[x] A4 Language and disclaimer standards

## Phase 4 — Product spec (04-product-spec.md)
[x] P1 Personas and jobs-to-be-done
[x] P2 Features F1..Fn: story, acceptance criteria, rule IDs, RICE, MVP/Next/Later
[x] P3 Out-of-scope list

## Phase 5 — Domain model (05-domain-model.md)
[x] M1 Tax-year registry and versioned rule packs (effectiveFrom/To, source, status)
[x] M2 Component-code registry (no string literals); mapping to rule IDs
[x] M3 Profile, income sources, employments (multi-employer, dates), declarations, proofs
[x] M4 Documents (Form 16 ingestion record, parse result, confidence, reconciliation)
[x] M5 Computation + line-by-line trace; scenarios
[x] M6 Money: representation, scale, rounding policy, where rounding happens
[x] M7 Mongo collections, indexes, validation

## Phase 6 — Algorithms (06-algorithms.md)
[x] G1 Computation pipeline: ordered steps, each citing rule IDs, with pseudocode
[x] G2 Deduction-cap aggregator (80CCE, employer-contribution aggregate)
[x] G3 Marginal relief (rebate and surcharge): single implementation
[x] G4 Monthly TDS projection with revisions, joining, previous employer
[x] G5 Form 16 parsing strategy, validation, reconciliation vs computation
[x] G6 Optimizer algorithm and complexity
[x] G7 Explainability trace format

## Phase 7 — Architecture (07-architecture.md)
[x] X1 Modulith module split, allowed dependencies, events (incl. portfolio feed)
[x] X2 SPIs: IncomeHead, ComputationNode, RegimeStrategy, RulePack provider, DocumentParser
       (ComputationNode superseded by ComputationNode in Phase 14; RegimeStrategy by RegimeProvider
        in Phase 12; see 23-architecture-consolidation.md A4/A5 for the final taxonomy)
[x] X3 API contract (endpoints, DTOs only, validation, errors, authz incl. future advisor access)
[x] X4 Rule-pack authoring and year rollover process (data-only)
[x] X5 Testing strategy: golden files per tax year, property tests, mutation/coverage gates
[x] X6 Mermaid: module diagram, class diagram, compute sequence diagram

## Phase 8 — Golden tests (08-golden-tests.md)
[x] T1 Slab and 87A boundaries (at, +Rs 1, +Rs 50k), both regimes, both years
[x] T2 Surcharge entry and marginal relief
[x] T3 HRA (metro/non-metro per year, with DA)
[x] T4 PF/NPS incl. aggregate cap
[x] T5 LTA, 80C with employee PF, 80CCE
[x] T6 House property let-out loss, other sources
[x] T7 TDS projection: mid-year join, revision, previous employer
[x] T8 Form 16 reconciliation mismatch cases
[x] T9 Robustness: missing rule pack, null inputs, unknown component code

## Phase 9 — Build plan (09-build-plan.md)
[x] B1 Ordered vertical slices; each slice has scope, rule IDs, tests, definition of done
[x] B2 What to delete from the placeholder and when

## Phase 10 — Second pass (revision after review)
[x] E1 N parallel regimes as data (10-extensibility.md) — statute already has 5 (ss.200-204, s.205)
[x] E2 Open component vocabulary — registry, not closed enum
[x] E3 Relief formulas as an extensible sealed family
[x] E4 N-way comparison, break-even and optimiser
[x] F1 Stored entries + unlimited filing drafts (11-filing-drafts.md), revisions, concurrency, rollover
[x] C1 Claim integrity: statutory vs evidenced vs declared (12-claim-integrity.md)
[x] C2 Verified documentation and visibility thresholds (rule 163, rule 237, s.133)
[x] C3 Scrutiny selection criteria recorded as UNKNOWN (unpublished, no source opened)
[x] R1 Fifteen reframed decisions + 77-case edge catalogue (13-edge-cases.md)
[x] O1 Operational design: PII, authz, idempotency, audit, retention, docs, observability (14-operational-design.md)
[x] Contradicting passages in 02, 03, 05, 06, 07, 08 patched to match

## Phase 11 — Third pass (defects, engines, architecture)
[x] D1 Defect register: 18 items (15-review-findings.md Part A)
[x] D2 Computation as a DAG, not a linear list; NodeValue; incremental recomputation
[x] D3 Interest and fee engine (ss.423, 424, 425, 425(4), 426, 428) — all verified this session
[x] D4 Election engine (5 statutory elections, priced by recomputation)
[x] D5 Proration engine (named basis per rule: completed months / calendar months / days)
[x] D6 Rule-pack linter and cross-year diff (catches the H-2 class at authoring time)
[x] D7 Boundary-case generator (golden inputs derived from the pack's ladders)
[x] D8 Condition algebra — sealed typed predicates, incl. rule-level effective dating
[x] D9 New entities: PAN on profile, FamilyMember, Employer, Landlord, Lender, BankAccount, Election, RulePackReview
[x] D10 Rate type; Money.roundToNearestTenPerSection516; corrected inputsFingerprint
[x] S1 Getting-started plan: Day 0 decisions, Day 1 skeleton with real pack JSON, 24 PRs, 3 tracks (16-getting-started.md)

## Phase 12 — Fourth pass (regime lifecycle)
[x] L1 Four lifecycle shapes verified in the Act (s.201(2), s.203(2), s.204(2), s.202(4)(a)(iii)-(iv))
[x] L2 RegimeLifecycle: entryWindow vs inForceWindow; ContinuationPolicy; InvalidationPolicy
[x] L3 ElectionProtocol + BindingSpec replace the lockIn enum; RegimeElectionHistory state machine
[x] L4 Playbooks: introduce / withdraw / close-entry-only / flip-the-default
[x] L5 Three-tier escalation for per-regime behaviour; RegimeBehaviour SPI with guard rails
[x] L6 Invariants I9-I12; tests T17-1..T17-11; patched 10, 11, 15, 16

## Phase 13 — Fifth pass (temporal versioning)
[x] V1 Evidence: 5 verified instances of rules changing over time (s.197(3), G.S.R. 822(E) split
       commencement, 4 Rules-2026 amendments in 6 months, FA 2026 s.4 retrospective to 2007,
       Sch III Sl.38A pending notification)
[x] V2 D-TEMPORALKEY: per-rule TemporalAxis; resolution by date, not only by year
[x] V3 RuleRow = stable ID + ordered List<RuleVersion>; Validity {Between, Retrospective, PendingNotification}
[x] V4 Mid-year apportionment with a DECLARED combination rule; UNDETERMINED fails loudly
[x] V5 ComponentLifecycle + DeprecationPolicy + DraftUpcaster (blocks, never silently drops)
[x] V6 Clause-level lints (V7); AmendmentRef ledger (V8)
[x] V7 No polling: pin + staleness sweep + out-of-band gazette watch that opens a ticket
[x] V8 Invariants I13-I18; tests T18-1..T18-13; Condition.EffectiveBetween withdrawn
[x] V9 Patched 05, 10, 15, 16

## Phase 14 — Sixth pass (engineering foundations, from inside the repo)
[x] E1 ArchitectureTest:102-107 forbids BigDecimal.setScale outside ..shared.util.money.. —
       the design would have failed the build. Resolved: BigDecimal Money in ..taxengine.money..,
       rule amended deliberately + 3 new rules + one named MoneyBridge seam. THIS IS PR 0.
[x] E2 Rule-pack authoring toolchain: JSON Schema, typed DSL, CLI goals
[x] E3 Build-time pack compilation + generated RuleIds/ComponentCodes constants
[x] E4 Metamorphic properties, independent Python oracle, trace snapshots, pack fuzzing, builders
[x] E5 Closed error catalogue with retryable/userActionable flags
[x] E6 Computation receipt (TXC-2026-9F3A21)
[x] E7 API depth: versioning, projections, ETag, quotas
[x] E8 Background job model with Mongo leases
[x] E9 Reuse inventory verified against the actual tree; Excel import promoted to MVP (F28)
[x] E10 Persona fixtures; E11 three acceptance gates incl. CA sign-off

## Phase 15 — Seventh pass (product and feature surface)
[x] P1 Journey audit: 6 of 8 stages have gaps
[x] F29 Refund/payable forecast (MVP, home screen)
[x] F30 Tax calendar with measured cost of inaction (MVP)
[x] F31 Offer comparison after tax (Next, best differentiator)
[x] F32 Life events incl. the freelancing regime lock-in trap (Next)
[x] F33 Completeness weighted by measured worth (MVP)
[x] F34 Progressive onboarding (MVP)
[x] F35 Multi-year view incl. loss destruction on regime switch (Next)
[x] F36 ITR schedule-ready export (MVP)
[x] F37 Plain-language explanations from TraceLine.limitedBy (MVP)
[x] F38 Rent vs buy (Later); F39 Document vault (Next)
[x] Revised tiers; what deliberately does NOT belong

## Phase 16 — Eighth pass (ITR forms as data)
[x] I1 Audit: R22 had rows and features, no model. Forms were implicitly code.
[x] I2 Evidence: r.164(1) scopes the set to one tax year; r.164(14) retains historical sets;
       nine forms (ITR-1..7, ITR-V, ITR-UN); r.164(12) furnishing-manner dimension
[x] I3 ItrFormSpec as pack data; eligibility reuses the Condition algebra unchanged
[x] I4 Deterministic cascade selector returning ALL verdicts with reasons
       CORRECTION: r.164(4) makes forms mutually exclusive — not "simplest eligible"
[x] I5 ItrSchedule shared by reference; FieldSource makes F36 a fold over data
[x] I6 Per-year packs + FILING_DATE-axis validity for mid-year form revisions
[x] I7 Playbooks: new form / withdrawn form
[x] I8 Invariants I19-I22; tests T21-1..T21-16; patched 01, 04, 05, 16, 20

## Phase 17 — Ninth pass (claim catalogue)
[x] C1 Gap: rulebook says what the law allows, nothing said what the USER DOES
[x] C2 Five claim channels (E / R / R+ / T / P), verified from s.392(4)(a), s.122(5), s.133(6), s.124(1)
[x] C3 KEY FINDING: s.122(5) late-return bar applies to Part C ONLY. Part B/D/E — essentially
       everything a salaried person claims — survives a late return. Only 80G (T) and employer NPS (P)
       can actually be lost.
[x] C4 ClaimCatalogueEntry model; catalogue lives in the pack beside the rule
[x] C5 Twelve worked entries: HRA, 80C, 80CCD(1B), employer NPS, 80D, home-loan interest, LTA,
       80G, 80TTA/TTB, 80U/80DD, professional tax, regime election
[x] C6 Materiality bands: INVISIBLE_AFTER_ROUNDING (< Rs 5, s.516 rounds to Rs 10),
       BELOW_TYPICAL_EFFORT, MATERIAL. Asymmetry: never suppress INCOME, only deductions.
[x] C7 Counterparty enum — ask whom, and for exactly what artefact
[x] C8 r.164(11): nothing attached to the return; certificates retained. Invariants I23-I26;
       tests T22-1..T22-12; new feature F40 (MVP, RICE 1.00); patched 04, 05, 12

## Phase 18 — Tenth pass (architecture consolidation)
[x] A1 Audit: 12 gaps, all DRIFT rather than defects
[x] A2 Pack layered by change rate: law/ exec/ forms/ content/ — separate versions and checksums.
       Compute cache keys on law+exec only, so editorial changes invalidate nothing. Invariant I27.
[x] A3 EngineInputs — the engine fetches NOTHING; election history, ledgers, family, CG all passed in.
       Fixes the 17 L3 boundary violation. Fingerprint is now a hash of one object.
[x] A4 D-REGIMEZERO: a disallowed relief produces a trace line with out=0 AND a reason, not silence.
       ComputationStep WITHDRAWN -> ComputationNode; swept across 10 docs; invariant I28 + CI grep gate.
       IncomeHead narrowed from SPI to a label over a subgraph.
[x] A5 Extension taxonomy consolidated: 12 sealed families + 5 SPIs, with the rule for choosing
[x] A6 Determinism: TaxComputation (date-independent, cached forever) + FilingPosition (overlay).
       No clock in taxengine (ArchUnit). Makes F29/F30 affordable.
[x] A7 Error mapping: sealed TaxEngineException -> TaxError, classified user/pack/engine for alerting
[x] A8 Trace layering: TraceLine (values, provenance) vs TraceAnnotation (notices, explanations, claims)
[x] A9 Module boundaries final — interest/fee, election, ITR selector and export all land in taxengine
       because A3 made "does it need the DB?" the wrong question
[x] A10 Read models for F29/F30/F33/F35, rebuilt on read when their source is stale
[x] A11 Invariants I27-I32; tests T23-1..T23-11; NodeHarness for isolated node testing
[x] A12 One-page architecture; five tested properties

## Final
[x] 00-SUMMARY.md: key decisions, top risks, all UNKNOWN / NEEDS VERIFICATION items with sources to check
[x] git status shows only docs/taxplanning-design/

## Verification log
<!-- URL | what it confirmed | draft/final | date accessed -->
| # | URL | What it confirmed | Draft/Final | Accessed |
|---|---|---|---|---|
| S1 | https://www.pib.gov.in/PressReleasePage.aspx?PRID=2248005 | Income-tax Act 2025 in force 1 Apr 2026; assent 21 Aug 2025; Income-tax Rules 2026 notified by CBDT on 20 Mar 2026; new Forms notified | FINAL (PIB release, 01 Apr 2026) | 2026-09-18 |
| S2 | https://egazette.gov.in/WriteReadData/2025/265620.pdf | THE INCOME-TAX ACT, 2025 (No. 30 of 2025), full gazette text, 572 pp. Sections 1,2,3,4,13,15-19,20-25,92-95,122-157,202,392,516; Schedules II/III/XI/XV | FINAL (as enacted; NOT as amended by FA 2026) | 2026-09-18 |
| S3 | https://egazette.gov.in/WriteReadData/2026/271439.pdf | THE FINANCE ACT, 2026 (No. 4 of 2026), assent 30 Mar 2026. s.2 + First Schedule Part I-A (1961 Act, AY 2026-27 = FY/TY 2025-26); s.3 + Part I-B (2025 Act, TY 2026-27); surcharge tables, marginal-relief formulae, 4% cess; Chapter III Part B amendments to the 2025 Act | FINAL | 2026-09-18 |
| S4 | https://egazette.gov.in/WriteReadData/2026/271092.pdf | Income-tax Rules, 2026, G.S.R. 198(E) dated 20-03-2026, 2508 pp. Rules 73 (s.157 relief), 136 (regime option in the return), 164 (ITR forms), 203-206 (TDS credit, Forms 122/123/124/130), 278 (LTA), 279 (HRA, 8-city list), 280 (allowances), perquisite Tables I/II/III; Forms 122, 123, 124, 130, 39 | FINAL as notified; amendment chain to G.S.R. 656(E) of 24-07-2026 NOT traced | 2026-09-18 |
| S5 | https://egazette.gov.in/WriteReadData/2026/271857.pdf | Corrigendum G.S.R. 286(E) dated 16-04-2026 to G.S.R. 198(E): 40+ textual corrections, none touching rule 279 or 280 | FINAL | 2026-09-18 |
| S6 | https://egazette.gov.in/WriteReadData/2026/276301.pdf | Income-tax (Fourth Amendment) Rules 2026, G.S.R. 822(E) dated 17-09-2026: amends rules 160, 176, 225, 246, 256 and Forms 169, 171. Foot-note records last prior amendment as G.S.R. 656(E), 24-07-2026 | FINAL | 2026-09-18 |
| S7 | https://egazette.gov.in/WriteReadData/2025/262125.pdf | THE FINANCE ACT, 2025 (No. 7 of 2025), assent 29-03-2025. s.9 (17(2) thresholds -> prescribed), s.10 (s.23(2) self-occupied), s.20 (87A: Rs 12L / Rs 60,000 + marginal relief + cap), s.25 (115BAC(1A)(iii) slab table for AY 2026-27 onwards) | FINAL | 2026-09-18 |
| S8 | https://gazettetracker.com/search?q=... | Third-party gazette index. Used ONLY to resolve G.S.R. numbers to egazette document IDs. No legal fact taken from it | index | 2026-09-18 |
| S9 | https://www.ey.com/en_in/technical/alerts-hub/2026/03/key-changes-under-the-income-tax-rules-2026 | EY technical alert. Corroborates the DIRECTION of the Rules-2026 changes (HRA city list "expanded"; allowance limits "enhanced"; food/gift/motor-car valuations "revised") and that the Rules are FINAL. No numeric value taken from it | secondary/professional | 2026-09-18 |
| S10 | (blocked) https://www.incometaxindia.gov.in/... | HTTP 403 on every request, via both curl and the fetch tool. The Income-tax Act 1961 base text and the whole of the Income-tax Rules 1962 could NOT be opened. This is the source of every NEEDS VERIFICATION on the TY 2025-26 detail rows | NOT OPENED | 2026-09-18 |
| S11 | (404) https://www.indiacode.nic.in/handle/123456789/2435 | India Code handle for the Income-tax Act 1961 returned HTTP 404; site root returned a 2 KB stub | NOT OPENED | 2026-09-18 |
| S12 | https://egazette.gov.in/WriteReadData/2026/271092.pdf (same as S4, further sections) | Rule 163 (mandatory return-filing triggers); rule 237(2) and 237(6) (Statement of Financial Transaction — capital gains on listed securities/MF units, dividends and interest reported for ALL transactions, no threshold); rule 205(2) evidence table; rule 279(1)(c), 280(2), 278 | FINAL as notified | 2026-09-18 |
| S13 | https://egazette.gov.in/WriteReadData/2025/265620.pdf (same as S2, further sections) | ss.199-205 (five parallel regime sections; s.205(1) shared disallowance list; s.205(2) shared eligibility conditions); s.133(4),(5),(6) (80G: money only, non-cash above Rs 2,000, allowable only on the institution's report); s.156(3) (rebate capped at s.202(1) slab-rate tax); s.425(4) (no shortfall interest on capital gains/dividend if paid by the next instalment or 31 March); s.21(7)(a) self-occupied election; s.263(5) revised-return window | FINAL, as enacted | 2026-09-18 |
| S14 | https://egazette.gov.in/WriteReadData/2026/271439.pdf (same as S3, further sections) | FA 2026 s.4 (retrospective insertion "with effect from 1 June 2007"); s.5 and s.66 (due dates, revised-return windows) | FINAL | 2026-09-18 |

### Code read first-hand this session (for `path:line` claims)
- `backend/src/main/java/com/thiru/wealthlens/taxplanning/package-info.java` — `allowedDependencies = {"shared", "auth"}`
- `backend/src/main/java/com/thiru/wealthlens/portfolio/package-info.java` — `allowedDependencies = {"shared", "auth", "corporate", "brokercharges", "helper"}`
- `backend/src/main/java/com/thiru/wealthlens/portfolio/entity/model/RealisedProfits.java` — `gainsByClassification` is `Map<CapitalGainsType, FinancialReport>`
- `backend/src/main/java/com/thiru/wealthlens/portfolio/dto/enums/CapitalGainsType.java` — five values, `countsAsCapitalGains()`
- `find` over `backend/src/main/java/com/thiru/wealthlens/taxplanning` — **56** Java files; `backend/src/main/resources/data/tax-policies/` — **5** seed JSONs; **6** unit test classes plus `backend/src/test/java/com/thiru/wealthlens/integration/TaxPlanningControllerIntegrationTest.java`
- `docs/taxplanning-context/00-INDEX.md` and `04-constants-tests-history.md` — the context pack. **Leads, not proof.**

## Decision log — TO BE COMPLETED BY THE DEVELOPER
See 16-getting-started.md S1. Seven questions (Q1..Q7) block concrete work. Record answers here.
[ ] Q1 Ship TY 2025-26 at launch, or TY 2026-27 only?   (recommended: 2026-27 first)
[ ] Q2 Four modules or one?                             (recommended: four)
[ ] Q3 Modulith Type.CLOSED or OPEN?                    (recommended: CLOSED)
[ ] Q4 Advisor path within 12 months?                   (recommended: seam only)
[ ] Q5 Document ingestion in release 1?                 (recommended: fast-follow, flag off)
[ ] Q6 Who signs off a rule-pack change?                (name a person)
[ ] Q7 SAL-ALW-NEW-SUBSET-001 per section or per r.280(3)? (recommended: per section, flagged)

## Notes for resume
ALL PHASES COMPLETE, INCLUDING THE SECOND PASS. Commit at start 7aecba8 — compare with
`git rev-parse --short HEAD` at the start of any resumed session.

Files (all under docs/taxplanning-design/):
  00-SUMMARY.md, 01-rulebook.md .. 09-build-plan.md, 10-extensibility.md, 11-filing-drafts.md,
  12-claim-integrity.md, 13-edge-cases.md, 14-operational-design.md, PROGRESS.md

SECOND-PASS OUTCOME: the first draft had five HIGH-severity defects, all found by the review
questions. Two regimes hard-coded; ComponentCode a closed enum (which made "a new allowance is a
data change" false); one input set per person per year; concurrent employment asserted away; and the
s.156(3) rebate cap not designed for (which would have produced a NIL tax bill on a Rs 12,00,000
income that is mostly long-term capital gains). Docs 10-14 fix all five and reframe ten more
decisions. Contradicting passages in 02, 03, 05, 06, 07 and 08 were patched, not left to disagree.

SCHEMA-FIRST WARNING carried into the build plan: defects 1, 2, 3 and 5 are rule-pack or persistence
schema decisions. They must land BEFORE Slice 1 authors either rule pack, because schema is the only
thing in this design that is expensive to change once a pack ships or a draft exists.

Carried-forward research gap is unchanged: incometaxindia.gov.in HTTP 403 (S10), indiacode.nic.in 404
(S11), so the Income-tax Act 1961 base text and the whole of the Income-tax Rules 1962 were never
opened. All TY 2025-26 DETAIL rules remain NEEDS VERIFICATION; TY 2025-26 RATE rules are VERIFIED
from the Finance Acts. Nine golden cases remain PROVISIONAL. Slice 0 closes this; egazette.gov.in
(WriteReadData/<year>/<id>.pdf) worked for every gazette tried and is the route to try.

STATUS UNCLEAR items now four: SAL-ALW-NEW-SUBSET-001 (r.280(3) inverts the regime condition),
REG-OPTOUT-LATE-001 (belated return and the opt-out), ROUND-NEG-001 (s.516 rounding of a negative
amount), AGE-BOUNDARY-001 (the 1 April birthday).

UNKNOWN items now two: SAL-ALW-BOOKS-001 (books-and-periodicals exemption), and CBDT's scrutiny
selection criteria (unpublished; no source opened; explicitly NOT modelled - see 12-claim-integrity.md C5).

All worked arithmetic was re-derived before publication; three slips of my own were found and
corrected (01-rulebook R5 surcharge example, 08-golden-tests T2-1 and T1-A1e).
