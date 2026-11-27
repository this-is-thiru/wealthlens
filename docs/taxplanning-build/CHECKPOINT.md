# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-337i2 DONE (dev on m1.7/PostgreSQL). Next: W-414, W-415 (before staging), W-337i3 (rehearsal).
Stage           : between items; W-337i1 + W-337i2 committed and pushed; api-collection f4c85b8.
Last item commit: "taxplanning W-337i2: move dev to PostgreSQL on m1.7 — copied, seeded, the collection run; §0.9 step 1's query fixed; D-992 – D-994"
Next step       : 1. Plan W-414 + W-415 (batched small items), then W-337i3. Commit on the owner's word (api-collection: Auth/Login.yml only).
Files touched   : (none uncommitted but this file)
Flags for owner : §1.s Aiven retention not given; staging termination protection off (owner's call). W-337f flags unchanged.
Env notes       : NO Claude attribution; DOCKER_HOST=unix://$HOME/.orbstack/run/docker.sock; full tiers on pipeline only.
  Jars: m1.7 ../wealthlens-m1.7/backend/target, m1.6.1 ../wealthlens-m1.6.1/backend/target.
Updated         : 2026-11-27 (session 124)
```
## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch -> stop and report both outputs. Match -> do **Next step** and nothing else.
