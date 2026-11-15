# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-123b DONE (D-713). Every approved plan is now built. Placed, not planned: W-329 (audit created-at), W-117c/d/e, W-119b1, W-120, W-122b/c, W-324
Stage           : nothing in progress; W-123b committed and pushed with api-collection
Done today      : session 100: W-122a (D-710), W-121 (D-711), W-123a (D-712), W-123b (D-713)
Board           : W-122a D, W-121 D, W-123a D, W-123b D; W-329 ·; W-117c – e ·, W-119b1 ·, W-120 ·, W-122b – c ·; W-324 ·
Next step       : check W-123b's pipeline; then ask the owner which item to plan next (board order on STATE line 63)
Owner actions   : unchanged from session 99; open: ClaimCatalogueEntry 8 components; W-122a's two findings (D-710); W-329
Last verify     : local named classes green (D-713); pipeline for the W-123b commit not yet checked
Files touched   : CHECKPOINT.md only, if anything
Last item commit: taxplanning W-123b: list a profile's computations so each report can be taken before erasure
Updated         : 2026-11-15, session 100
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
