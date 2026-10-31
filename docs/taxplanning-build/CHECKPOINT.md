# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : none in flight — W-088g4 is closed (D-432)
Stage           : 6 of 6 (DONE) — "commit it" was the review approval; board D in the item commit
Plan            : plans/W-088g4.md — approved C1a C2a; Revision 1 and Build record
Decisions       : D-432 — notices by effect; W-088g6-g8 placed; "2025 s.55" corrected to s.90
Board           : `W-088g4 D  W-088g5 ·  W-088g6 ·  W-088g7 ·  W-088g8 ·`
Next step       : W-088g5 (land through the trade paths, proven — D-429) — on "start W-088g5", plan and STOP
Last verify     : unit tier 3886 / 0 / 0; integration tier 323 / 0 / 0; Checkstyle 0
Files touched   : none
Last commit     : the W-088g4 item commit (subject in STATE "Last checkpoint")
Updated         : 2026-11-01, session 44 — W-088g4 closed
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
