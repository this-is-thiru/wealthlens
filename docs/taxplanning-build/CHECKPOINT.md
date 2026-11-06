# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : none in flight — session 46 paused by the owner (session limit, Mac shut down); W-089c + W-112 closed (D-446, fc7c5056)
Stage           : next family W-134 – W-136 not started: no reading, no plan, no code
Done today      : session 46: READ (sources/2026-11-06-advance-tax-interest.md); plan approved C1a–C5a; built advance.tax, interest.and.fees, 7 rows, 7 definitions; relocked law.126 / law.129
Board           : W-089c D (PARKED-ROWS: NOW 2), W-112 D; W-112a (234C carve-out), W-112b (refund interest) placed after W-112
Next step       : read the W-134 – W-136 rows and their decision rows, source what they need (Gazette only), write one family plan, STOP
Owner actions   : pushed 2026-11-06: review/taxplanning at fc7c5056; api-collection already level with origin/main; W-217 check passed (pointer 814dfb4 on origin/main) — run it again before every push. Runbook §1.x, §3.7 – §3.9, §0.4 at the deploy. Revoke the zenmux key if not wanted. Integration tier: `orb start` first
Last verify     : unit + integration 4425 / 0 / 0; Checkstyle 0; Spotless clean (at the W-089c + W-112 commit)
Files touched   : none
Last item commit: taxplanning W-089c, W-112: compute advance tax, the interest on it and the late-return fee
Updated         : 2026-11-06, session 46 — paused
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
