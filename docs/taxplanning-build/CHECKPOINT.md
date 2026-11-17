# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-128b DONE (D-752). Next by board order: W-128c (row 4(b) vs standard deduction and row A; row 16 vs cess)
Stage           : committed and pushed; nothing in progress
Done today      : session 104: D-740 – D-752; W-126e1 – W-126e3b, W-126e5, W-128a, W-128b pushed
Board           : W-128b D; W-128c ·; W-129 ·
Next step       : check the pipeline for the W-128b push; then plan W-128c (grep TaxCalculator for a standard-deduction / cess accessor first; grep fixtures for 4(b)/16 values the new checks reach); stop for approval
Owner actions   : runbook §0.8 before the one deploy
Last verify     : targeted 498/0 with validate on W-128b
Files touched   : CHECKPOINT.md only, if anything
Last item commit: taxplanning W-128b: check a certificate across its parts
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
