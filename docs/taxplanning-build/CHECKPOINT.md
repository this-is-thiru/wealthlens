# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-406 DONE (D-985), board D; committed and pushed on "commit it and push".
Stage           : between items. W-410, W-411 placed (both before W-337i). Owner asked: do you hold every account's email (§0.10 users.txt)?
Last item commit: "taxplanning W-406: compare every read of m1.6.1 and the PostgreSQL build in the rehearsal — a difference not listed stops the move; D-985"
Next step       : 1. Check the pipeline runs on 8a537a5c (45932326356) and the W-406 commit (gh run list -b w-337 -L 2). Red → fix first.
  2. Plan W-410 + W-411 together (batch-small-items), then W-337i. Trial containers w406-mongo / w406-pg still running (stop when done).
Files touched   : (none uncommitted)
Flags for owner : staging/dev readback unpasted (W-337h); §1.s Aiven retention not given. W-337f flags unchanged.
Env notes       : NO Claude attribution; OrbStack started this session; DOCKER_HOST=unix://$HOME/.orbstack/run/docker.sock; full tiers on
  pipeline only. Trial: m1.6.1 jar built in ../wealthlens-m1.6.1/backend/target, w-337 jar in backend/target.
Updated         : 2026-11-27 (session 124)
```
## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch -> stop and report both outputs. Match -> do **Next step** and nothing else.
