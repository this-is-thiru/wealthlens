# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-413 DONE (D-989), board D; committed and pushed on "commit it and push" (with D-988).
Stage           : between items.
Last item commit: "taxplanning W-413: put every PostgreSQL table in schema wealthlens — nothing named a schema, so all of it went to public; D-989"
Next step       : 1. Check the pipeline run on the W-413 commit (gh run list -b w-337 -L 2). Red → fix first.
  2. Plan W-412; rerun W-406's trial (trial PG now in schema wealthlens); remove trial stores; then W-337i.
  Owner to paste staging/dev readback (W-337h, runbook §2.19 steps 1 – 3) before W-337i.
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
