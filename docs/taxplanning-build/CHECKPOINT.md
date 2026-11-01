# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-089b — `CreditsNode` (first of the switch's queue, PARKED-ROWS.md)
Stage           : 1 of 6 (READ) — W-229 closed (D-435)
Done today      : W-088g9 (D-434), W-229 (D-435) committed and closed
Board           : `W-088g9 D  W-229 D`; queue: W-089b, W-223, W-213, W-216, then Phase 11 from W-096
Next step       : republish WL-A-2 if not yet read back (runbook §0.4); write plans/W-089b.md, one page, and STOP
Owner actions   : nothing pushed — api-collection is ahead by 2: push it first. W-217 before the next push. Runbook §3.7, §0.4 at the deploy
Last verify     : unit tier 3927 / 0 / 0; Checkstyle 0 (W-229); integration 334 / 0 / 0 (W-088g9, no main code since)
Files touched   : none
Last item commit: taxplanning W-229: park the open Phase 9 rows and switch to Phase 11
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
