# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : none in flight — W-090b done (D-521), committed, not pushed
Stage           : 6 → next: plan W-090c (§2 template), ask for approval; STOP before building
Done today      : session 81: W-090b planned, approved C1a – C4a, built and committed (D-521)
Board           : W-090a —; W-090a1 D; W-090a2 D; W-090b D; W-090c ·; W-090d ·; W-090e K; W-258 ·; W-259 ·
Next step       : resume at W-090c (TY 2026-27 ITR-1 – 4 from r.164): run the resume check, read r.164(12)'s Table (Gazette 271092) first, the W-090c row and the two W-090b errata after Phase 10's table, then plan it
Owner actions   : push api-collection main first, then review/taxplanning (9926ea64, b9d45f2f, 7f01234b and the W-090b commit unpushed); adopt the 13 questions' wording
Last verify     : W-090b unit 5022 / 0 / 0 (Checkstyle + Spotless on), integration 539 / 0 / 0
Files touched   : none — all committed
Last item commit: taxplanning W-090b: hold a year's return forms as pack rows in the forms layer
Updated         : 2026-11-09, session 81 — W-090b committed; next W-090c
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
