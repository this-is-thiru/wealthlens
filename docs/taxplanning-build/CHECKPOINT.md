# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-337g2b1 DONE — D-974 (JsonWalk, EmbeddedTimes, JsonCopy; five collections). Committed and pushed.
Stage           : between items. Nothing in flight.
Last item commit: "taxplanning W-337g2b1: build JSON columns by the entity's type and copy the five small ones — the copier's typed walk; D-974" — matches `git log --grep`.
Next step       : check this commit's pipeline; then plan W-337g2b2 (assets, transactions, trade_outcomes: corporate-action copies,
  asset_request ciphertext, order-time quantities, charge break-ups; times per plans/W-337g2.md finding 3); EXPLAIN, board E, stop.
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
