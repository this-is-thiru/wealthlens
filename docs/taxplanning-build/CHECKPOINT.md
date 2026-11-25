# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-407a DONE — D-964 (no gaps); board D; committed and pushed.
Stage           : 6 — DONE. Nothing in flight.
Last item commit: "taxplanning W-407a: audit auth and insurance against m1.6.1 — encryption, indexes, nullability and expiry all match; D-964" — matches `git log --grep`.
Next step       : W-407b (port audit: shared privacy_* vs m1.6.1 — encryption, unique indexes, nullability/defaults, expiry; same table
  shape as plans/W-407a.md). Then W-407c – e, then W-337g2a1 … g2c (after W-337g2 C2 – C5). Check the pipeline run of this push first.
Flags for owner : W-337f flags unchanged (inert @Field, GazetteCursor created*, lease audit nulls, findByEmailAndTransactionDateBetween).
Env notes       : NO Claude attribution; integration needs OrbStack + DOCKER_HOST=unix://$HOME/.orbstack/run/docker.sock; full tiers on the
  pipeline only. Tests run in UTC (argLine); time tests pin a fixed Asia/Kolkata clock.
Updated         : 2026-11-25 (session 122, W-407a committed and pushed)
```
## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch -> stop and report both outputs. Match -> do **Next step** and nothing else.
