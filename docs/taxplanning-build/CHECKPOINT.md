# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-287 (mutation testing for the trade ledger) — next by board order; nothing written
Stage           : 6 — W-282 DONE (D-570), committed and pushed
Done today      : session 84: W-264a (7ae723b2), W-268 (2303dba2, api e7130f8), W-269 (f58a3857), W-270 (e2e2e92a), W-271 (577adf84), W-272 (34da75c7), W-273 (1e7a4761), W-274 (39d1f739), W-275 (49b059dd), W-276 (6508d24a), W-277 (d1c6f4de), W-278 (5e644e92), W-279 (e4c1c919), W-280 (5b457406), W-281 (b8969264), W-286 (5fa13795), W-282 committed; W-285 – W-287 placed
Board           : W-264a D; W-268 – W-282 D; W-286 D; W-287 ·; W-283 – W-285 ·; W-264b – d ·; W-266 ·; W-090d ·
Next step       : plan W-287 (plans/W-287.md), STOP for approval
Owner actions   : answer Q-1 (should V2 refuse a trade while a temporary is pending? PS-8); run runbook 3.10 – 3.15 after deploy; check the pipeline on the W-286 commit; rotate the Mongo password and JWT key pasted in session 82; adopt the 13 questions' wording
Last verify     : full clean verify 5,774 tests, 0 failures, all four JaCoCo gates met; probe failed the new gate; PIT 57 % (reverted)
Files touched   : none since the W-282 commit
Last item commit: taxplanning W-282: hold the trade ledger to the charge engine's coverage floor
Updated         : 2026-11-11, session 84 — W-282 done; W-287 next
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
