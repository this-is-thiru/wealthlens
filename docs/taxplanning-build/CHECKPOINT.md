# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-122a DONE (D-710). BUILD NEXT: W-121, then W-123a, W-123b — each in plans/<id>.md; placed: W-117e, W-119b1, W-120, W-117c/d, W-122b/c
Stage           : nothing in progress; W-122a committed and pushed with api-collection
Done today      : session 100: W-122a (D-710) — onboarding steps on the draft, GET …/onboarding
Board           : W-122a D; W-121 E; W-123a – b E; W-117c – e ·, W-119b1 ·, W-120 ·, W-122b – c ·; W-324 ·; Phase 10: W-299, W-294 ·
Next step       : check W-122a's pipeline, then BUILD W-121 from plans/W-121.md, stop at its report. Grep every path first. Locally only NAMED test classes; full tiers on the pipeline; WealthLensModulithTest before every commit
Owner actions   : unchanged from session 99 (§3.24/W-316 … rotate Mongo password and JWT key); open: ClaimCatalogueEntry 8 components; W-122a's two findings (READY reopened by a step patch; a step added twice) — D-710
Last verify     : local named classes green (D-710); pipeline for the W-122a commit not yet checked
Files touched   : CHECKPOINT.md only, if anything
Last item commit: taxplanning W-122a: record which onboarding steps are answered and show what each open one is worth
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
