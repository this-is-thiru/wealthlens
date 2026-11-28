# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-337i6 — built (D-1021), docs edited, NOT committed; awaiting owner "commit and push".
Stage           : rows 2 + 4 run by owner and logged; FinishedPlanTest 5/5 green; PR #86 run 46105067663 was in progress.
Last item commit: "taxplanning W-337j3a: retire the MongoDB-era one-time runbook steps — the open cleanups moot on PostgreSQL; m1.8 released; W-337j3 split; D-1019, D-1020"
Next step       : 1. On "commit and push": git add the 6 docs files below + this file && commit "taxplanning W-337i6: close the move — the W-334 archives deleted, prod's empty Neon database dropped; D-1021" && push. 2. gh run list --branch w-337 --limit 1 → green → ask "merge #86". 3. §1.h counts on staging + prod in SQL (due 2026-11-29; runbook "Next deploy" line 1). 4. "start W-337j3b" → plan; STOP.
Files touched   : docs/taxplanning-build/{03-WORK-ITEMS.md,DECISIONS.md,STATE.md,plans/W-337i6.md,post-deployment-runbook.md}; api-collection Bruno edits — never commit
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
