# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : PAUSED (session 126). Done today: W-337i4 staging + W-337i5 prod moved (D-1012, D-1014), W-421, W-422, W-337j1 (WL-50 Done), W-337j2 (Mongo client gone), W-337j3a; m1.7.5, m1.7.6, m1.8 released; staging + prod run m1.8 with no MONGO_*.
Stage           : All committed + pushed on w-337 (pause commit last). PR #86 (docs) OPEN — check its run, then owner's "merge #86".
Last item commit: "taxplanning W-337j3a: retire the MongoDB-era one-time runbook steps — the open cleanups moot on PostgreSQL; m1.8 released; W-337j3 split; D-1019, D-1020"
Next step       : 1. gh run list --branch w-337 --limit 1 (red → read, report); green → ask "merge #86". 2. W-337i6 (approved D-1015): owner runs row 2 (archives rm) + row 4 (DROP DATABASE wealthlens on Neon) — commands in plans/W-337i6.md; log; close W-337i6. 3. §1.h counts on staging + prod in SQL (due 2026-11-29; query in runbook "Next deploy" line 1). 4. "start W-337j3b" → plan (14 sections, ~74 Mongo lines → SQL, each proven read-only on dev); STOP.
Files touched   : (none); api-collection Bruno edits — never commit
Flags for owner : W-337j4 (delete the 3 Atlas clusters) due 2026-12-05. W-418 (pre-#33 trade date) needs the broker statement. Same-day FIFO tie in sales (findEligibleHoldingsForSell) proposed, not placed (D-1010). W-416 (retire V1) waits. §1.s Aiven retention not given.
Env notes       : NO Claude attribution; ask "commit and push" explicitly (approval alone was blocked); OrbStack: `orb start`, DOCKER_HOST=unix://$HOME/.orbstack/run/docker.sock; full tiers on pipeline only; SQL via postgres:18 psql (PSQL() in plans/W-337i4.md / i5.md).
  Jars: m1.8 ../wealthlens-m1.8-pg/backend/target (115,408,953 B, sha 8440d4c56b20…), m1.7.6 ../wealthlens-m1.7.6-pg, m1.6.1 ../wealthlens-m1.6.1. env files ~/wealthlens-runs/{dev,staging,prod}.env (600) hold PG values + still MONGO_* (W-337j3d removes). No app running.
Updated         : 2026-11-28 (session 126) — paused
```
## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch -> stop and report both outputs. Match -> do **Next step** and nothing else.
