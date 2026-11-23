# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-337b done (D-939, D-940, board D); W-337a done (7136554e, D-937, D-938, board D); W-337h prod signed off on Neon, dev & staging parked (board P)
Stage           : READY — nothing in flight on branch w-337 (prod & staging on m1.5 e832b56a, signed off, app stopped)
Done session 119: W-337b closed out, committed and pushed on branch w-337 (V2–V5 across 4 module folders + FlywaySchemaBaselineIntegrationTest, D-938 – D-940 recorded in DECISIONS.md, STATE.md board W-337a D / W-337b D)
Board           : W-337a D  W-337b D  W-337c - g ·  W-337h P  W-337h1 —  W-337h2 —  W-337i - j ·  W-399 ·  W-400 ·; W-380 - W-398 ·; W-337 —
Next step       : Plan W-337c (port shared, auth, insurance to JPA on branch w-337) — Stage 2 EXPLAIN (size first, probe before plan, five roles, board E, STOP for approval)
Open to owner   : live Azure server/login delete whenever; W-337h parked rows (Neon dev branch, staging/dev rows 5–6); W-390 (1), W-388 (1), W-385 (1)/(2), W-386 (1), W-381 (1), W-382 (1)/(6), W-387, W-383, W-373
Env notes       : NO Claude attribution anywhere; integration tier needs OrbStack (orb start) + DOCKER_HOST=unix://$HOME/.orbstack/run/docker.sock; a full tier only on the pipeline
Last verify     : per plans/W-337b.md (session 118): FlywaySchemaBaselineIntegrationTest 2/2, PostgresMappingProbeIntegrationTest 6/6, unit tier 5085/5085, ./mvnw validate clean
Last item commit: taxplanning W-337b: lay out per-module Flyway baseline DDL (V2–V5) — D-939, D-940
Files touched   : none — all committed
Updated         : 2026-11-23 (W-337b closed out, committed and pushed on w-337; next W-337c)
```
## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch -> stop and report both outputs. Match -> do **Next step** and nothing else.
