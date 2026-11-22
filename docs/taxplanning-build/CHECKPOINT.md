# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-369 DONE (D-895), committed; releasing m1.6.1 (PR #72 → master → tag); W-366 paused at W-366b row 4
Stage           : prod and staging on m1.5 (e832b56a, W-362, D-869 – D-871), signed off; app stopped; worktrees m1.4 (way back) and m1.5 on disk; MongoDB tools REMOVED (reads via the owner's IntelliJ console, one $documents query; check the "database" field)
Done session 114: W-360, W-361, W-206, W-359b, W-362 (m1.5 deployed), W-358d split + d1 – d4, W-364, Phase 15 batch of ten (D-864 – D-887); W-363 parked, W-365 placed; D-888 three deployments
Board           : W-358d —, W-364 D, W-365 D, W-366 ·, W-367 ·, W-173 D, W-254 B, W-294 D, W-299 D, W-357b ·, W-351 ·, W-355 ·, W-356 ·, W-337 ·
Next step       : owner's word → commit W-369 (+ the W-366 run-log rows), PR #72 into master, its run, merge, master pipeline, tag m1.6.1, worktree + jar, WL-A-2 → W-366b from row 1 with the m1.6.1 jar → W-366c → close-out (D-896, D-897, W-368)
Open to owner   : the word to commit W-369 and release m1.6.1
Env notes       : NO Claude attribution anywhere; integration tier needs OrbStack (orb start) + DOCKER_HOST=unix://$HOME/.orbstack/run/docker.sock; a full tier only on the pipeline
Last verify     : W-254 green locally (Architecture 26, Modulith 1, UnusedPrivateField 2, StoredActorEncryption 3, RouteAccess 8, StoredStringId 15, ApiCollectionContract 1, Checkstyle, Spotless)
Last item commit: taxplanning W-369: store a password by a targeted update by email — a save missed every login the m1.4 re-key gave an ObjectId id
Files touched   : none — committed with W-369
Updated         : 2026-11-22, session 115 (W-369 committed; m1.6.1 release running)
```
## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
