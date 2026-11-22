# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : D-904 and W-357b4 (D-905) committed and pushed; next W-357b5 corporate
Stage           : prod and staging on m1.5 (e832b56a, W-362, D-869 – D-871), signed off; app stopped; worktrees m1.4 (way back) and m1.5 on disk; MongoDB tools REMOVED (reads via the owner's IntelliJ console, one $documents query; check the "database" field)
Done session 114: W-360, W-361, W-206, W-359b, W-362 (m1.5 deployed), W-358d split + d1 – d4, W-364, Phase 15 batch of ten (D-864 – D-887); W-363 parked, W-365 placed; D-888 three deployments
Board           : W-357b —, W-357b1 – b3 D, W-357b4 – b10 ·, W-371 – W-373 · (proposals), W-337 ·, W-356 ·, W-355 ·, W-351 ·
Next step       : W-357b5 (corporate) in the same template. No PR open (27b20d09 onward untested by the pipeline). §1.h week-after count due 2026-11-29
Open to owner   : W-373 build or remove; the gap rows' order (W-371 – W-373, W-380); whether to open a PR
Env notes       : NO Claude attribution anywhere; integration tier needs OrbStack (orb start) + DOCKER_HOST=unix://$HOME/.orbstack/run/docker.sock; a full tier only on the pipeline
Last verify     : W-370 green locally (ControllerAdviser 28, EmailLogMask 4, RequestId 3, CallerMistake 24, ArchitectureTest 26, Modulith 1)
Last item commit: taxplanning W-357b4: write the auth module doc — zones, sign-in, both tokens and the guessing limits, from the code
Files touched   : none — all committed
Updated         : 2026-11-22, session 115 (D-904 and W-357b4 committed)
```
## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
