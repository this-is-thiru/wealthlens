# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-412 DONE (D-990), board D; committed and pushed on "commit it and push". Trial stores removed.
Stage           : between items. Staging and dev readback DONE (both 18.7, schema wealthlens), logged; all three hosts ready.
Last item commit: "taxplanning W-337h: log staging's and dev's readback — PostgreSQL 18.7, schema wealthlens on both; the setup's last gap"
Next step       : 1. Plan W-337i (the moves: dev, a rehearsal on a copy of prod, staging, prod).
Files touched   : (none uncommitted)
Flags for owner : §1.s Aiven retention not given; staging termination protection off (owner's call). W-337f flags unchanged.
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
