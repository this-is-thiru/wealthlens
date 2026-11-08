# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : none in flight — W-238i committed and closed (D-498)
Stage           : 6 (DONE) — W-238i closed
Done today      : sessions 49 – 67: D-462 – D-493 (W-238 split, W-238a, W-238b, W-238c, W-238d, W-238e, W-145, W-243, W-244, W-146, W-146b, W-236, W-237a – g, W-247, W-248, W-249, W-250, W-255, W-252, W-253, W-256, W-257; W-251 parked; W-237 split and closed; W-245 decided); W-241 – W-254, W-146a, W-237b – g placed; WL-43 created; W-256, W-257 placed; pushed at 759cfa7f, then at 40160055 (session 58)
Board           : W-238 D; W-238a – W-238e1 D; W-238e2 B; W-238f D; W-238g D; W-238h D; W-238i D; W-238j ·; W-238k · (after W-238j, owner); W-256 D; W-257 D; W-253 D; W-252 D; W-251 K (D-483); W-255 D; W-254 B in Phase 15; W-146a · (after W-082e); W-239 – W-242 ·, W-246 · in Phase 14
Next step       : W-238j by board order — read its row and board line 65 first, then plan and ask for approval; STOP before building
Owner actions   : UNPUSHED: the W-238g, W-238h and W-238i commits, and api-collection main's W-238i commit — push after the W-217 check; last pushed 2026-11-08 (session 69): review/taxplanning at 212543d3, api-collection main at 33e356d (W-217 check passed: origin/main contains it — run it again before every push). Fill runbook §1.s's record (W-237f facts 1 – 4: Atlas tier/backup, snapshot retention ≤ P1Y, copies outside Atlas, EBS; processor terms); give the Data Protection Board's breach-intimation channel (W-237c, asked); give the PRIVACY_CONTACT value (asked, none received yet); sign off the findings in reviews/pack-review-2025-26-to-2026-27.md; deploy runbook §1.r now also re-keys logins and encrypts identifiers and actors (W-253, W-256: first start with APP_TAX_JOBS_ENABLED=false, before users, with a SUPER_USER token taken on the previous build; then step 7 turns the jobs on; §0.7 for console look-ups); §1.o (before the deploy carrying W-255; §0.1.3 grants SUPER_USER on a fresh database), §0.5, §0.6, §1.r (right after the first start, before users), §1.q (before and after the first run), §1.p (before and a week after; any client must send privacyNoticeVersion; add the Board's link to board-complaint when you have it), §1.y, §1.z, §1.u, §1.t, §1.s, §1.w (when you choose), §1.v, §1.x, §5.7, §3.7 – §3.9, §0.4; `orb start` before the integration tier
Last verify     : W-238i: verify 4908 / 0 / 0 (backend), test-report 26 / 0 / 0, Checkstyle 0, spotless pass, JaCoCo pass; targeted 81 / 0 / 0; -Pbenchmark 4 / 0 / 0 (N3 p99 70.533 ms); 4 probes failed as expected, restored by cmp
Files touched   : none (W-238i committed)
Last item commit: taxplanning W-238i: let each provident fund share be held when basic moves
Updated         : 2026-11-08, session 72 — W-238i committed and closed (D-498); next W-238j
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
