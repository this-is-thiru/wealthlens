# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-337i3 (D-998) resuming at row 5 on m1.7.3 (W-419 done, D-1003).
Stage           : W-419 Revision 3 committed + pushed (PR to open/check). W-337i3 rows 5 – 9 go on m1.7.3, row 10 stopped (138) then clean on the saved answers with the fixed tool. Apps up 20784/20785. W-420 (CI speed: single verify pass, split unit/integration jobs, no PR image) proposed, not placed — owner to answer.
Last item commit: "taxplanning W-419: compare the holdings lists on what the view works out for the holding — sums within 1e-9, no field copied from a lot; row 10 on m1.7.3 found 138 such differences; D-1004"
Next step       : 1. PR run (red → read). 2. Green: owner's word → merge, master run, tag m1.7.4, worktree ../wealthlens-m1.7.4-pg + jar, WL-A-2, log rows. 3. Row 10: compare only, tool from m1.7.4, on saved before/after → expect exit 0. 4. Rows 11 – 14.
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
