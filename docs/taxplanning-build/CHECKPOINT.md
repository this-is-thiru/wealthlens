# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-337i3 (D-998) resuming at row 5 on m1.7.3 (W-419 done, D-1003).
Stage           : W-420 committed + pushed on w-337 (owner: "push") — cancels PR #78's run 46041253371; the new run tests W-419 R3 + W-420 together on the three-job pipeline. Apps up 20784/20785.
Last item commit: "taxplanning W-419: compare the holdings lists on what the view works out for the holding — sums within 1e-9, no field copied from a lot; row 10 on m1.7.3 found 138 such differences; D-1004"
Next step       : 1. #78 green → owner "merge and release" → m1.7.4, row 10 compare from it, rows 11 – 14. 2. Then commit W-420 on w-337 → PR: green run (counts = PR #77's 6888+26, gate ratios = its jacoco.xml), one red commit per tier, revert; owner's word to merge.
Files touched   : (none); api-collection — never commit
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
