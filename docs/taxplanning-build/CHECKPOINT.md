# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-291 (a bonus lot copies the first lot's charges and sales) — next by board order; nothing written
Stage           : W-293 DONE (D-588), committed and pushed; W-291 not started
Done today      : session 85: W-287, W-288, W-283, W-283a, W-283a1, W-283a2, W-283a3, W-289, W-293 pushed; W-290 – W-292 placed
Board           : W-293 D; W-291 ·; W-292 ·; W-284, W-285 ·; W-290 ·; W-264b – d ·; W-266 ·; W-090d ·
Next step       : resume: run the resume check below; then plan W-291 (red test first; V2 sale effect now known: it allocates from the copied charges too), STOP for approval
Owner actions   : run §3.16 after deploy; Q-1 (PS-8); runbook §2.11 / §2.12 when needed (§2.12 only with W-293 deployed); 3.10 – 3.15 after deploy; rotate the Mongo password and JWT key pasted in session 82; adopt the 13 questions' wording
Last verify     : W-293 unit 5,194 / 0, integration 627 / 0 (60 classes)
Files touched   : none
Last item commit: taxplanning W-293: record every sale's share of a lot's charges in the replay
Updated         : 2026-11-12, session 85 — W-293 done; W-291 next
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
