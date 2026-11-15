# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-117d DONE (D-715). Next by board order: W-117e (donations as claims) — not planned yet; then W-119b1, W-120, W-122b, W-122c, W-329
Stage           : nothing in progress; W-117d committed and pushed
Done today      : session 100: W-122a (D-710), W-121 (D-711), W-123a (D-712), W-123b (D-713), W-117c (D-714), W-117d (D-715)
Board           : W-117d D; W-117e ·, W-119b1 ·, W-120 ·, W-122b – c ·, W-329 ·; W-113a, W-324 blocked
Next step       : check W-117d's pipeline; then plan W-117e (an entry moves its rules off notYetCatalogued), stop for approval
Owner actions   : unchanged from session 99; open: ClaimCatalogueEntry 8 components; W-122a's two findings (D-710); W-329
Last verify     : local named classes green (D-715); pipeline for the W-117d commit not yet checked
Files touched   : CHECKPOINT.md only, if anything
Last item commit: taxplanning W-117d: place every rule in or outside the claim catalogue, and fail the build on one placed nowhere
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
