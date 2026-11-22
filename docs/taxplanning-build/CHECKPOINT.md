# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-370 DONE (D-900), committed and pushed with D-899's order; next W-357b
Stage           : prod and staging on m1.5 (e832b56a, W-362, D-869 – D-871), signed off; app stopped; worktrees m1.4 (way back) and m1.5 on disk; MongoDB tools REMOVED (reads via the owner's IntelliJ console, one $documents query; check the "database" field)
Done session 114: W-360, W-361, W-206, W-359b, W-362 (m1.5 deployed), W-358d split + d1 – d4, W-364, Phase 15 batch of ten (D-864 – D-887); W-363 parked, W-365 placed; D-888 three deployments
Board           : W-370 D, W-357b ·, W-337 ·, W-356 ·, W-355 ·, W-351 ·, W-367 — (not taken, D-899)
Next step       : plan W-357b's first module (smallest: finance or insurance) to settle the doc format with the owner → the other modules → W-337. No PR is open: the next PR into master carries ec0f341e onward. §1.h week-after count due 2026-11-29
Open to owner   : none
Env notes       : NO Claude attribution anywhere; integration tier needs OrbStack (orb start) + DOCKER_HOST=unix://$HOME/.orbstack/run/docker.sock; a full tier only on the pipeline
Last verify     : W-370 green locally (ControllerAdviser 28, EmailLogMask 4, RequestId 3, CallerMistake 24, ArchitectureTest 26, Modulith 1)
Last item commit: taxplanning W-370: log a caller's mistake at WARN and only a failure at ERROR — four deliberate refusals were counted as errors at the deploy
Files touched   : none — all committed (this file is in the commit)
Updated         : 2026-11-22, session 115 (W-370 committed and pushed)
```
## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
