# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-337i (the moves, D-991): i1 DONE (m1.7), i2 DONE (dev on PG), i1 again DONE (m1.7.1, D-997). W-414, W-415 DONE.
Stage           : m1.7.1 = tag 08e111e6 → master 9dcc9394, signed off, close-out committed and pushed.
Last item commit: "taxplanning W-337i1: release m1.7.1 — PR #75 merged, tagged on 9dcc9394, jar built, WL-A-2 published; signed off; D-997"
Next step       : Plan W-337i3 (rehearsal, D-991 C3a) from ../wealthlens-m1.7.1-pg. No PR open on w-337: a push runs no pipeline.
Files touched   : (none); api-collection has Bruno's own .DS_Store + Local - Staging.yml edits — never commit them
Flags for owner : §1.h count due 2026-11-29 (staging/prod still Mongo: console; dev on PG). §1.s Aiven retention not given. W-416 (retire V1) waits for the owner.
Env notes       : NO Claude attribution; OrbStack stops on reboot: `orb start`, DOCKER_HOST=unix://$HOME/.orbstack/run/docker.sock; full tiers on pipeline only.
  Owner values: ~/wealthlens-runs/dev.env (600, Mongo + PG + keys); staging.env/prod.env lack PG vars yet. No app running. Jars: m1.7.1 ../wealthlens-m1.7.1-pg/backend/target, m1.7
  ../wealthlens-m1.7/backend/target, m1.6.1 ../wealthlens-m1.6.1/backend/target. No pipeline runs on a w-337 push without an open PR.
Updated         : 2026-11-27 (session 125)
```
## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch -> stop and report both outputs. Match -> do **Next step** and nothing else.
