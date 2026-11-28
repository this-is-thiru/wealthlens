# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-431 DONE (D-1044), committed and pushed. Next: plan W-432 (history archive into tag docs-history-m3).
Stage           : #87 green on f29935d3 (run 46120637286); W-431 adds a commit — check its run, then ask "merge #87".
Last item commit: "taxplanning W-431: correct the comments that still describe MongoDB, GridFS or the encryption — 37 lines in 28 files, reasons kept; D-1043, D-1044"
Next step       : 1. plan W-432 (EXPLAIN, STOP). 2. #87 run on the W-431 commit → "merge #87" → release → runbook §1.1 per database. 3. 2026-11-29: runbook §1.2 count. 4. W-337j4 due 2026-12-05.
Files touched   : (none); api-collection — never commit
Flags for owner : W-337j4 (delete the 3 Atlas clusters) due 2026-12-05. W-418 needs the broker statement. Same-day FIFO tie in sales proposed, not placed (D-1010). W-416 waits. §1.s Aiven retention not given (staging's only way back to m1.8 now). Keep tax key values (password manager) while dev/prod pre-w428a dumps are kept; §2.21 removes them from env files.
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
