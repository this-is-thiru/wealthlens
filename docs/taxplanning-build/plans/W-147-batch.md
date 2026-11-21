## Plan for a batch of ten Phase 15 clean-ups — W-147, W-149, W-150, W-168, W-170, W-174, W-171, W-175, W-331, W-335

> Planned in session 114 (2026-11-21). Owner: "pick multiple small items", then "batch W-171, W-175, W-331, W-335, W-147, W-149, W-150,
> W-168, W-170, W-174". One plan and one approval; each item keeps its own red test (where it has code), D-row, YouTrack issue and commit
> (the batching rule). Named `W-147-batch` so `FinishedPlanTest` reads it against no single item. **Stop point: nothing is built until
> the owner answers C1 – C2.**

**What this batch is for (one sentence, in my own words):** remove the one-time operations, and retire the runbook steps, whose runs are
now logged for every database, so no route or step that must never run again stays one call away.

### Why now (each gate, read in the run log at tag `docs-history-m1`)

| Item | Removes or retires | Gate (its row) | prod `Cluster0` | staging `it-staging` | YouTrack |
|---|---|---|---|---|---|
| W-147 | W-088u7's correction, §2.1 | §2.1 logged for prod | W-334e2 (count only, D-798) | W-334d3a (count only) | WL-29 |
| W-149 | the `FY` rewrite, §2.3 | §2.3 logged for prod | W-334e2 (163 written, after 0) | W-334d3a (56 written) | WL-32 |
| W-150 | the bonus allotment-date correction, §2.4 | §2.4 for every database with bonus actions | W-334e2 (written, second dry run 0) | W-334d3a | WL-34 |
| W-168 | the stored-term removal, §2.5 | §2.5 for every database | W-334e2 (163, after 0) | W-334d3a (201, after 0) | WL-35 |
| W-170 | the P&L split reshape, §2.6 | §2.6 for every database, its unplaced sides placed | W-334e2 (3 placed, **0 unplaced**) | W-334d3a (7 placed, **0 unplaced**) | WL-36 |
| W-174 | the deductible-charge removal, §2.9 | §2.9 for every database | W-334e2 (0, nothing to write) | W-334d3a (1, after 0) | WL-39 |
| W-171 | retire §2.7 (no code) | §2.7 for every database | W-334e2 (dropped, empty) | W-334d3a (11 backed up, dropped) | WL-37 |
| W-175 | retire §2.10 (no code) | §2.10 for every database | W-334e2 (read, nothing to write) | W-334d3a (0 flagged) | WL-40 |
| W-331 | retire §0.6, §0.8 (no code) | §0.8 for every database | W-334e1 (seven dropped) | W-334d2 (seven dropped) | WL-47 |
| W-335 | remove §6, the interim run | §6 logged for every database | signed off, D-849 | signed off, D-843 | WL-48 |

Dev holds nothing since D-837 and a fresh database needs none of these (each corrects data older code wrote).

### The code (measured, and a probe run)

| Item | Route (admin zone) | Main files | Tests deleted |
|---|---|---|---|
| W-147 | `POST /api/v1/admin/portfolio/trade-outcomes/rebuild-charges/correct` | `TradeOutcomeCorrectionController`, `RebuildChargeCorrectionReport`, `RebuildBuyChargeCorrection` | `RebuildBuyChargeCorrectionIntegrationTest`, `RebuildBuyChargeCorrectionTest` |
| W-149 | `…/trade-outcomes/financial-years/rewrite` | `TradeOutcomeFinancialYearController`, `FinancialYearRewriteReport`, `TradeOutcomeFinancialYearMigration` | `TradeOutcomeFinancialYearMigrationIntegrationTest` |
| W-150 | `…/corporate-actions/bonus/allotment-dates/correct` | `BonusAllotmentDateCorrectionController`, `BonusAllotmentCorrectionReport`, `BonusAllotmentDate`, `BonusAllotmentDateCorrection` | `BonusAllotmentDateCorrectionIntegrationTest` |
| W-168 | `…/trade-outcomes/stored-terms/remove` | `TradeOutcomeStoredTermController`, `StoredTermRemovalReport`, `TradeOutcomeStoredTermRemoval` | `TradeOutcomeStoredTermRemovalIntegrationTest` |
| W-170 | `…/profit-and-loss/split/reshape` | `ProfitAndLossSplitReshapeController`, `SplitReshapeReport`, `ProfitAndLossSplitReshape` — **not** `StoredReport`: W-173's `ProfitAndLossV2Backfill` uses it, so it goes with W-173 | `ProfitAndLossSplitReshapeIntegrationTest` |
| W-174 | `…/trade-outcomes/deductible-charges/remove` | `TradeOutcomeDeductibleChargeController`, `DeductibleChargeRemovalReport`, `TradeOutcomeDeductibleChargeRemoval` | `TradeOutcomeDeductibleChargeRemovalIntegrationTest` |

Nothing outside these files uses them but one Javadoc line (`TransactionBasedTradeOutcomeMigration:88` names `RebuildBuyChargeCorrection`;
reworded with W-147, `rebuiltRows` stays as the row says). No api-collection request, no Checkstyle suppression names them. **Probe
(this session, reverted):** all 26 files deleted together → it compiles; ArchitectureTest 26, WealthLensModulith 1, UnusedPrivateField 2,
RouteAccess 8, ApiZone 4, Response / RequestContract, OpenApi 3, ApiCollectionContract 1, RetiredTestRoutes 3, ProfitAndLossV2Backfill 12,
TradeOutcomeRebuild 6, BonusAllotment 3, CorporateAction 48, Portfolio 43 all green.

### Decisions (for approval with this plan)

- **C1 — §0.8's exports (W-331's row: "delete the step-2 exports").** They are your local backups of the legacy salary and policy
  collections — salary rows are personal data that no code reads since W-159; D-849 kept the post-run archives until W-337. **C1a
  (recommended):** a row for you: list them, then delete them (W-337 moves only live collections, so it never needs these). **C1b:** keep
  them with the archives until W-337.
- **C2 — the YouTrack issues.** **C2a (recommended):** I set each item's issue to **Done** (as WL-30) when its commit lands. **C2b:** you
  close them.

### Each item (one commit each, in this order)

- **The six removals** (W-147, W-149, W-150, W-168, W-170, W-174): **red first** — a case in a new
  `integration/RetiredOneTimeRoutesIntegrationTest` (Javadoc: why these are gone): an admin's `POST` to the route answers **404**, as for
  W-311's retired routes; today it is served. Then the files and tests above deleted; the runbook section's heading
  `· **retired by W-nnn (D-nnn) · do not run**` with a `> **Retired.**` note (where it ran, what removed it), its body kept as the record
  (as §2.0). Green: the new case, and ArchitectureTest, WealthLensModulith, UnusedPrivateField, RouteAccess, OpenApi, ApiCollectionContract.
- **W-171, W-175:** §2.7, §2.10 marked retired the same way; no code.
- **W-331:** §0.6 and §0.8 marked retired; C1a's row for you; the order table's `0.8` line `retired`.
- **W-335:** §6 removed with its pointers (the order note's sentence, §0.4's line, any other `§6` / `6.0.n` mention found at the build),
  the run-log lines kept.
- **Every item:** its D-row (D-874 onward, approval in each), board `D`, this plan's step, the order table's `2.1 – 2.14` line naming what
  is retired, YouTrack (C2a). WL-A-2 republished once at the end (the runbook changes in most commits).

**Numbers I will use:** none legal or monetary; the run-log figures above, as logged.
**Design sections I read:** each row; D-277 – D-378, D-479 – D-726 cited by them; runbook §0.6, §0.8, §2.0 – §2.10, §6; the run log at
`docs-history-m1`.
**What I am NOT doing:** W-173 (it unsets a stored field, a data write: its own deploy step), W-254 (the encryption pass, entangled with
code that stays), `StoredReport` (with W-173).
**How I will verify:** per item as above; the full tiers on PR #71's pipeline after the push.
**Post-deployment actions (R15):** none written to a database; for callers, the six routes answer 404 (one "Next deploy" line).
**Five roles:** Architect: one-time code gone once it has run everywhere. PM: a smaller attack and support surface. Senior engineer:
deletions proven by a route test each. Automation tester: red first per route. Manual tester: the runbook reads only what can still run.
**Risks or ambiguities:** a database not yet known (a new environment) would need these steps' effects — none is planned (W-337 starts SQL
from the migrated data).

### Steps

- [x] 1. Approval (owner: "approved C1a C2a", 2026-11-21)
- [x] 2. W-147 (D-874)
- [x] 3. W-149 (D-875)
- [x] 4. W-150 (D-876)
- [x] 5. W-168 (D-877)
- [x] 6. W-170 (D-878)
- [x] 7. W-174 (D-879)
- [x] 8. W-171 (D-880)
- [ ] 9. W-175
- [ ] 10. W-331 (and C1's row)
- [ ] 11. W-335
- [ ] 12. WL-A-2 republished
