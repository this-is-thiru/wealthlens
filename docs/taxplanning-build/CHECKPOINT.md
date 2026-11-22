# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-357b9b DONE (D-917), committed and pushed; next W-357b9b1 (approved: re-home the nodes' 20 test plans)
Stage           : prod and staging on m1.5 (e832b56a, W-362, D-869 – D-871), signed off; app stopped; worktrees m1.4 (way back) and m1.5 on disk; MongoDB tools REMOVED (reads via the owner's IntelliJ console, one $documents query; check the "database" field)
Done session 114: W-360, W-361, W-206, W-359b, W-362 (m1.5 deployed), W-358d split + d1 – d4, W-364, Phase 15 batch of ten (D-864 – D-887); W-363 parked, W-365 placed; D-888 three deployments
Board           : W-357b9 —, W-357b9a – b D, W-357b9b1 ·, W-357b9c – f ·, W-357b10 ·, W-380 – W-390 · (gap rows), W-371 – W-373 ·, W-337 ·
Next step       : W-357b9b1: for each of the 20 plans cited by taxengine/node + golden tests, move what the test relies on into taxengine.md or the test, re-point, delete the plan if nothing outside history cites it (W-089 waits for W-357b10, W-112b for W-357b9e). §1.h count due 2026-11-29
Open to owner   : W-390 (1) order; W-388 (1) wording and order; order of W-385 (1), (2), W-386 (1), W-381 (1), W-382 (1); W-387; W-382 (6); W-383, W-373; W-385 (6); whether to open a PR
Env notes       : NO Claude attribution anywhere; integration tier needs OrbStack (orb start) + DOCKER_HOST=unix://$HOME/.orbstack/run/docker.sock; a full tier only on the pipeline
Last verify     : W-357b9b: citations checked; worked figures match DR 08 T1-N3, N4a, N4b; validate exit 0
Last item commit: taxplanning W-357b9b: write the taxengine module doc's nodes with a salary worked to DR 08's figure — and record the business head neither computed nor refused
Files touched   : none — all committed
Updated         : 2026-11-22, session 116 (W-357b9b committed)
```
## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
