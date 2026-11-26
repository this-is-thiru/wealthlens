# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-337g2c DONE — D-979; the W-337 copier complete. Committed and pushed.
Stage           : between items. WL-50 (under WL-28) opened; f77a5bf4's runbook repaired by 149308c9; WL-A-2 republished.
Last item commit: "taxplanning W-337g2c: test the copier's whole run and write its runbook entry — the move's copy, step by step; D-979" — matches `git log --grep`.
Next step       : check the pipeline; then W-337h (owner's database setup, status P, plans/W-337h.md) or plan W-406 (the rehearsal's
  side-by-side read comparison) — ask the owner which.
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
