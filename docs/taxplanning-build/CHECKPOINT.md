# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-428b DONE (D-1038), committed and pushed. dev, staging, prod on m1.9, all plain. Next: plan W-428c (encryption, IdentifierDigest + digest columns, the two keys, the rewrite; WL-51) — EXPLAIN template, STOP for approval.
Stage           : pushed to w-337 (no PR open; #86 merged). Runbook republished to WL-A-2.
Last item commit: "taxplanning W-428b: record the plain-text rewrite on dev, staging and prod with m1.9 — every database plain, the old database setting names out of the env files; D-1038"
Next step       : 1. plan W-428c. 2. 2026-11-29: §1.h rerun ("Next deploy" line 1). 3. W-337j4 due 2026-12-05.
Files touched   : (none); api-collection — never commit
Flags for owner : W-337j4 (delete the 3 Atlas clusters) due 2026-12-05. W-418 needs the broker statement. Same-day FIFO tie in sales proposed, not placed (D-1010). W-416 waits. §1.s Aiven retention not given (staging's only way back to m1.8 now). Keep tax keys while dev/prod pre-w428a dumps are kept.
Env notes       : NO Claude attribution; ask "commit and push" explicitly; OrbStack: `orb start`, DOCKER_HOST=unix://$HOME/.orbstack/run/docker.sock; full tiers on pipeline only; SQL via postgres:18 psql. Owner's zsh: no inline # comments.
  Jars: m1.9 ../wealthlens-m1.9-pg/backend/target (all three run it). env files ~/wealthlens-runs/{dev,staging,prod}.env (600): DATABASE_*, still MONGO_* (W-337j3d). No app running.
Updated         : 2026-11-28 (session 128)
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch -> stop and report both outputs. Match -> do **Next step** and nothing else.
