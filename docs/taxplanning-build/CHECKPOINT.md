# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-357b9d DONE (D-920), committed and pushed; next W-357b9e (rule packs and filing)
Stage           : prod and staging on m1.5 (e832b56a, W-362, D-869 – D-871), signed off; app stopped; worktrees m1.4 (way back) and m1.5 on disk; MongoDB tools REMOVED (reads via the owner's IntelliJ console, one $documents query; check the "database" field)
Done session 114: W-360, W-361, W-206, W-359b, W-362 (m1.5 deployed), W-358d split + d1 – d4, W-364, Phase 15 batch of ten (D-864 – D-887); W-363 parked, W-365 placed; D-888 three deployments
Board           : W-357b9 —, W-357b9a – d D, W-357b9e – f ·, W-357b10 ·, W-380 – W-392 · (gap rows), W-371 – W-373 ·, W-337 ·
Next step       : W-357b9e: rule packs and filing (rules/timeline, pack, json, validate, catalogue, coverage, registry, filing, generated) into docs/modules/taxengine.md; re-home the tests' plan citations (W-139a, W-079a, W-139, W-052c, W-055) and W-112b's pack-file citations. §1.h count due 2026-11-29
Open to owner   : W-390 (1) order; W-388 (1) wording and order; order of W-385 (1), (2), W-386 (1), W-381 (1), W-382 (1); W-387; W-382 (6); W-383, W-373; W-385 (6); whether to open a PR
Env notes       : NO Claude attribution anywhere; integration tier needs OrbStack (orb start) + DOCKER_HOST=unix://$HOME/.orbstack/run/docker.sock; a full tier only on the pipeline
Last verify     : W-357b9d: citations checked; validate exit 0
Last item commit: taxplanning W-357b9d: write the taxengine module doc's rules vocabulary — the closed families a pack is written in, and move W-048's relief figures into it
Files touched   : none — all committed
Updated         : 2026-11-22, session 116 (W-357b9d committed)
```
## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
