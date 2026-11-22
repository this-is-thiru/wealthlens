# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-357b7c DONE (D-913), committed and pushed; W-357b7 complete; next W-357b8 (shared)
Stage           : prod and staging on m1.5 (e832b56a, W-362, D-869 – D-871), signed off; app stopped; worktrees m1.4 (way back) and m1.5 on disk; MongoDB tools REMOVED (reads via the owner's IntelliJ console, one $documents query; check the "database" field)
Done session 114: W-360, W-361, W-206, W-359b, W-362 (m1.5 deployed), W-358d split + d1 – d4, W-364, Phase 15 batch of ten (D-864 – D-887); W-363 parked, W-365 placed; D-888 three deployments
Board           : W-357b7 —, W-357b7a – c D, W-357b8 – b10 ·, W-380 – W-387 · (gap rows), W-371 – W-373 ·, W-337 ·
Next step       : W-357b8 (shared) in the same template — size it first (split if mis-sized, §7). §1.h count due 2026-11-29
Open to owner   : order of W-385 (1), (2) and W-386 (1); W-387 correct or not; W-381 (1); W-382 (1), (6); W-383, W-373 build or remove; W-385 (6); whether to open a PR
Env notes       : NO Claude attribution anywhere; integration tier needs OrbStack (orb start) + DOCKER_HOST=unix://$HOME/.orbstack/run/docker.sock; a full tier only on the pipeline
Last verify     : W-357b7c: validate exit 0; no live trade-ledger/ path left outside records
Last item commit: taxplanning W-357b7c: retire docs/trade-ledger and carry the design names the code cites into the portfolio doc — and place the B-8 data question left open
Files touched   : none — all committed
Updated         : 2026-11-22, session 116 (W-357b7c committed)
```
## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
