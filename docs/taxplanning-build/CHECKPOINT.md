# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-268 (maintenance and test routes reach other users' data) — next by board order; nothing written
Stage           : 6 — W-264a DONE (D-535), committed and pushed
Done today      : session 84: W-264a built and committed (17 fix rows W-268 – W-284 placed, D-536 – D-552)
Board           : W-264a D; W-268 – W-284 · (before W-264b); W-264b – d ·; W-266 ·; W-090d ·
Next step       : plan W-268 (plans/W-268.md), STOP for approval
Owner actions   : answer Q-1 (should V2 refuse a trade while a temporary is pending? PS-8); check the pipeline on 200ead80, 4f268a1c and the W-264a commit; rotate the Mongo password and JWT key pasted in session 82; adopt the 13 questions' wording
Last verify     : PortfolioGapAuditIntegrationTest 20 / 20; failing scenarios reproduced twice (11 + 5); ./mvnw validate clean
Files touched   : none since the W-264a commit
Last item commit: taxplanning W-264a: audit the portfolio module through its API
Updated         : 2026-11-11, session 84 — W-264a done; W-268 next
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
