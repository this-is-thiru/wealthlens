# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-337g2a3 DONE — D-973 (election history, confirmations, gazette leads). Committed and pushed.
Stage           : between items. Nothing in flight.
Last item commit: "taxplanning W-337g2a3: copy election history, document confirmations and gazette leads — sub-document keys reach their snake_case columns; D-973" — matches `git log --grep`.
Next step       : check the pipeline (45748701282 for 5ee64639, then this commit's); then plan W-337g2b1 (03-WORK-ITEMS.md row; the
  typed JSON walk C2a and time table C4a of plans/W-337g2.md); EXPLAIN template, board E, stop for approval.
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
