# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-337g2b2 DONE — D-977 (trade ledger). Committed and pushed.
Stage           : between items. Nothing in flight.
Last item commit: "taxplanning W-337g2b2: copy assets, transactions and trade outcomes — corporate-action copies, the kept request and order times in UTC; D-977" — matches `git log --grep`.
Next step       : check this commit's pipeline; then plan W-337g2b3 (profit_and_loss: realised_profits_json and
  out_sourced_realised_profits_json, the four-level tree, both halves; renames capital_gains→monthlyReport,
  first/second_half_report→first/secondFortnightReport; ReportModel.lastUpdatedTime to classify; RealisedProfits.lastUpdatedTime
  local-held, ChargeSummaryReport.lastUpdatedTime UTC-held per plans/W-337g2.md finding 3); EXPLAIN, board E, stop.
  Note for W-337g2c: the runbook copies before seeding the catalogue (the copier refuses a seeded target).
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
