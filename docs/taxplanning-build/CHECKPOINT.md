# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-365 DONE (D-889), committed and pushed to PR #71 — check its pipeline run first
Stage           : prod and staging on m1.5 (e832b56a, W-362, D-869 – D-871), signed off; app stopped; worktrees m1.4 (way back) and m1.5 on disk; MongoDB tools REMOVED (reads via the owner's IntelliJ console, one $documents query; check the "database" field)
Done session 114: W-360, W-361, W-206, W-359b, W-362 (m1.5 deployed), W-358d split + d1 – d4, W-364, Phase 15 batch of ten (D-864 – D-887); W-363 parked, W-365 placed; D-888 three deployments
Board           : W-358d —, W-364 D, W-365 D, W-366 ·, W-367 ·, W-173 B, W-254 B, W-294 ·, W-299 ·, W-357b ·, W-351 ·, W-355 ·, W-356 ·, W-337 ·
Next step       : check the pipeline run for the W-365 commit (fix if red); then plan the batch W-294 + W-299 (check §2.13 / §2.14 in the run log at tag docs-history-m1 first), then W-173, W-254 → merge PR #71 on the owner's word → W-366 deploy m1.6. Each item: plan first, probe first
Open to owner   : none. PR #71 not to merge before the owner says so
Env notes       : NO Claude attribution anywhere; integration tier needs OrbStack (orb start) + DOCKER_HOST=unix://$HOME/.orbstack/run/docker.sock; a full tier only on the pipeline
Last verify     : W-365 green locally (CallerMistake 25, PortfolioService 37, ChargeSimulationService 31, FinancesIntegration 5, ApiCollectionContract 1, ArchitectureTest 26, Modulith 1, validate)
Last item commit: taxplanning W-365: refuse a trade, finance or preview body missing what it cannot do without — trades were stored half-empty or dated today, and three routes crashed
Files touched   : none — all committed (this file is in the item commit)
Updated         : 2026-11-22, session 115 (W-365 committed and pushed)
```
## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
