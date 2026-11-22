# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-366 DONE (D-894 – D-897), W-368 DONE (D-898): staging and prod on m1.6.1, signed off; close-out committed and pushed
Stage           : prod and staging on m1.5 (e832b56a, W-362, D-869 – D-871), signed off; app stopped; worktrees m1.4 (way back) and m1.5 on disk; MongoDB tools REMOVED (reads via the owner's IntelliJ console, one $documents query; check the "database" field)
Done session 114: W-360, W-361, W-206, W-359b, W-362 (m1.5 deployed), W-358d split + d1 – d4, W-364, Phase 15 batch of ten (D-864 – D-887); W-363 parked, W-365 placed; D-888 three deployments
Board           : W-366 D, W-368 D, W-369 D, W-370 · (proposal), W-357b ·, W-367 ·, W-356 ·, W-355 ·, W-351 ·, W-337 ·
Next step       : check the close-out's pipeline run; then plan W-357b's first module (smallest: finance or insurance) unless the owner puts W-370 or m1.7 first. §1.h week-after count due 2026-11-29
Open to owner   : W-370's place in the order; W-355 / W-356 in m1.7 (D-888) or after W-337 (their rows)
Env notes       : NO Claude attribution anywhere; integration tier needs OrbStack (orb start) + DOCKER_HOST=unix://$HOME/.orbstack/run/docker.sock; a full tier only on the pipeline
Last verify     : prod and staging on m1.6.1 signed off (W-366b / c rows 1 – 7); master 8dbb4cf0 run 44982257584 green
Last item commit: taxplanning W-366: close out the m1.6.1 deploy — staging and prod signed off, §2.18 retired (W-368), the next-deploy list cleared
Files touched   : none — all committed (this file is in the commit)
Updated         : 2026-11-22, session 115 (m1.6.1 deploy closed out)
```
## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
