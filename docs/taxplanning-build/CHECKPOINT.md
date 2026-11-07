# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : none in flight — W-237g committed and closed (D-477); W-237 closed with it
Stage           : 6 (DONE); next by board order W-247 (a client that shows the privacy notice), not started
Done today      : sessions 49 – 55: D-462 – D-477 (W-145, W-243, W-244, W-146, W-146b, W-236, W-237a – g; W-237 split and closed; W-245 decided); W-241 – W-249, W-146a, W-237b – g placed; WL-A-2 republished; pushed at a4c41426, six item commits since
Board           : W-237 D, W-237a – g D, W-247 · (next), W-248 ·, W-249 ·; W-146a · (after W-082e); W-238 – W-242 ·, W-246 · in Phase 14
Next step       : read W-247's row (a client that shows the privacy notice, now also the copy before both erasures) and write its plan, STOP
Owner actions   : pushed 2026-11-07 (session 50): review/taxplanning at a4c41426, api-collection main at fe20cd9 (W-217 check passed — run it again before every push). Fill runbook §1.s's record (W-237f facts 1 – 4: Atlas tier/backup, snapshot retention ≤ P1Y, copies outside Atlas, EBS; processor terms); give the Data Protection Board's breach-intimation channel (W-237c, asked); give the PRIVACY_CONTACT value (asked, none received yet); give the rule for when an account's purpose lapses (W-249, when you choose); sign off the findings in reviews/pack-review-2025-26-to-2026-27.md; deploy runbook §0.5, §0.6, §1.y, §1.z, §1.u, §1.t, §1.s, §1.w (when you choose), §1.v, §1.x, §5.7, §3.7 – §3.9, §0.4; `orb start` before the integration tier
Last verify     : W-237g (docs only): unit tier 4742 / 0 / 0 from this run's reports; FinishedPlanTest 5 / 0; 4 stale failing XMLs are old Zz probe / benchmark runs, not in the tree
Files touched   : none
Last item commit: taxplanning W-237g: move the copy before erasure into the PDF report and client rows, and close W-237
Updated         : 2026-11-07, session 55 — W-237g and W-237 closed
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
