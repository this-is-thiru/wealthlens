# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : none in flight — W-109a closed (D-443)
Stage           : 6 of 6 (DONE); the owner orders what remains
Done today      : W-088g9, W-229, W-089b, W-223, W-213, W-216, W-230; profile, drafts, compute families; W-109a (D-434 – D-443)
Board           : `W-109a D`; open in Phase 11: W-160g, W-111; Phases 10, 12, 13, 14 unstarted; `K` rows in PARKED-ROWS.md
Next step       : the owner chooses the next family from the view given in session 45
Owner actions   : nothing pushed — api-collection is ahead by 5: push it first. W-217 before the next push. Runbook §1.x, §3.7 – §3.9, §0.4 at the deploy
Last verify     : unit + integration 4389 / 0 / 0; Checkstyle 0
Files touched   : none
Last item commit: taxplanning W-109a: store what was decided for each capital-gains bucket
Updated         : 2026-11-01, session 45
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
