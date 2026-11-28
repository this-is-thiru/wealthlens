# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-337j3b DONE (D-1023), committed and pushed (see Last item commit). W-337j3c approved (D-1024), building step 2.
Stage           : close-out edits made (plan ticked, D-1023, 03-WORK-ITEMS Built, board D); FinishedPlanTest run.
Last item commit: "taxplanning W-337j3b: the recurring and on-request runbook steps as SQL — every query proven on dev, staging and a throwaway copy; D-1022, D-1023"
Next step       : 1. W-337j3c step 2 (script, throwaway run, owner runs dev/staging/prod). 2. #86 run → green → ask "merge #86". 3. §1.h read 2026-11-28 (staging 14/1, prod 12/1) — log; ask: remove Next-deploy line 1 now or rerun 11-29.
Files touched   : docs/taxplanning-build/plans/W-337j3c.md (new); api-collection Bruno edits — never commit
Flags for owner : W-337j4 (delete the 3 Atlas clusters) due 2026-12-05. W-418 needs the broker statement. Same-day FIFO tie in sales proposed, not placed (D-1010). W-416 waits. §1.s Aiven retention not given.
Env notes       : NO Claude attribution; ask "commit and push" explicitly; OrbStack: `orb start`, DOCKER_HOST=unix://$HOME/.orbstack/run/docker.sock; full tiers on pipeline only; SQL via postgres:18 psql.
  Jars: m1.8 ../wealthlens-m1.8-pg/backend/target. env files ~/wealthlens-runs/{dev,staging,prod}.env (600) still hold MONGO_* (W-337j3d). No app running.
Updated         : 2026-11-28 (session 127)
```
## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch -> stop and report both outputs. Match -> do **Next step** and nothing else.
