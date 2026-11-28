# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-428a DONE (D-1037), committed and pushed; WL-51 (removal, under WL-28). Next W-428b (owner runs §2.20 per database) after a release carrying W-428a; W-428c after all three.
Stage           : pushed to w-337; PR #86 reruns with W-428a. Ask "merge #86" when green.
Last item commit: "taxplanning W-428a: store personal data plain and read both forms, with a one-time rewrite of the stored ciphertext — sign-in needs the rewrite first; runbook §2.20, WL-51; D-1037"
Next step       : 1. gh run list --branch w-337 --limit 1 → green → ask "merge #86"; red → read, report. 2. publish runbook to WL-A-2 if not yet (tools/youtrack_publish_runbook.py). 3. 2026-11-29: §1.h rerun. 4. W-337j4 due 2026-12-05.
Files touched   : (none); api-collection — never commit
Flags for owner : W-337j4 (delete the 3 Atlas clusters) due 2026-12-05. W-418 needs the broker statement. Same-day FIFO tie in sales proposed, not placed (D-1010). W-416 waits. §1.s Aiven retention not given. Key rotation offered (W-429 placed).
Env notes       : NO Claude attribution; ask "commit and push" explicitly; OrbStack: `orb start`, DOCKER_HOST=unix://$HOME/.orbstack/run/docker.sock; full tiers on pipeline only; SQL via postgres:18 psql.
  Jars: m1.8 ../wealthlens-m1.8-pg/backend/target. env files ~/wealthlens-runs/{dev,staging,prod}.env (600) still hold MONGO_* (W-337j3d). No app running.
Updated         : 2026-11-28 (session 128)
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch -> stop and report both outputs. Match -> do **Next step** and nothing else.
