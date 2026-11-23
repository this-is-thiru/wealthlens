# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-337c2 done (D-943, D-944, board D — completes W-337c); W-337d (brokercharges + corporate) next; W-337h prod signed off on Neon, dev & staging parked (board P)
Stage           : READY — nothing in flight on branch w-337 (prod & staging on m1.5 e832b56a, signed off, app stopped)
Done session 119: W-337b committed & pushed (74851e16); W-337c1 committed & pushed (2f9ff23d, D-941/D-942); W-337c2 built, verified (validate clean, 54/54 unit/arch, all 9 moving integration tests green), committed & pushed on branch w-337 (D-943/D-944)
Board           : W-337a D  W-337b D  W-337c —  W-337c1 D  W-337c2 D  W-337d - g ·  W-337h P  W-337h1 —  W-337h2 —  W-337i - j ·  W-399 ·  W-400 ·; W-380 - W-398 ·; W-337 —
Next step       : Plan W-337d (port brokercharges + corporate — 7 collections incl. 5 whose Flyway DDL was deferred from W-337b) — Stage 2 EXPLAIN (size first, probe before plan, five roles, board E, STOP for approval)
Open to owner   : live Azure server/login delete whenever; W-337h parked rows (Neon dev branch, staging/dev rows 5–6); W-390 (1), W-388 (1), W-385 (1)/(2), W-386 (1), W-381 (1), W-382 (1)/(6), W-387, W-383, W-373
Env notes       : NO Claude attribution anywhere; integration tier needs OrbStack (orb start) + DOCKER_HOST=unix://$HOME/.orbstack/run/docker.sock; a full tier only on the pipeline
Last verify     : ./mvnw validate 0 violations; unit/arch 54/54; all 9 moving integration tests green against Postgres 18 + Mongo 8.0.4 Testcontainers
Last item commit: taxplanning W-337c2: port shared privacy collections to JPA — D-943, D-944
Files touched   : none — all committed
Updated         : 2026-11-23 (W-337c2 closed out, committed and pushed on w-337; W-337c complete; next W-337d)
```
## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch -> stop and report both outputs. Match -> do **Next step** and nothing else.
