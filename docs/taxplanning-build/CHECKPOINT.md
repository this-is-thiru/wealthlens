# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-404 not taken — D-961; board —; committed and pushed.
Stage           : 6 — DONE. Nothing in flight.
Last item commit: "taxplanning W-404: close as not taken — D-959 misread V9's tables; both order_execution_time columns already hold their value exactly; D-961" — matches `git log --grep`.
Next step       : plan W-337g2 (18 collections with embedded data: 12 child tables, 16 JSON columns named *_json; written-zone Asia/Kolkata;
  assets.order_execution_time an Instant, the other times UTC-held or local-held per D-954; runbook entry; removal ticket). Then W-403.
  Check pipeline runs 45650940814 (W-402) and this push first.
Flags for owner : W-337f flags unchanged (inert @Field, GazetteCursor created*, lease audit nulls, findByEmailAndTransactionDateBetween).
Env notes       : NO Claude attribution; integration needs OrbStack + DOCKER_HOST=unix://$HOME/.orbstack/run/docker.sock; full tiers on the
  pipeline only. Tests run in UTC (argLine); time tests pin a fixed Asia/Kolkata clock.
Updated         : 2026-11-25 (session 122, W-404 closed)
```
## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch -> stop and report both outputs. Match -> do **Next step** and nothing else.
