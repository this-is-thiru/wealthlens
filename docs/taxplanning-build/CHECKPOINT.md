# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-337h DONE — D-980, board D; committed and pushed on "commit it and push".
Stage           : between items. Owner signed off all three hosts (row 6) without pasting staging's/dev's
  version + schema readback (rows 0, 2 – 3) — recorded as not pasted in the run log.
Last item commit: "taxplanning W-337h: record the three PostgreSQL hosts as they are now and close the setup — dev and staging on Aiven, prod on Neon; D-980"
Next step       : 1. Check the pipeline run on the W-337h commit (gh run list -b w-337 -L 1). Red → fix first.
  2. Then plan W-406 (rehearsal read comparison), then W-337i, W-337j (closes WL-50), W-403, W-399.
Files touched   : (none uncommitted)
Flags for owner : staging/dev readback unpasted; §1.s Aiven retention not given. W-337f flags unchanged.
Env notes       : NO Claude attribution; integration needs OrbStack + DOCKER_HOST=unix://$HOME/.orbstack/run/docker.sock; full tiers on the
  pipeline only. Tests run in UTC (argLine). Next Flyway version: V14.
Updated         : 2026-11-27 (session 124)
```
## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch -> stop and report both outputs. Match -> do **Next step** and nothing else.
