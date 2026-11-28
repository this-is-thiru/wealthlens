# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-337j1 BUILT (D-1017), committed + pushed, PR open (owner's word); board R until merged. W-337i6 APPROVED (D-1015): row 3 done; rows 2 + 4 pastes awaited.
Stage           : Close-out committed and pushed on w-337, PR to master opened (owner's word); its run to check. Empty Neon database `wealthlens` left (removal = later decision).
Last item commit: "taxplanning W-337j1: retire the copier and the read comparison — every database moved and compared; W-337i6 and W-337j planned; D-1015 – D-1017"
Next step       : 1. PR run → green → owner's "merge"; then W-337j1 D (close-out commit) and WL-50 closed on the owner's word. 2. Rows 2 + 4 pastes → log, close W-337i6. 3. §1.h counts on/after 2026-11-29. 4. W-337j2 plan.
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
