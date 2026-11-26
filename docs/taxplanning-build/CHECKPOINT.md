# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-337g2b3 DONE — D-978 (profit_and_loss; all 40 specified). Committed and pushed.
Stage           : between items. Nothing in flight.
Last item commit: "taxplanning W-337g2b3: copy profit_and_loss — the gains and charge trees under their former names; every collection specified; D-978" — matches `git log --grep`.
Next step       : check this commit's pipeline; then plan W-337g2c (row in 03-WORK-ITEMS.md): CopierRun runs whole (a "runs and exits"
  case), post-deployment-runbook.md entry per W-337g2 C5a (precheck incl. user_details rows without email, D-964; copy BEFORE seeding
  the catalogue — the copier refuses a seeded target; --app.copier.written-zone=Asia/Kolkata), W-337j's row + a YouTrack issue,
  WL-A-2 republished. EXPLAIN, board E, stop.
Flags for owner : W-337f flags unchanged (inert @Field, GazetteCursor created*, lease audit nulls, findByEmailAndTransactionDateBetween).
Env notes       : NO Claude attribution; integration needs OrbStack + DOCKER_HOST=unix://$HOME/.orbstack/run/docker.sock; full tiers on the
  pipeline only. Tests run in UTC (argLine); time tests pin a fixed Asia/Kolkata clock. Next Flyway version: V14.
Updated         : 2026-11-26 (session 123)
```
## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch -> stop and report both outputs. Match -> do **Next step** and nothing else.
