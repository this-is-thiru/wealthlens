# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-337i5 DONE (D-1014) — prod on PostgreSQL (Neon neondb), m1.7.6. All three databases moved. Next: W-337i6 (close), not started.
Stage           : Close-out committed and pushed on w-337, PR to master opened (owner's word); its run to check. Empty Neon database `wealthlens` left (removal = later decision).
Last item commit: "taxplanning W-337i5: prod signed off on m1.7.6 — copy clean, read comparison 0 differences, §1B, routes; schema made in neondb; D-1013, D-1014"
Next step       : 1. Check the PR's run; green → owner's "merge" (docs only). 2. On/after 2026-11-29: §1.h counts in SQL on staging and prod (C2a/C1a). 3. "start W-337i6" → plan the close (archives ~/wealthlens-runs/archives, Next-deploy lines 3 – 5, W-337j); STOP.
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
