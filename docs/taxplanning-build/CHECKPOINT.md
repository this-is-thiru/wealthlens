# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : none in flight — W-236 committed and closed (D-469)
Stage           : 6 (DONE); next by board order W-237, not started
Done today      : sessions 49 – 50: D-462 – D-469 (W-145, W-243, W-244, W-146, W-146b, W-236; W-245 decided); W-241 – W-246, W-146a placed; WL-A-2 republished (§1.w); not pushed since d5479b0c
Board           : W-236 D, W-237 · (next); W-146a · (after W-082e); W-238 – W-242 ·, W-246 · in Phase 14
Next step       : read W-237's row and decision rows, write its plan, STOP
Owner actions   : pushed 2026-11-07: review/taxplanning at d5479b0c, api-collection main at fe20cd9 (W-217 check passed — run it again before every push). Six item commits since. Give the TAX_PRIVACY_CONTACT value (asked; none received yet); sign off the findings in reviews/pack-review-2025-26-to-2026-27.md; deploy runbook §0.5, §0.6, §1.y, §1.z, §1.w (new: Gazette watch, when you choose), §1.x, §3.7 – §3.9, §0.4; `orb start` before the integration tier
Last verify     : W-236: unit 4260 / 0 / 0; integration 425 / 0 / 0; Checkstyle 0; Spotless clean
Files touched   : none
Last item commit: taxplanning W-236: watch the e-Gazette daily for instruments that may change a shipped pack, recording leads, never writing a pack
Updated         : 2026-11-07, session 50 — W-236 closed
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
