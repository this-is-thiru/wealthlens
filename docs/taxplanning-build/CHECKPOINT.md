# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-407c DONE — D-966 (G-1 fix: V11 NULLS NOT DISTINCT), D-967 (close-out); board D; committed and pushed.
Stage           : 6 — DONE. Nothing in flight.
Last item commit: "taxplanning W-407c: audit corporate and brokercharges, rebuild three unique indexes NULLS NOT DISTINCT — PostgreSQL accepted duplicate nulls MongoDB refused; D-966, D-967" — matches `git log --grep`.
Next step       : W-407d (port audit: portfolio vs m1.6.1 — table shape as plans/W-407c.md; plus each unique index over a nullable
  column: NULLS NOT DISTINCT unless m1.6.1's index was sparse, D-966; next migration V12). Then W-407e, then W-337g2a1 … g2c
  (after W-337g2 C2 – C5). Check the pipeline run of this push first.
Flags for owner : W-337f flags unchanged (inert @Field, GazetteCursor created*, lease audit nulls, findByEmailAndTransactionDateBetween).
Env notes       : NO Claude attribution; integration needs OrbStack + DOCKER_HOST=unix://$HOME/.orbstack/run/docker.sock; full tiers on the
  pipeline only. Tests run in UTC (argLine); time tests pin a fixed Asia/Kolkata clock.
Updated         : 2026-11-25 (session 122, W-407c committed and pushed)
```
## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch -> stop and report both outputs. Match -> do **Next step** and nothing else.
