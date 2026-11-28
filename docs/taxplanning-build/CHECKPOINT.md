# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-426 DONE (D-1033), committed and pushed. Its full tiers are on PR #86's run.
Stage           : pushed to w-337; PR #86 reruns with W-423 – W-426 code.
Last item commit: "taxplanning W-426: build the store by Spring Boot's auto-configuration — the hand-wired data source, entity manager, transaction manager and package lists from the dual-store era removed; 89 qualified @Transactional plain; D-1032, D-1033"
Next step       : 1. gh run list --branch w-337 --limit 1 → green → ask "merge #86"; red → read the failure, report, R13 if W-426's plan is wrong. 2. 2026-11-29: §1.h rerun → log → remove Next-deploy line 1. 3. W-337j4 (owner, due 2026-12-05).
Files touched   : (none); api-collection Bruno edits — never commit
Flags for owner : W-337j4 (delete the 3 Atlas clusters) due 2026-12-05. W-418 needs the broker statement. Same-day FIFO tie in sales proposed, not placed (D-1010). W-416 waits. §1.s Aiven retention not given.
Env notes       : NO Claude attribution; ask "commit and push" explicitly; OrbStack: `orb start`, DOCKER_HOST=unix://$HOME/.orbstack/run/docker.sock; full tiers on pipeline only; SQL via postgres:18 psql.
  Jars: m1.8 ../wealthlens-m1.8-pg/backend/target. env files ~/wealthlens-runs/{dev,staging,prod}.env (600) still hold MONGO_* (W-337j3d). No app running.
Updated         : 2026-11-28 (session 127)
```
## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch -> stop and report both outputs. Match -> do **Next step** and nothing else.
