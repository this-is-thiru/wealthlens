# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-419 DONE (D-1003), committed + pushed. W-337i3 stopped at row 10, resumes at row 5 on m1.7.3.
Stage           : PR for w-337 → check its run; then m1.7.3 (W-337i1 again). Containers + both apps UP (8081 pid 14757, 8082 pid 14758); w406/before,after,users.txt kept.
Last item commit: "taxplanning W-419: answer every read on prod's copy as m1.6.1 did — old transactions' zone copied, holdings' lots oldest first, no leaked empty flag; the tool compares holdings by holding and orders what neither build fixes; row 10 found 6496 differences; D-1001 – D-1003"
Next step       : 1. gh run for the PR (red → read, report). 2. Green: owner's word → merge, master run, tag m1.7.3, worktree ../wealthlens-m1.7.3-pg + jar, WL-A-2, log rows. 3. W-337i3: J17 → m1.7.3; stop 8082 app; DROP SCHEMA wealthlens CASCADE; CREATE SCHEMA; rows 5 – 14.
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
