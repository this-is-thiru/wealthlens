# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-325a done (D-673), committed and pushed; W-325 split (D-672); W-327 placed
Stage           : 6 DONE — review approved ("commit and push"); pipeline to confirm and record in D-673 (content only, no benchmark)
Done today      : session 94: W-115 (D-670), W-116 (D-671), W-325 split (D-672), W-325a (D-673); session 93: W-113 – W-114d
Board           : W-113 – W-116, W-325, W-325a D; W-325b – d ·, W-327 ·, W-326 ·; W-113a · (after F12); W-324 · (after AIS / 26AS); Phase 10 left: W-299, W-294 · (wait run logs)
Next step       : W-325b by board order (deductions and other heads; plan first, §2)
Owner actions   : after deploy: §3.24 then redrive per listed user (W-316); §3.22 then §2.12 per listed user (W-313); §3.23 (W-312); §3.21 (W-304); §3.20 (W-303); §3.18, §3.19, then §2.14 (W-297, once; backup first) and its per-user §2.12 → §2.11; §3.17 → §2.13 → per user §2.12 → §2.2 → §2.11; §3.16; Q-1 (PS-8), Q-2 (AMC periods); rotate the Mongo password and JWT key pasted in session 82
Last verify     : W-116 pipeline 43560701697 success (1a69560a), build + benchmark, recorded in D-671 (uncommitted); RUN_BENCHMARKS deleted
Files touched   : none beyond this file once the item commit lands
Last item commit: taxplanning W-325a: explain the salary lines in plain language
Updated         : 2026-11-14, session 94 — W-325a committed and pushed
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
