# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-407d DONE — D-968 (V12 pnl NULLS NOT DISTINCT); board D; committed and pushed.
Stage           : 6 — DONE. Nothing in flight.
Last item commit: "taxplanning W-407d: audit portfolio, rebuild pnl_user_year_idx NULLS NOT DISTINCT — a second null financial year was accepted; D-968" — matches `git log --grep`.
Next step       : W-407e (port audit: taxplanning vs m1.6.1 — table shape as plans/W-407d.md; m1.6.1 had five taxplanning index
  initializers: read them; each unique index over a nullable column NULLS NOT DISTINCT unless sparse at m1.6.1 (D-966); next
  migration V13; extend FlywaySchemaBaselineIntegrationTest's null-duplicate test). Then W-337g2a1 … g2c (after W-337g2 C2 – C5).
  Check the pipeline run of this push first.
Flags for owner : W-337f flags unchanged (inert @Field, GazetteCursor created*, lease audit nulls, findByEmailAndTransactionDateBetween).
Env notes       : NO Claude attribution; integration needs OrbStack + DOCKER_HOST=unix://$HOME/.orbstack/run/docker.sock; full tiers on the
  pipeline only. Tests run in UTC (argLine); time tests pin a fixed Asia/Kolkata clock.
Updated         : 2026-11-25 (session 122, W-407d committed and pushed)
```
## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch -> stop and report both outputs. Match -> do **Next step** and nothing else.
