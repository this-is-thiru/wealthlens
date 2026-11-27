# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-410 (D-986) and W-411 (D-987) DONE, board D; committed separately and pushed on "commit it and push".
Stage           : between items. W-412 placed (user-charge lists' tie). Owner still to answer: every account's email for §0.10?
Last item commit: "taxplanning W-411: list rate cards by start date, then schedule code — a shared start date came back in store order; D-987"
Next step       : 1. Check the pipeline run on the W-411 commit (gh run list -b w-337 -L 2). Red → fix first.
  2. Plan W-412, then W-337i. Trial containers w406-mongo / w406-pg still running (stop when no longer needed).
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
