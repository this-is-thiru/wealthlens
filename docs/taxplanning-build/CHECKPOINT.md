# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-357b6a DONE (D-908, split D-907), committed and pushed; next W-357b6b (retire docs/charges-engine/ and the W-264b review)
Stage           : prod and staging on m1.5 (e832b56a, W-362, D-869 – D-871), signed off; app stopped; worktrees m1.4 (way back) and m1.5 on disk; MongoDB tools REMOVED (reads via the owner's IntelliJ console, one $documents query; check the "database" field)
Done session 114: W-360, W-361, W-206, W-359b, W-362 (m1.5 deployed), W-358d split + d1 – d4, W-364, Phase 15 batch of ten (D-864 – D-887); W-363 parked, W-365 placed; D-888 three deployments
Board           : W-357b6 —, W-357b6a D, W-357b6b ·, W-357b7 – b10 ·, W-380 – W-382 · (gap rows), W-371 – W-373 ·, W-337 ·
Next step       : W-357b6b: re-point every citation of docs/charges-engine/ to docs/modules/brokercharges.md or a D-row, move what only the old design holds, delete the folder and reviews/2026-11-13-charge-engine-gap-audit.md. §1.h week-after count due 2026-11-29
Open to owner   : W-381 (1) order; W-382 (1) (/simulate reads email from the body) order; W-382 (6) = W-264b Q-2 AMC span ruling; W-373 build or remove; whether to open a PR
Env notes       : NO Claude attribution anywhere; integration tier needs OrbStack (orb start) + DOCKER_HOST=unix://$HOME/.orbstack/run/docker.sock; a full tier only on the pipeline
Last verify     : W-357b6a: every doc citation printed against its line; ./mvnw validate exit 0
Last item commit: taxplanning W-357b6a: write the brokercharges module doc from the code and re-point its gap test — and record a /me route that reads the user from the body
Files touched   : none — all committed
Updated         : 2026-11-22, session 116 (W-357b6a committed)
```
## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
