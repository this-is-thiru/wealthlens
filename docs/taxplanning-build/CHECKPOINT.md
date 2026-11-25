# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-407e DONE — D-969 (V13); the port audit W-407a – e complete; board D; committed and pushed.
Stage           : 6 — DONE. Nothing in flight.
Last item commit: "taxplanning W-407e: audit taxplanning, rebuild three unique indexes NULLS NOT DISTINCT — the port audit is complete; D-969" — matches `git log --grep`.
Next step       : owner answers W-337g2's C2 – C5 (plans/W-337g2.md); then build W-337g2a1 (refresh_tokens, insurances,
  privacy_breach_notices child tables). Check the pipeline run of this push first.
Flags for owner : W-337f flags unchanged (inert @Field, GazetteCursor created*, lease audit nulls, findByEmailAndTransactionDateBetween).
Env notes       : NO Claude attribution; integration needs OrbStack + DOCKER_HOST=unix://$HOME/.orbstack/run/docker.sock; full tiers on the
  pipeline only. Tests run in UTC (argLine); time tests pin a fixed Asia/Kolkata clock. Next Flyway version: V14.
Updated         : 2026-11-25 (session 122, W-407e committed and pushed)
```
## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch -> stop and report both outputs. Match -> do **Next step** and nothing else.
