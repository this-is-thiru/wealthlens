# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : none in flight — W-090a2 done (D-520), committed with api-collection, not pushed; W-090a closed
Stage           : 6 → next: plan W-090b (§2 template), ask for approval; STOP before building
Done today      : session 80: W-090a1 (9926ea64, D-518), W-259 placed (b9d45f2f, D-519), W-090a2 (D-520)
Board           : W-090a —; W-090a1 D; W-090a2 D; W-090b ·; W-090c ·; W-090d ·; W-090e K; W-258 ·; W-259 ·
Next step       : resume at W-090b (the ITR form model, synthetic pack): run the resume check, read plans/W-090.md, the W-090b row and DR 21 I3 (21-itr-forms.md:49 – 95), then plan it in the §2 template
Owner actions   : push api-collection main first, then review/taxplanning (9926ea64, b9d45f2f and the W-090a2 commit unpushed); adopt the 13 questions' wording; earlier standing actions unchanged
Last verify     : W-090a2 unit 5002 / 0 / 0 (Checkstyle + Spotless on), integration 539 / 0 / 0
Files touched   : none — all committed
Last item commit: taxplanning W-090a2: let a draft answer the year's declarations, refused on save when the year does not ask them
Updated         : 2026-11-09, session 80 — W-090a2 committed; next W-090b
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
