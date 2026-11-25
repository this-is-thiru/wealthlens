# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-405 DONE — D-963; board D; committed and pushed (with D-962's placements and plans/W-337g2.md).
Stage           : 6 — DONE. Nothing in flight.
Last item commit: "taxplanning W-405: validate every entity against its table at start — nothing checked entities and DDL against each other; D-962, D-963" — matches `git log --grep`.
Next step       : plan W-407a (port audit: auth, insurance vs m1.6.1 — encryption, unique indexes, nullability/defaults, expiry).
  Then W-407b – e, then W-337g2a1 … g2c (after W-337g2 C2 – C5). Check the pipeline run of this push first.
Flags for owner : W-337f flags unchanged (inert @Field, GazetteCursor created*, lease audit nulls, findByEmailAndTransactionDateBetween).
Env notes       : NO Claude attribution; integration needs OrbStack + DOCKER_HOST=unix://$HOME/.orbstack/run/docker.sock; full tiers on the
  pipeline only. Tests run in UTC (argLine); time tests pin a fixed Asia/Kolkata clock.
Updated         : 2026-11-25 (session 122, W-405 committed and pushed)
```
## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch -> stop and report both outputs. Match -> do **Next step** and nothing else.
