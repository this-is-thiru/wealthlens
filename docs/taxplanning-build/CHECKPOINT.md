# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : none in flight — W-249 committed and closed (D-480)
Stage           : 6 (DONE); next by board order W-250 (the notice before consent), not started
Done today      : sessions 49 – 58: D-462 – D-480 (W-145, W-243, W-244, W-146, W-146b, W-236, W-237a – g, W-247, W-248, W-249; W-237 split and closed; W-245 decided); W-241 – W-254, W-146a, W-237b – g placed; WL-43 created; pushed at 759cfa7f, then at 40160055 (session 58)
Board           : W-249 D; W-250 · (next), W-251 · (waits for a client decision), W-252 ·, W-253 ·; W-254 B in Phase 15; W-146a · (after W-082e); W-238 – W-242 ·, W-246 · in Phase 14
Next step       : read W-250's row (the notice before consent) and write its plan, STOP
Owner actions   : nothing unpushed; last pushed 2026-11-07 (session 58): review/taxplanning at 40160055, api-collection main at 4a73c15 (W-217 check passed — run it again before every push). Fill runbook §1.s's record (W-237f facts 1 – 4: Atlas tier/backup, snapshot retention ≤ P1Y, copies outside Atlas, EBS; processor terms); give the Data Protection Board's breach-intimation channel (W-237c, asked); give the PRIVACY_CONTACT value (asked, none received yet); sign off the findings in reviews/pack-review-2025-26-to-2026-27.md; deploy runbook §0.5, §0.6, §1.r (right after the first start, before users), §1.q (before and after the first run), §1.y, §1.z, §1.u, §1.t, §1.s, §1.w (when you choose), §1.v, §1.x, §5.7, §3.7 – §3.9, §0.4; `orb start` before the integration tier
Last verify     : W-249: unit + integration 4767 / 0 / 0; Checkstyle 0; four probes failed their tests, restored (cmp)
Files touched   : none
Last item commit: taxplanning W-249: erase an account three years after its last sign-in
Updated         : 2026-11-07, session 58 — W-249 closed and pushed; PAUSED at the owner's session-limit notice (Mac shut down). Resume: run the resume check, then Next step
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
