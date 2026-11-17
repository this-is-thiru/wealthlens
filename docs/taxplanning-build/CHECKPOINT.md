# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-126e3b DONE (D-747); W-126e3 complete. Next by board order: W-126e4 (form identity on a real Form 16)
Stage           : committed and pushed; nothing in progress
Done today      : session 104: D-740 – D-747; W-126e1, W-126e2, W-126e3a, W-126e3b pushed
Board           : W-126e3b D; W-126e4 ·; W-128 ·
Next step       : check the pipeline for the W-126e3b push; W-126e4 needs the owner's own TY 2025-26 Form 16 path (local, uncommitted drift probe) — ask first; then plan it
Owner actions   : own Form 16 path for W-126e4; runbook §0.8 before the one deploy
Last verify     : targeted 476/0 with validate on W-126e3b
Files touched   : CHECKPOINT.md only, if anything
Last item commit: taxplanning W-126e3b: read Form 16's Part A boxes
Updated         : 2026-11-17, session 104
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.

*Known false mismatch:* once a post-commit state line exists, the grep returns **that** commit rather than the
item commit named above, because state lines carry the item prefix too. Compare the **item** commit; the tree,
not the grep, is what decides. It has fired for W-179's planning (`f461363c` against `866f3dd2`), W-181's
(`c620ba99` against `c76dad9a`), W-182's (`592ff6a6` against `e494326f`), W-183's (`ac3a9fce` against
`ecf681cd`), W-184's (`235d2566` against `f511544a`, then `a0fe2665`), W-088g's (`c59d8223` against
`c030e04f`), W-088g1's (`1f975262` against `f3027724`), W-088g2's (`09ed07b7` against `927b2581`) and
W-089's (`f7140cfe` against the post-commit line `1b62fe5a`).
