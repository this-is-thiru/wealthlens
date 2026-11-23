# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-337d2 done (D-947, D-948, board D — completes W-337d); W-337e (portfolio) next; W-337h prod signed off on Neon, dev & staging parked (board P)
Stage           : READY — nothing in flight on branch w-337 (prod & staging on m1.5 e832b56a, signed off, app stopped)
Done session 119: W-337b (74851e16), W-337c1 (2f9ff23d), W-337c2 (8cfc6d50), W-337d1 (4e53ca8e), W-337d2 (brokercharges — validate clean, 117/117 unit/arch, all 13 integration test classes green, D-947/D-948) committed & pushed on branch w-337
Board           : W-337a D  W-337b D  W-337c —  W-337c1 D  W-337c2 D  W-337d —  W-337d1 D  W-337d2 D  W-337e - g ·  W-337h P  W-337h1 —  W-337h2 —  W-337i - j ·  W-399 ·  W-400 ·; W-380 - W-398 ·; W-337 —
Next step       : Plan W-337e (port portfolio — assets, transactions, trade_outcomes, profit_and_loss, TradeCollisionRetry, PortfolioIndexInitializer) — Stage 2 EXPLAIN (size first, probe before plan, five roles, board E, STOP for approval)
Open to owner   : live Azure server/login delete whenever; W-337h parked rows (Neon dev branch, staging/dev rows 5–6); W-390 (1), W-388 (1), W-385 (1)/(2), W-386 (1), W-381 (1), W-382 (1)/(6), W-387, W-383, W-373
Env notes       : NO Claude attribution anywhere; integration tier needs OrbStack (orb start) + DOCKER_HOST=unix://$HOME/.orbstack/run/docker.sock; a full tier only on the pipeline
Last verify     : ./mvnw validate 0 violations; unit/arch 117/117; all 13 integration test classes green against Postgres 18 + Mongo 8.0.4 Testcontainers
Last item commit: taxplanning W-337d2: port brokercharges module to JPA with V8 DDL — D-947, D-948
Files touched   : none — all committed
Updated         : 2026-11-23 (W-337d2 closed out, committed and pushed on w-337; W-337d complete; next W-337e)
```
## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch -> stop and report both outputs. Match -> do **Next step** and nothing else.
