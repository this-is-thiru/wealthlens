# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-412 DONE (D-990), board D; committed and pushed on "commit it and push". Trial stores removed.
Stage           : between items. Next: plan W-337i (the moves).
Last item commit: "taxplanning W-412: order the user-charge lists by trade date, then id — a shared trade date came back in store order; D-990"
Next step       : 1. Check the pipeline runs on c3258099 (45935525199) and the W-412 commit (gh run list -b w-337 -L 2). Red → fix first.
  2. Plan W-337i. Owner to paste staging/dev readback (runbook §2.19 steps 1 – 3) first.
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
