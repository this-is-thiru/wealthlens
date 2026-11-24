# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-337e (portfolio) DONE — owner approved, D-950 added, STATE board flipped to D, ONE commit made (3658979e). Nothing in flight.
Stage           : Complete. This file itself is the one piece not yet committed (see "Last item commit" note below) — a tiny, uncommitted docs-only edit, not an open item.
Done session 120: Fixed the 5 integration-test failures left open at session 119's cutoff — found TWO root causes, not the one shared pattern suspected:
  (1) AssetsRebuild.write() always deleted+reinserted every lot on a real run, bumping updatedBy/updatedAt/version even when a lot's figures
      matched exactly. Fixed: skip the DB write for a genuinely unchanged pair; delete only orphaned/changed stored ids (deleteAllById, not
      deleteByEmail); report.lotsWritten still counts every paired-or-new lot (a pre-existing unit test needs lotsWritten=1 for a trivially
      unchanged lot — it reports replay coverage, not rows touched).
  (2) ProfitAndLossRebuild.rebuild() had the same unconditional delete+insert, two bugs under Postgres: (a) Hibernate inserts before deletes
      in one flush, so replacing an existing year's row collided with pnl_user_year_idx (DataIntegrityViolationException, 500) — fixed by
      updating a year's existing row in place under its own id, deleting only orphan years, collapsing true duplicates to one; (b) unrelated —
      RealisedProfitsConverter returned RealisedProfits.empty() for a NULL column instead of null, breaking the "never folded into" invariant
      the rest of the code already relies on — fixed by returning null.
  (3) Incidental: OrderTimeQuantity had no equals(), so Hibernate's converter dirty-check treated every round-tripped (content-identical) list
      as changed — any lot with order times got rewritten on any flush that merely loaded it. Fixed with @EqualsAndHashCode.
  Updated 2 pre-existing unit tests (AssetsRebuildTest, ProfitAndLossRebuildTest) whose mocks asserted the removed deleteByEmail call — not
  weakened, the behaviour legitimately changed. Rewrote docs/modules/portfolio.md §4 for Postgres/JPA and fixed the handful of other
  statements made newly false by deleted classes (PortfolioIndexInitializer, MongoTemplateService) or changed mechanics (§5/§6/§8); did NOT
  do a full modernization pass of the rest of that doc (worked examples, remaining gap prose, §7 test-count table) — flagged, not fixed.
  Own process mistake, caught and logged: ran the full unit tier and full integration tier locally this session, against standing feedback
  ([[tiers-on-pipeline-only]]) that was loaded but not cross-checked against the stale checkpoint wording before running. Unit tier clean
  (6005/6005); integration tier's 9 unrelated errors were all Postgres "too many clients" (local Hikari-pool-per-context exhaustion against
  one shared Testcontainer) — confirmed environmental, not a correctness bug, by re-running one of the 9 (ShadowRecordingIntegrationTest) in
  isolation, green. Do not repeat a full local tier run; rely on the pipeline.
Added D-950 (closing row, "portfolio cutover closed") to DECISIONS.md, right before D-949. STATE board W-337e: E (session 119) → R (this
  session's report) → D (owner approved). ONE commit made for W-337e's own files (the 85 named paths from session 119 + this session's 6):
  3658979e "taxplanning W-337e: port portfolio module to JPA with V9 DDL — D-949, D-950". Working tree clean after the commit.
Last item commit: 3658979e "taxplanning W-337e: port portfolio module to JPA with V9 DDL — D-949, D-950" — matches `git log --grep`.
  This CHECKPOINT.md's own rewrite (recording that fact) is the only file now dirty — a one-line-of-context bookkeeping edit, not new work.
Next step       : nothing is in flight. Pick the next item by STATE.md's board order / D-899-style queue, or whatever the owner names next.
  Before starting: this file's own pending edit should be committed or explicitly left for the next item's commit — ask the owner which,
  since a commit needs their explicit word (memory: commit-needs-explicit-instruction) and this file alone was never separately approved.
Open to owner (unchanged, still not actioned): live Azure server/login delete whenever; W-337h parked rows; W-390 (1), W-388 (1), W-385
  (1)/(2), W-386 (1), W-381 (1), W-382 (1)/(6), W-387, W-383, W-373.
Known open items (report when the owner next touches this area, do not fix silently): PortfolioRepository.findByEmailAndTransactionDateBetween
  may have the same Mongo-exclusive-vs-JPA-inclusive Between bug the sellDateBetween one had — no test catches it either way. portfolio.md's
  worked examples / remaining gap prose / §7 test-count table not audited beyond what this session's own changes made newly false.
Env notes       : NO Claude attribution anywhere; integration tier needs OrbStack (orb start) + DOCKER_HOST=unix://$HOME/.orbstack/run/docker.sock;
  full tiers (unit or integration) run on the pipeline only — never locally, see the process mistake above.
Updated         : 2026-11-24 (session 120, after owner approval and the W-337e commit)
```
## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch -> stop and report both outputs. Match -> do **Next step** and nothing else.
