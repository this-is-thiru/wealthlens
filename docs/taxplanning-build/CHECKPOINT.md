# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-337a BUILT AND DONE (D-938), board D. Code+ADR+plan committed and pushed on branch w-337 (7136554e); on review/taxplanning now for the bookkeeping docs. W-337h: prod signed off, dev/staging rows PARKED (unchanged)
Stage           : prod and staging on m1.5 (e832b56a, W-362, D-869 – D-871), signed off; app stopped; worktrees m1.4 and m1.5 on disk; MongoDB tools REMOVED (reads via the owner's IntelliJ console)
Done session 118: W-337 split (D-928) + D-929 pushed (e728dd22); engine/host pivoted three times for prod — Oracle → Aiven → Azure SQL → Neon (D-930, D-931, D-933, D-935); D-936: C1 resolved (prod + dev on Neon, staging on Aiven), W-400 answered; W-337h prod signed off (rows 0, 1, 2 – 3, 5, 6; dev/staging PARKED); D-937: W-337a re-planned for Postgres 18, approved C2a C3a, scope narrowed to parallel entities only; D-938: W-337a built and done — C1 of D-928 closed (docs/adr/W-337a.md), PostgresMappingProbeIntegrationTest 6/6, unit tier 5091/5091, validate clean; committed and pushed on branch w-337 (7136554e)
Board           : W-337h P  W-337h1 —  W-337h2 —; W-337a D; W-337b - j, W-399, W-400 ·; W-380 - W-398 · (gap rows); W-337 -
Next step       : commit this file's own four docs (DECISIONS/STATE/03-WORK-ITEMS/CHECKPOINT) on review/taxplanning, push; then plan W-337b (the schema, Flyway baseline for all 40 collections)
Open to owner   : the live Azure server/login — delete whenever, not urgent; W-390 (1) order; W-388 (1) wording; order of W-385 (1)/(2), W-386 (1), W-381 (1), W-382 (1); W-387; W-382 (6); W-383, W-373; W-385 (6); whether to open a PR
Env notes       : NO Claude attribution anywhere; integration tier needs OrbStack (orb start) + DOCKER_HOST=unix://$HOME/.orbstack/run/docker.sock; a full tier only on the pipeline; W-337's code lives on branch w-337 (D-928 C2a), not review/taxplanning, until W-337i merges it
Last verify     : PostgresMappingProbeIntegrationTest 6/6 (Docker, Postgres 18 + Mongo 8.0.4 containers); unit tier 5091/5091 incl. ArchitectureTest 20/0, WealthLensModulithTest 1/0; ./mvnw validate clean — all on branch w-337
Last item commit: taxplanning: update CHECKPOINT.md and STATE.md after W-337a's build (D-938)
Files touched   : none — all committed (on review/taxplanning, after this commit; not pushed until the push step)
Updated         : 2026-11-23, session 118 (W-337a built and done, D-938)
```
## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch -> stop and report both outputs. Match -> do **Next step** and nothing else.
