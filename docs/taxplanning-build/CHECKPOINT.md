# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-440 in progress (P): W-440a DONE (D-1054, approved C1a C2a D-1053). Next W-440b (registry on charge_instruments), then W-440c (parsers in portfolio).
Stage           : W-440a committed; W-440c waits on owner rulings C3 – C13 (plans/W-440.md §2, §3a, §3b).
Last item commit: "taxplanning W-440a: key an upload row on its trade and its occurrence in the file — an overlapping export recorded every shared trade again; D-1053, D-1054"
Current position: dev, staging, prod on m1.9. On w-337, not yet released: W-428c, W-430 – W-435, W-436 – W-440a. Before the next release on each database: runbook §1.1.
Next step       : 1. Plan W-440b (stage 2) — no ruling needed. 2. Ask C3 – C13. 3. #87 green → "merge #87" → release → runbook §1.1. 4. 2026-11-29: runbook §1.2 count. 5. W-337j4 due 2026-12-05.
Files touched   : (none). Broker samples only in the session scratchpad (personal data) — never in the repo.
Flags for owner : W-440 open gap: rows uploaded before W-440a + a later overlapping file still double (backfill or C8b). W-337j4 due 2026-12-05. W-418 needs the broker statement. W-416 waits.
Env notes       : NO Claude attribution; ask "commit and push" explicitly; OrbStack: `orb start`, DOCKER_HOST=unix://$HOME/.orbstack/run/docker.sock; full tiers on pipeline only; SQL via postgres:18 psql. Owner's zsh: no inline # comments.
  Jars: m1.9 ../wealthlens-m1.9-pg/backend/target (all three run it). env files ~/wealthlens-runs/{dev,staging,prod}.env (600). No app running.
Updated         : 2026-11-28 (session 128)
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch -> stop and report both outputs. Match -> do **Next step** and nothing else.
