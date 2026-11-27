# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-337i3 DONE + W-420 DONE (D-1006). Next: W-337i4 (staging moves), not started.
Stage           : close-out committed + pushed (W-337i3, W-420 D); PR to check. No containers, no apps.
Last item commit: "taxplanning W-337i3: rehearsal signed off on m1.7.4 — rows 1 – 14, read comparison 0 differences; ci W-420 done — 5 m 22 s, red per tier; D-1006"
Next step       : 1. Owner's word → commit named paths + push (w-337; open PR, check run). 2. W-337i4: read plans/W-337i3.md + runbook §0.9/§0.10/§1B + D-991, write plans/W-337i4.md (staging: it-staging → Aiven wealthlens-staging), STOP.
Files touched   : (none); api-collection Bruno edits — never commit
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
