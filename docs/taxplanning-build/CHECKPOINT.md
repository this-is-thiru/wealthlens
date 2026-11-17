# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-131b DONE (D-763). Next: W-131c (the ParseDocument job)
Stage           : committed and pushed; nothing in progress
Done today      : session 104: D-740 – D-763 pushed
Board           : W-131a D; W-131b D; W-131c – f ·
Next step       : check the pipeline for the W-131b push; plan W-131c (held profiles skipped; idempotent on (documentId, parserVersion); wall-clock budget)
Owner actions   : runbook §0.8, §1.n; W-131g, W-333 yes/no
Last verify     : W-131b targeted verify 119/0 incl. 4 integration classes
Files touched   : CHECKPOINT.md only, if anything
Last item commit: taxplanning W-131b: store a document's bytes encrypted within O6's limits
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
