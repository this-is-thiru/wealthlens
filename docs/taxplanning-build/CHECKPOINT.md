# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-337h approved C2a C3a (D-936), C1 resolved as a mixed shape, board P; **prod signed off** (rows 0, 1, 2 – 3, 5, 6 done); row 4 (dev branch) and staging's/dev's share of 5 – 6 PARKED by owner ("park for later" / "we ca do remaining later")
Stage           : prod and staging on m1.5 (e832b56a, W-362, D-869 – D-871), signed off; app stopped; worktrees m1.4 and m1.5 on disk; MongoDB tools REMOVED (reads via the owner's IntelliJ console)
Done session 118: W-337 split (D-928) + D-929 pushed (e728dd22); engine/host pivoted three times — Oracle (D-930, not taken) → Aiven (D-931, stopped row 1) → Azure SQL (D-933/D-934, stopped row 3, live server/login not torn down) → Neon (D-935); D-936: C1 resolved — prod + dev on Neon, staging on the Aiven service from D-931/D-932; W-400 answered, not separate; prod signed off; committed a8d1d1ab (not pushed)
Board           : W-337h P  W-337h1 —  W-337h2 —; W-337a ·; W-337b - j, W-399, W-400 ·; W-380 - W-398 · (gap rows); W-337 -
Next step       : prod done and signed off: row 0 Aiven staging check (wealthlens-aaaaaw.i.aivencloud.com:18042, db defaultdb, user avnadmin, PostgreSQL 18, schema wealthlens exists — staging itself still not built further than this); row 1 Neon production (direct host ep-holy-meadow-a1f4tyxx.ap-southeast-1.aws.neon.tech:5432, db wealthlens, user neondb_owner, sslmode=require, needed the SNI options=endpoint%3D<id> workaround for DataGrip); rows 2 – 3 (PostgreSQL 18.7, schema wealthlens created and confirmed); row 5 (production Idle, 0 CU-hours after 10 min); row 6 ("prod signed off"). PARKED: row 4 (create Neon branch `dev`), and dev's/staging's own share of rows 5 – 6. Resume at row 4 whenever ready; item's close-out (runbook §2.19 for both hosts, §1.s rows, STATE board, W-400 marked answered) waits until then
Open to owner   : commit of these edits; the live Azure server/login — delete whenever, not urgent; W-390 (1) order; W-388 (1) wording; order of W-385 (1)/(2), W-386 (1), W-381 (1), W-382 (1); W-387; W-382 (6); W-383, W-373; W-385 (6); whether to open a PR
Env notes       : NO Claude attribution anywhere; integration tier needs OrbStack (orb start) + DOCKER_HOST=unix://$HOME/.orbstack/run/docker.sock; a full tier only on the pipeline
Last verify     : none this session (planning only, no code/tests touched)
Last item commit: taxplanning W-337h: prod signed off on Neon; dev and staging parked
Files touched   : none — all committed (a8d1d1ab, not pushed)
Updated         : 2026-11-23, session 118 (Azure SQL dropped for Neon PostgreSQL, D-935; C1 resolved, Aiven for staging, D-936)
```
## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch -> stop and report both outputs. Match -> do **Next step** and nothing else.
