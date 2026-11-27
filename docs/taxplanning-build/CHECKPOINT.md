# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-415 DONE (D-995), W-414 DONE (D-996). Next: m1.7.1 = W-337i1 again (PR w-337 → master), then plan W-337i3.
Stage           : between items; pushed. Release rows need the owner's word each (merge, tag, publish).
Last item commit: "taxplanning W-414: read the plain answer in the runbook's live commands, send §1B's stockCode, fix the collection — dev's run found each; D-996"
Next step       : 1. Open PR w-337 → master; on its green run and the owner's word: merge, master run, tag m1.7.1, worktree, jar, WL-A-2.
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
