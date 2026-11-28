# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-422 DONE (D-1011), committed and pushed on w-337; PR to master opened (owner: "commit and push W-422, open the PR"). W-337i4 STOPPED at row 10 (D-1010), resumes at row 9 on m1.7.6.
Stage           : m1.7.5 released. Staging: copied on m1.7.5 (rows 3 – 9 go); row 10 compare 6 diffs (RELIANCE same-day lots). BOTH APPS STILL UP on 8081/8082 (pids ~/wealthlens-runs/w337i4/*.pid); before/ after/ kept in ~/wealthlens-runs/w406.
Last item commit: "taxplanning W-421: leave out the camelCase assetRequest of staging's old temporary, counted — m1.6.1 never read it; W-337i4 planned (D-1007), stopped at row 6; D-1008, D-1009"
Next step       : 1. Check the PR's run (gh run list --branch w-337 --limit 1). 2. Green → owner's "merge" → m1.7.6 (W-337i1 again). 3. Staging: kill 8082, start m1.7.6 on 8082 (row 9), rm -rf ~/wealthlens-runs/w406/before after, row 10 fetch both sides with m1.7.6's tool.
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
