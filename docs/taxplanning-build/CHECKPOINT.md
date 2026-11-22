# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-357b10a DONE (D-924), committed (not yet pushed); next W-357b10b (taxplanning doc part 2)
Stage           : prod and staging on m1.5 (e832b56a, W-362, D-869 – D-871), signed off; app stopped; worktrees m1.4 (way back) and m1.5 on disk; MongoDB tools REMOVED (reads via the owner's IntelliJ console, one $documents query; check the "database" field)
Done session 114: W-360, W-361, W-206, W-359b, W-362 (m1.5 deployed), W-358d split + d1 – d4, W-364, Phase 15 batch of ten (D-864 – D-887); W-363 parked, W-365 placed; D-888 three deployments
Board           : W-357b10 —, W-357b10a D, W-357b10b – d ·, W-380 – W-395 · (gap rows), W-371 – W-373 ·, W-337 ·
Next step       : W-357b10b: taxplanning module doc part 2 (assemble/, portfolio/, compute/; 50 files, 3,628 lines, 19 routes, 1 collection; re-home W-089's 3 tests to taxengine.md §11 plus W-088g, W-088g2, W-088g9). §1.h count due 2026-11-29
Open to owner   : W-390 (1) order; W-388 (1) wording and order; order of W-385 (1), (2), W-386 (1), W-381 (1), W-382 (1); W-387; W-382 (6); W-383, W-373; W-385 (6); whether to push W-357b10a; whether to open a PR
Env notes       : NO Claude attribution anywhere; integration tier needs OrbStack (orb start) + DOCKER_HOST=unix://$HOME/.orbstack/run/docker.sock; a full tier only on the pipeline
Last verify     : W-357b10a: 171 path:line citations checked (0 errors), 31/31 routes matched; ./mvnw validate exit 0
Last item commit: taxplanning W-357b10a: write the taxplanning module doc's boundary, profiles, workspaces, drafts and salary import — and place eight gaps as W-395
Files touched   : none — all committed
Updated         : 2026-11-22, session 117 (W-357b10a committed)
```
## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
