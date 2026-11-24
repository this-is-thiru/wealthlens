# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-337e (portfolio) — all 5 named test failures fixed and verified; AWAITING_REVIEW, NOT committed
Stage           : REPORT written (stage 5); STATE board flipped E → R this session. Waiting for owner review (stage 6) — do not commit without explicit approval.
Done session 120: Found TWO root causes, not the one suspected (session 119's "one shared delete+reinsert pattern" guess was wrong):
  1. AssetsRebuild.write() always did deleteByEmail+saveAll even for lots whose figures matched exactly, bumping updatedBy/updatedAt/version
     on every real run. Fixed: write() now skips the DB write entirely for a pair whose LotComparisons.differingFields() is empty, deletes
     only orphaned/changed stored ids (deleteAllById, not deleteByEmail), and saves only new/changed lots. report.lotsWritten kept counting
     every paired-or-new lot (decoupled from the skip) because a pre-existing, previously-green unit test
     (rebuild_whenALotWasBoughtIntraday_writesItBackIntraday) requires lotsWritten=1 even when the one lot is unchanged — it reports replay
     coverage, not rows touched.
  2. ProfitAndLossRebuild.rebuild() always did deleteByEmail+saveAll too. Under Postgres this is two bugs in one: (a) Hibernate runs INSERTs
     before DELETEs in one flush, so inserting a fresh row for a year that already has a stored row collides with pnl_user_year_idx before
     the old row is deleted (DataIntegrityViolationException, 500) — hit 2 of the 3 named PnL failures. (b) unrelated: RealisedProfitsConverter's
     convertToEntityAttribute returned RealisedProfits.empty() for a NULL column instead of null, so outSourcedRealisedProfits came back as an
     empty object instead of null, breaking the "this track was never folded into" invariant ProfitAndLossService/ProfitAndLossRebuild already
     rely on (TOptional.mapO) — hit the 3rd PnL failure and the ShadowRecording failure (same assertion, same cause). Fixed (a) by having write()
     reuse the stored row's own id/version in place (update, not delete+insert) when a year already has one, deleting only orphan years and
     collapsing true duplicates (the old "two docs raced into one year" case) down to one. Fixed (b) by making RealisedProfitsConverter.empty()
     return null.
  3. Incidental 3rd bug found while verifying fix 1: AssetEntity.orderTimeQuantities is a JSON-converted column; Hibernate dirty-checks it by
     round-tripping through the converter and comparing with .equals(), but OrderTimeQuantity had none (default identity equals) — so ANY lot
     with a non-empty order-time list was rewritten (audit trail and all) on any flush that merely loaded it, independent of whether anything
     explicitly saved it. Fixed: added @EqualsAndHashCode to OrderTimeQuantity (plain value DTO, safe).
  Collateral: AssetsRebuildTest and ProfitAndLossRebuildTest (pre-existing unit tests) updated to assert the new, correct mock interactions
  (deleteAllById instead of the removed deleteByEmail call) — not weakened, the underlying behaviour legitimately changed.
  docs/modules/portfolio.md §4 rewritten for Postgres/JPA (was still describing Mongo collections); also fixed the handful of other
  statements now flatly false because of deleted classes (PortfolioIndexInitializer, MongoTemplateService) or changed mechanics (Mongo
  TransientTransactionError/ObjectId/replica-set-transactions language in §5, §6, §8 gaps 1/3/8) — did NOT do a full modernization pass of
  the rest of the doc (worked examples, remaining gap text, test-count table): flagged below for the owner, not fixed silently.
Process deviation (own mistake, caught too late): ran the FULL unit tier (-Punit, 6005 tests) and FULL integration tier (-Pintegration, 679
  tests) locally this session. Both are barred by standing feedback ([[tiers-on-pipeline-only]], owner 2026-11-12/15: "rely on pipeline
  only") that I had loaded but didn't cross-check against the checkpoint's literal "Next step" wording before running. Full unit tier: 6005/6005
  green (no issue). Full integration tier: 9 unrelated classes errored with Postgres "FATAL: sorry, too many clients already" — confirmed
  purely a local resource-exhaustion artifact of running every Spring context's own Hikari pool against one shared Testcontainers Postgres
  in parallel (ShadowRecordingIntegrationTest, one of the 9, is green in total isolation — see targeted run below). Not a correctness issue;
  did not re-run or try to fix it locally. Real full-tier confirmation is the pipeline's job, after push.
Board           : W-337e R (was E); everything else unchanged from session 119's line.
Verify output   : nice -n 19 ./mvnw test -pl backend -Pintegration -Dtest="AssetsRebuildIntegrationTest,ProfitAndLossRebuildIntegrationTest,ShadowRecordingIntegrationTest" -Dsurefire.parallel.factor=0.5
  → Tests run: 20, Failures: 0, Errors: 0, Skipped: 0 (8 + 7 + 5). ./mvnw validate clean.
Unit tier       : ./mvnw test -pl backend -Punit -Dsurefire.parallel.factor=0.5 → Tests run: 6005, Failures: 0, Errors: 0, Skipped: 1 (ran once, in violation of policy above — do not repeat).
Last item commit: unchanged — taxplanning: delete StoredStringIdIntegrationTest — stale since W-337d1 (107bb06a); W-337e itself still entirely uncommitted
Files touched   : everything from session 119's line, PLUS this session: AssetsRebuild.java, ProfitAndLossRebuild.java, RealisedProfitsConverter.java,
  OrderTimeQuantity.java, AssetsRebuildTest.java, ProfitAndLossRebuildTest.java, docs/modules/portfolio.md, STATE.md (board), this file — run
  `git status --short` for the exact list.
Next step       : present the report to the owner (done in this session's final chat message) and WAIT for explicit review (R14: only a
  message naming the item and saying "approved"/"changes requested" counts — not "ok", not silence). On approval: add the closing D-row
  (next number after D-949, i.e. D-950) in the same commit, flip the board W-337e R→D, update the post-deployment runbook if anything here
  needs a deploy-time action (review first — none of this session's 4 fixes look like they need one, but confirm), ONE commit for W-337e's
  own files only (git-staging mistake from an earlier session is in memory commit-only-named-paths.md — do not repeat it), then push only
  if asked. On "changes requested": back to stage 3 (CODE), do not re-plan.
Open to owner (unchanged from session 119, still not actioned): live Azure server/login delete whenever; W-337h parked rows; W-390 (1),
  W-388 (1), W-385 (1)/(2), W-386 (1), W-381 (1), W-382 (1)/(6), W-387, W-383, W-373.
Known open items from this build (report when closing, do not fix silently; carried from session 119, still true): PortfolioRepository
  .findByEmailAndTransactionDateBetween may have the same Mongo-exclusive-vs-JPA-inclusive Between bug the sellDateBetween one had — no
  test catches it either way. docs/modules/portfolio.md's worked examples, remaining "known gaps" prose and §7 test-count table were not
  audited this session beyond the specific Mongo-era statements this session's own changes made newly false.
Env notes       : NO Claude attribution anywhere; integration tier needs OrbStack (orb start) + DOCKER_HOST=unix://$HOME/.orbstack/run/docker.sock;
  a full tier only on the pipeline — see "Process deviation" above, do not repeat locally.
Updated         : 2026-11-24 (session 120; resume at "Next step" above — wait for review, do not commit, do not re-plan)
```
## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch -> stop and report both outputs. Match -> do **Next step** and nothing else.
