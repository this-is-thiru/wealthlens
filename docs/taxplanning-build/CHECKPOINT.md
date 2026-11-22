# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-357b10d DONE (D-927), committed and pushed — W-357b complete (every module doc written); next by the D-899 queue
Stage           : prod and staging on m1.5 (e832b56a, W-362, D-869 – D-871), signed off; app stopped; worktrees m1.4 (way back) and m1.5 on disk; MongoDB tools REMOVED (reads via the owner's IntelliJ console, one $documents query; check the "database" field)
Done session 114: W-360, W-361, W-206, W-359b, W-362 (m1.5 deployed), W-358d split + d1 – d4, W-364, Phase 15 batch of ten (D-864 – D-887); W-363 parked, W-365 placed; D-888 three deployments
Board           : W-357b10 —, W-357b10a – b D, W-357b10c D, W-357b10d D, W-380 – W-398 · (gap rows), W-371 – W-373 ·, W-337 ·
Next step       : ask the owner which queue item next (D-899 queue: gap rows W-371 – W-398 with owner-order questions open, then W-337 SQL, W-356 / W-355, W-351); §1.h count due 2026-11-29
Open to owner   : W-390 (1) order; W-388 (1) wording and order; order of W-385 (1), (2), W-386 (1), W-381 (1), W-382 (1); W-387; W-382 (6); W-383, W-373; W-385 (6); whether to open a PR
Env notes       : NO Claude attribution anywhere; integration tier needs OrbStack (orb start) + DOCKER_HOST=unix://$HOME/.orbstack/run/docker.sock; a full tier only on the pipeline
Last verify     : W-357b10d: 486 path:line citations in the doc in bounds (0 errors), part-four starts read (0 suspicious); 4/4 routes vs controllers, 2 lack api-collection requests (gap 26); TaxJobsIntegrationTest 21/0, TaxErasureIntegrationTest 5/0 (incl. validate)
Last item commit: taxplanning W-357b10d: write the taxplanning module doc's protection, erasure, jobs and Gazette watch — re-home W-137 and W-235, delete both plans, place W-398
Files touched   : none — all committed
Updated         : 2026-11-22, session 118 (W-357b10d committed and pushed)
```
## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
