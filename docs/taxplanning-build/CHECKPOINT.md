# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : none in flight — W-140a committed and closed (D-455)
Stage           : 6 (DONE); next by board order W-141, not started
Done today      : session 48: D-451 (W-235), D-452 (W-139), D-453 (W-139a), D-454 (W-140), D-455 (W-140a) built and closed; W-237 placed
Board           : W-140a D, W-141 · (next)
Next step       : read W-141's row and decision rows, write its plan, STOP
Owner actions   : give the TAX_PRIVACY_CONTACT value; sign off the findings in reviews/pack-review-2025-26-to-2026-27.md; push review/taxplanning and api-collection when ready (W-217 check first); deploy runbook §0.5, §0.6, §1.y, §1.z, §1.x, §3.7 – §3.9, §0.4; `orb start` before the integration tier
Last verify     : unit + integration 4622 / 0 / 0; Checkstyle 0; Spotless clean (at the W-140a commit)
Files touched   : none
Last item commit: taxplanning W-140a: work by hand the 23 surcharge and age boundaries the generator found without a case
Updated         : 2026-11-07, session 48 — W-140a closed
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
