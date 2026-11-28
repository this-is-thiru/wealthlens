# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-435 DONE (D-1052), committed and pushed with tag docs-history-m5. Tracking is now BACKLOG.md (open items only) + this file + DECISIONS.md.
Stage           : #87 open; check its run on the latest commit, then ask "merge #87".
Last item commit: "taxplanning W-435: track only open items in BACKLOG.md, in five new phases — the status board, work items, parked register and the runbook's extras into tag docs-history-m5; D-1051, D-1052"
Current position: dev, staging, prod on m1.9, all personal data plain (W-428b). On w-337, not yet released: W-428c (encryption removed, Flyway V14), W-430 – W-435 (clean-up). Before the next release starts on each database: runbook §1.1.
Next step       : 1. #87 green → "merge #87" → release → runbook §1.1 per database. 2. 2026-11-29: runbook §1.2 count. 3. W-337j4 due 2026-12-05. 4. BACKLOG.md Phase 1, W-385 first; W-384 can close (done by W-430) on owner's word.
Files touched   : (none). api-collection committed properly at 3b99d5e (token variables empty, filled by Login); never commit a token value
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
