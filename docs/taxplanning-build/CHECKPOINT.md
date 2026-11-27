# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : PAUSED after W-337i3 (rehearsal) + W-420 (CI speed) — both DONE (D-1006). Next: W-337i4 (staging moves), not started.
Stage           : All committed + pushed on w-337 (pause commit last). PR #80 (docs: close-out + pause) open; its run to check. m1.7.4 released (ccb7037d, tag 8aa6baf5).
Last item commit: "taxplanning W-337i3: rehearsal signed off on m1.7.4 — rows 1 – 14, read comparison 0 differences; ci W-420 done — 5 m 22 s, red per tier; D-1006"
Next step       : 1. gh run list --branch w-337 --limit 1 (red → read, report). 2. Green: owner's word → merge #80 (docs only, no release needed). 3. "start W-337i4" → read plans/W-337i3.md, runbook §0.9/§0.10/§1B, D-991; write plans/W-337i4.md (it-staging → Aiven wealthlens-staging, m1.7.4 jar); STOP.
Files touched   : (none); api-collection Bruno edits — never commit
Flags for owner : §1.h count due 2026-11-29 (staging/prod still Mongo: console). §1.s Aiven retention not given. W-416 (retire V1) waits for the owner. staging.env/prod.env lack PG vars (W-337i4 needs staging's).
Env notes       : NO Claude attribution; OrbStack stops on reboot: `orb start`, DOCKER_HOST=unix://$HOME/.orbstack/run/docker.sock; full tiers on pipeline only; CI = 3 jobs (unit, integration, gate), ~5½ min.
  Jars: m1.7.4 ../wealthlens-m1.7.4-pg/backend/target (the move's build), m1.6.1 ../wealthlens-m1.6.1/backend/target. No containers, no app running; rehearsal copies all removed.
Updated         : 2026-11-27 (session 125) — paused
```
## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch -> stop and report both outputs. Match -> do **Next step** and nothing else.
