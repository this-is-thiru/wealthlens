# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-417 DONE (D-1000), committed + pushed. W-337i3 stopped at row 6, resumes at row 5 on m1.7.2. W-418 placed (after the move).
Stage           : m1.7.2 release = W-337i1 again: PR w-337 → master to open, its run, merge on owner's word, tag, worktree, jar, WL-A-2. Containers UP (C2a).
Last item commit: "taxplanning W-417: read prod's old data in the copier as m1.6.1 read it — retired keys left out, pre-#33 dates read at UTC, each counted; prod's copy refused all three; D-997 – D-1000"
Next step       : 1. PR w-337 → master, its run (red → read, report). 2. Green: owner's word → merge --match-head-commit, master run, tag m1.7.2, worktree ../wealthlens-m1.7.2-pg + jar, WL-A-2, log rows as "W-337i1 again". 3. W-337i3 plan: J17 → m1.7.2 jar; owner runs DROP SCHEMA wealthlens CASCADE; CREATE SCHEMA wealthlens; then rows 5 – 14.
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
