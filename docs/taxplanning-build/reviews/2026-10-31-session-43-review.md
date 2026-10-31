# Review of session 43's work (2026-10-31)

The 43 commits of 2026-10-31, `e5c7e305` (W-203) through `e556a394` (W-088i's post-commit state), read
item by item for correctness, architecture and product effect. Placed as backlog rows by **D-429**; each finding
below names its row. Nothing here changes code: the fixes are the rows' work.

**Build at review:** unit tier, this run's reports only — 3864 tests, 0 failures, 0 errors; tree clean. The
integration tier was not run (Docker). One stale report (`ZzPrintChecksumsTest`, 22:42, the relock dump tool) was
excluded; the tool is not in the tree.

**Probes.** Findings R1 and R2 were proven by a throwaway test, run once and deleted (the tree was clean after each).
Their inputs and outputs are below so that the fixing item can rebuild them as its regression tests. Every expected
figure was worked by hand from the law before the run.

---

## R1 — one house claim exempts several buckets of the same sale → W-088i5

`ReinvestmentExemption.claimFor` (`taxengine/node/ReinvestmentExemption.java:88`) matches a claim to a bucket by
`transferDate` alone, and refuses only where **two claims** name one date (`:93`). `portfolio` keys a bucket on
`acquiredOn` as well as `transferredOn` (`portfolio/service/CapitalGainsSummaryService.java:115`), so one sale that
consumes two FIFO lots bought on different dates is two buckets on one date. The single claim then reaches both, and
each bucket is measured with the **whole** house cost against its **own** net consideration.

D-428 records the approved decision (Risk 3) as "matched by the transfer date and refused where the match is not
unique"; the code enforces that for two claims on one bucket, not for one claim on two buckets.

**Probe** (`taxengine/node`, TY 2026-27, the `ReinvestmentExemptionTest` harness): two `EQUITY` buckets sold on NSE
on 2026-09-01, acquired 2023-05-01 and 2024-05-01, each consideration ₹60,00,000 and cost ₹20,00,000, STT paid,
no charges; one `HouseReinvestment(2026-09-01, 2027-01-15, bought, ₹60,00,000, 0, 0)`.

- Law (2025 s.86(1)(i) / 1961 s.54F(1)(b)), the sale taken whole: gain ₹80,00,000, net consideration
  ₹1,20,00,000, exempt 80,00,000 × 60,00,000 ÷ 1,20,00,000 = ₹40,00,000; chargeable **₹40,00,000**.
- Engine: `LONG_TERM_LISTED_EQUITY` = **₹0.00** — each bucket's cost ≥ net consideration branch exempts its whole
  ₹40,00,000.

No existing test has more than one bucket (`ReinvestmentExemptionTest`, 17 cases).

## R2 — the land cap's lesser-of is taken over the whole part → W-088g3

`RATE-CG-LONG-OTHER`'s cap is one `LeastOf` (`:28`, `:92`) of 12.5 % of `SPECIAL_RATE_GAINS` and 20 % of
`INDEXED_SPECIAL_RATE_GAINS`, both read per **part** (`TaxByBasisNode.basesOf`, `:123`). `CapitalGainsNode:110`
merges into the part's indexed measure every bucket's gain — the indexed gain for qualifying land, the plain gain for
anything else in `LONG_TERM_OTHER` (equity sold without STT, `CG-PART-LONG-OTHER`). The 20 % limb is then taken on
the non-land gain too. 1961 s.112(1)(a) second proviso / 2025 s.197(3) compute the lesser for the land gain; the
rest stays at 12.5 %.

**Probe** (`taxplanning/portfolio`, TY 2025-26, the `LandOrBuildingCapTest` harness, resident, ₹10,00,000 other
income): the plan's own plot — bought 2005-06-01 for ₹10,00,000, sold 2025-09-01 for ₹40,00,000 (indexed cost
10,00,000 × 376 ÷ 117 = ₹32,13,675.21, indexed gain ₹7,86,324.79) — plus an `EQUITY` bucket on NSE, STT paid on
acquisition and `NOT_PAID` on transfer, acquired 2022-06-01, sold 2025-09-01, consideration ₹20,00,000, cost
₹10,00,000.

- Law: land min(3,75,000, 1,57,264.96) = 1,57,264.96, plus 12.5 % of 10,00,000 = 1,25,000; **₹2,82,264.96**.
- Engine: part ₹40,00,000, indexed ₹17,86,324.79, tax min(5,00,000, 3,57,264.96) = **₹3,57,264.96** — ₹75,000 over.

Since min(Σa, Σb) ≥ Σ min(a, b), the part-level cap can never charge less than the law and charges more whenever
another bucket shares the part. Every `LandOrBuildingCapTest` case has one bucket.

## R3 — a land sale is computed without the provisions that change it → W-088g4

Before W-088g2 a land sale was refused at `CapitalGainsTerm` (a loud failure). It now produces a figure, while three
provisions that change a land figure are `DEFERRED` in `content/coverage.json` and were not discussed in
`plans/W-088g.md` or `plans/W-088g2.md`: 1961 s.50C (the stamp-duty value as the consideration, `:654`), the cost of
improvement (1961 s.48, 2025 s.72(1)), and 1961 s.55(2)(b) / 2025 s.55 (the fair market value on 1-4-2001 as cost,
`:672`, `:58`). Nothing on the computation says so.

## R4 — land enters through the stock trade path untested → W-088g5

The W-088g2 work-item row asked for the kind "end to end — the enum, the buy/sell paths and seeds"; the plan cut it to
the enum (`plans/W-088g2.md` point 1, step 1). `AssetType.LAND_OR_BUILDING` is now accepted by both JSON trade paths
and the Excel upload (`AssetRequestParser:105`), and no test posts one: `LandOrBuildingCapitalGainsTest` mocks the
repository.

## R5 — the exemption leaves the indexed measure as an amount, not a proportion → W-088i6

`CapitalGainsIndexation.indexedGainOf` takes the gain after the exemption (`CapitalGainsNode:110-111`) and adds the
indexation to it (`CapitalGainsIndexation:72`), so the same rupees leave both measures. s.54F(1)(b) / s.86(1)(i)
exempt a proportion of "the capital gain", which for the indexed measure is the indexed gain. Approved as D-428
Risk 4. Unreachable today — the exemption serves `EQUITY` and indexation serves `LAND_OR_BUILDING` — and wrong the day
W-088i4 widens the row. A question for the owner before W-088i4, not a defect today.

## R6 — the construction window's start is written in Java → W-088i7

`ReinvestmentExemption:115` — `reinvestment.constructed() ? transfer : transfer.minusMonths(...)`: that a house built
cannot count from before the transfer is a legal boundary (1961 s.54F(1); 2025 s.86(1)) held as code, which
CLAUDE.md §4 puts in rule data.

## R7 — W-182 called a removed call behaviour-free → W-216

`plans/W-182.md:92` says "Behaviour is unchanged: the method did nothing before" of the removed
`processStockSplit(corporateAction, brokerName, stockEntities)` call. Its body ran
`Integer.parseInt(corporateAction.getRatio().split(":")[…])`, so a null or malformed ratio threw before
`updateLastlyPerformedCorporateAction` (`CorporateActionService:433`); now such a split is recorded as performed, which
also lifts the block on held trades. W-213 owns the split's arithmetic; the ratio's validation is not in it.

## R8 — the api-collection pointer names an unpushed commit → W-217

W-204 (`ee7a6efe`) points `api-collection` at `56715c7`; `git -C api-collection status -sb` reads
`main...origin/main [ahead 1]`. A push of the parent alone leaves a pointer no clone can check out.

## R9 — two plans lack the five-role review, and a commit states a wrong figure → W-218

`plans/W-089.md` and `plans/W-088i.md` have no architect, product manager, senior engineer, automation tester or
manual tester section (the other eighteen plans of the day have all five). W-088i's subject (`e5233041`) says the sale
"pays ₹4,51,750 rather than ₹9,71,750": without the exemption total income is ₹80,00,000, so the 10 % surcharge
applies (1,25,000 exempt; 80,00,000 − 4,00,000 − 1,25,000 = 74,75,000 × 12.5 % = 9,34,375; × 1.10 = 10,27,812.50;
× 1.04 = **₹10,68,925**). The figure is asserted nowhere; it stands in history uncorrected.

## R10 — two guards claim more than they check → W-219

- `CoverageCitationTest:30` says its 96-entry baseline "may only shrink", but compares it as an exact set: an entry
  added with a new mismatch passes. `CheckstyleBaselineTest` is the repository's shrink-only pattern.
- `UnusedPrivateFieldTest:68` skips every private instance field initialised with a literal
  (`isConstantVariable`), read or not; its Javadoc's two stated blind spots do not include it.

## R11 — nothing stops a seventh request-bound entity → W-220

W-205 fixed the six entities a body binds (five as `@RequestBody`, `AssetEntity` through a DTO — the set checked
complete at review). The fix is a per-class annotation; no rule makes the next `@Document` bound from a body carry it.

## R12 — a corrected card has no route into a seeded database → W-221

`ChargeSeederService.seedSchedules` (`:164`) skips a card on file (`:186`) and reports it as drift; the runbook's
§1B says drift "Must be empty" (`post-deployment-runbook.md:277`) and gives no step for when it is not. W-185 and
W-186 corrected 13 + 8 shipped cards; that reaches staging and prod only because the run log has not yet recorded
§1A / §1B for either. The first correction after a seed will stall.

## R13 — the commit protocol's churn and its co-author line → W-222

23 of the 43 commits change only CHECKPOINT / STATE to name the commit before them; W-179 and W-198 needed a further
commit to correct their own resume state, and W-182 took four. Every commit carries
`Co-Authored-By: Claude Code <noreply@anthropic.com>`, while the owner states the sessions ran DeepSeek on the Claude
Code harness.

---

## Read and found sound

The `CG-EXEMPT-RESIDENTIAL-HOUSE` row's figures (12 / 24 / 36 months, ₹10,00,00,000 cap, at most one other house
owned, none acquired); the goldens of W-089 (₹87,750), W-088i (₹4,51,750) and W-088g (₹1,57,264.96), each re-worked
by hand; the Cost Inflation Index 2001-02 – 2025-26 against the Gazette notifications its source file lists; W-181's
guard and its handling of the redrive and stored rows; W-205's read-only audit field (no code reads these entities
back from JSON); W-203's strict seeders; W-196's nil line.

**Not verified here:** the integration tier; the 2026-27 index (384, S.O. 3889(E) of 15-7-2026) and the NSE IPFT
circular (NSE/FA/73061) were not re-read; W-194, W-195, W-197 and W-198 had a lighter read.
