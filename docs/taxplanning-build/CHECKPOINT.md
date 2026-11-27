# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-409 DONE (D-984), board D; committed and pushed on "commit it and push".
Stage           : between items; W-406 resumes next (C5a – C8a approved, D-983); W-410, W-411 placed, before W-337i.
Last item commit: "taxplanning W-409: open no port under the copier profile — the jar failed at start, CORS needs the servlet context; D-984"
Next step       : 1. Check the pipeline run on the W-409 commit (gh run list -b w-337 -L 1). Red → fix first.
  2. W-406: C5a (UTC-held fields list) in the tool, C6a (taxYear values), runbook §0.10, rerun the trial (containers
  w406-mongo / w406-pg running; scratchpad w406/ has keys, logs, answers; re-copy: drop public schema, §0.9 command).
Files touched   : plans/W-406.md, tools/read_comparison.py, tools/read_comparison_test.py (all new, W-406, uncommitted)
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
