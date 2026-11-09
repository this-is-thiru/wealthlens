# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : none in flight — W-260 done (D-522), committed, not pushed
Stage           : 6 → next: plan W-090c (§2 template), ask for approval; STOP before building
Done today      : session 81: W-090b (327e5a00, D-521); W-260 placed, built and committed (D-522)
Board           : W-090a —; W-090a1 D; W-090a2 D; W-090b D; W-090c ·; W-090d ·; W-090e K; W-258 ·; W-259 ·; W-260 D
Next step       : resume at W-090c (TY 2026-27 ITR-1 – 4 from r.164): run the resume check, read r.164(12)'s Table (Gazette 271092) first, the W-090c row and the two W-090b errata after Phase 10's table, then plan it
Owner actions   : push api-collection main first, then review/taxplanning (9926ea64, b9d45f2f, 7f01234b, 327e5a00 and the W-260 commit unpushed); adopt the 13 questions' wording
Last verify     : unit-tier-stamp-test.sh 10 / 10; unit-tier.sh 5022 / 0 / 0
Files touched   : none — all committed
Last item commit: tooling W-260: run the commit gate's unit tier once per working tree, at low priority
Updated         : 2026-11-09, session 81 — W-260 committed; next W-090c
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
