# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : m1.8 RELEASED (W-337j2 D, D-1018; tag → ce445f6f; jar ../wealthlens-m1.8-pg/backend/target 115,408,953 B). W-337j3a DONE (D-1020). Next: deploy m1.8 on staging + prod without MONGO_* (Next deploy line 2), then W-337j3b plan.
Stage           : Close-out docs for W-337j1 written, uncommitted (board D, plan ticked, D-1017 note).
Last item commit: "taxplanning W-337j3a: retire the MongoDB-era one-time runbook steps — the open cleanups moot on PostgreSQL; m1.8 released; W-337j3 split; D-1019, D-1020"
Next step       : 1. Owner starts m1.8 on staging and prod (block handed over), pastes; log; W-337j4's week starts. 2. Docs commit pushed; PR to check/merge. 3. W-337i6 rows 2 + 4. 4. §1.h counts on/after 2026-11-29. 5. W-337j3b plan.
Files touched   : (none); api-collection Bruno edits — never commit
Flags for owner : staging: target holds Flyway's empty tables (row 6 refused in prepare); it-staging untouched. §1.h count due 2026-11-29 (staging per C2; prod still Mongo: console). §1.s Aiven retention not given. W-416 (retire V1) waits for the owner. staging.env gains PG vars at row 2.
Env notes       : NO Claude attribution; OrbStack stops on reboot: `orb start`, DOCKER_HOST=unix://$HOME/.orbstack/run/docker.sock; full tiers on pipeline only; CI = 3 jobs, ~5½ min.
  Jars: m1.7.4 ../wealthlens-m1.7.4-pg/backend/target (120,973,903 B, sha 6270d07a6010…), m1.6.1 ../wealthlens-m1.6.1/backend/target (91,330,043 B).
Updated         : 2026-11-28 (session 126)
```
## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch -> stop and report both outputs. Match -> do **Next step** and nothing else.
