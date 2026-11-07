# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : none in flight — W-142 committed and closed (D-457)
Stage           : 6 (DONE); next by board order W-142a, not started
Done today      : session 48: D-451 – D-457 built and closed (W-235, W-139, W-139a, W-140, W-140a, W-141, W-142); W-237, W-142a placed
Board           : W-142 D, W-142a · (next), W-143 ·
Next step       : read W-142a's row and decision rows, write its plan, STOP
Owner actions   : give the TAX_PRIVACY_CONTACT value; sign off the findings in reviews/pack-review-2025-26-to-2026-27.md; push review/taxplanning and api-collection when ready (W-217 check first); deploy runbook §0.5, §0.6, §1.y, §1.z, §1.x, §3.7 – §3.9, §0.4; `orb start` before the integration tier
Last verify     : unit + integration 4627 / 0 / 0; Checkstyle 0; Spotless clean (at the W-142 commit)
Files touched   : none
Last item commit: taxplanning W-142: fail the build when the published trace changes without a reviewed snapshot
Updated         : 2026-11-07, session 48 — W-142 closed
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
