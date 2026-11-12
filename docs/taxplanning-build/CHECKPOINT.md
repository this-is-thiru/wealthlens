# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-297 (correct stored transactions' and outcome rows' segment) — next by board order; nothing written
Stage           : W-296 DONE (D-598), committed and pushed; full tiers NOT run locally — pipeline runs only on a PR/push to master; W-297 not started
Done today      : session 86: W-292, W-295, W-296 committed and pushed; W-295 – W-298 placed, W-297 widened (D-591 – D-598)
Board           : W-295 D; W-296 D; W-297 ·; W-298 · (placed, D-596); W-284, W-285 ·; W-290 ·; W-294 ·; W-264b – d ·; W-266 ·; W-090d ·
Next step       : resume: run the resume check below; confirm W-296's tiers (pipeline run, or a local run if none); then plan W-297, STOP for approval
Owner actions   : after deploy: §3.18, §3.19 (read-only; no §2.11 for a §3.19 user before W-297); §3.17 → §2.13 → per user §2.12 → §2.2 → §2.11; §3.16; Q-1 (PS-8); rotate the Mongo password and JWT key pasted in session 82; adopt the 13 questions' wording
Last verify     : W-296 targeted 7 classes 147 / 0; validate 0; full tiers not run locally (pipeline)
Files touched   : none
Last item commit: taxplanning W-296: merge a V1 same-day buy only into a lot of its segment
Updated         : 2026-11-12, session 86 — W-296 done; W-297 next
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
