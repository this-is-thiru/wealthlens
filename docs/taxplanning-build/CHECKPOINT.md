# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : batch W-318 – W-322 (XIRR unpriced holdings, held trades in analytics, P&L year form, duplicate price, win/loss ratio) — next; nothing written
Stage           : W-264d DONE (D-641), committed and pushed; W-264 closed (all four audits); W-318 – W-322 ruled into Phase 10 as one batch
Done today      : session 89: freeze; W-304, W-305, W-306, W-309, W-264c, W-264d; batches W-317/W-315/W-314, W-313/W-312/W-266; W-316 (D-622 – D-646)
Board           : W-264d D; W-318 – W-322 · next (one batch); W-090d ·; W-091 – W-094 ·; W-299, W-294 · (wait run logs); W-307, W-308 K; W-311 · (after Phase 10, widened)
Next step       : record W-264d's pipeline run in D-641; plan the batch W-318 – W-322 with a probe run first, STOP for approval
Owner actions   : after deploy: §3.24 then redrive per listed user (W-316); §3.22 then §2.12 per listed user (W-313); §3.23 (W-312); §3.21 (W-304); §3.20 (W-303); §3.18, §3.19, then §2.14 (W-297, once; backup first) and its per-user §2.12 → §2.11; §3.17 → §2.13 → per user §2.12 → §2.2 → §2.11; §3.16; Q-1 (PS-8), Q-2 (AMC periods); rotate the Mongo password and JWT key pasted in session 82
Last verify     : W-316 pipeline 43384783225 success (D-640); W-264d kept class 6 / 0, validate 0, guards 0
Files touched   : none (all committed)
Last item commit: taxplanning W-264d: audit the P&L read and the analytics through the HTTP API
Updated         : 2026-11-13, session 89 — W-264d done; next batch W-318 – W-322
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
