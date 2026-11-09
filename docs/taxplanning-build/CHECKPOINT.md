# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : none in flight — W-090c1b2 done (D-528), committed, not pushed; W-090c1b closed
Stage           : 6 → next: plan W-090c2 (§2 template), ask for approval; STOP before building
Done today      : session 81: W-090b, W-260, W-090c1a, W-090c1b1; session 82: W-090c1b2 (D-528)
Board           : W-090c1a D; W-090c1b —; W-090c1b1 D; W-090c1b2 D; W-090c2 ·; W-090c3 K
Next step       : resume at W-090c2 (TY 2026-27 ITR-1 and ITR-2 as form rows, r.164(12) manners, I19/I20, I27 over forms/): run the resume check, read plans/W-090c.md and the W-090c2 row, then plan it
Owner actions   : push api-collection main first, then review/taxplanning (eight commits unpushed, ending with the W-090c1b2 commit); adopt the 13 questions' wording
Last verify     : W-090c1b2 unit 5069 / 0 / 0, integration 539 / 0 / 0; probes caught (3 fails), reverted byte for byte
Files touched   : none — all committed
Last item commit: taxplanning W-090c1b2: publish each head's loss brought forward and left to carry forward
Updated         : 2026-11-09, session 82 — W-090c1b2 committed; next W-090c2
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
