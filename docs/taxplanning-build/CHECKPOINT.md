# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-337i split (D-991: C1a, C2b, C3a – C6a). W-337i1 (the release) EXPLAINED, plans/W-337i1.md; board i1 E.
Stage           : W-337i1 approved ("sure, approved"); paths staged, validate run; awaiting the owner's explicit word to commit + push (row 1).
Last item commit: "taxplanning W-337h: log staging's and dev's readback — PostgreSQL 18.7, schema wealthlens on both; the setup's last gap"
Next step       : 1. On "commit it and push": W-337i1 row 1, then rows 2 – 6 (each outward row on the owner's word).
Files touched   : plans/W-337i.md, plans/W-337i1.md (new); DECISIONS.md (D-991); 03-WORK-ITEMS.md; STATE.md
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
