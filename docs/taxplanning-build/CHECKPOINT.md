# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-402 DONE — D-960 (close-out), D-958/D-959 (choices); board D; committed and pushed.
Stage           : 6 — DONE. Nothing in flight.
Last item commit: "taxplanning W-402: store every time in UTC, run the JVM in UTC and take today from app.time.zone — stored times were mixed local and UTC; D-958, D-959, D-960" — matches `git log --grep`.
Next step       : plan W-404 (order_execution_time column types; before W-337g2), then W-337g2 (written-zone = Asia/Kolkata, JSON columns *_json),
  then W-403 (wire format with Z + published zone). Check the pipeline run of this push first.
Flags for owner : W-337f flags unchanged (inert @Field, GazetteCursor created*, lease audit nulls, findByEmailAndTransactionDateBetween).
Env notes       : NO Claude attribution; integration needs OrbStack + DOCKER_HOST=unix://$HOME/.orbstack/run/docker.sock; full tiers on the
  pipeline only. Tests run in UTC (argLine); time tests pin a fixed Asia/Kolkata clock.
Updated         : 2026-11-25 (session 122, W-402 committed and pushed)
```
## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch -> stop and report both outputs. Match -> do **Next step** and nothing else.
