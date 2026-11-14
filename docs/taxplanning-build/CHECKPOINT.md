# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-328 done (D-694), board D — next by board order W-326 ("what would change it", in rupees; not yet planned)
Stage           : DONE (session 96, 2026-11-14) — committed and pushed
Done today      : session 96: W-327d2 – W-327e3 (D-688 – D-693), W-327 complete; W-328 placed and done (D-691, D-694)
Board           : W-327 complete, W-328 D; W-326 ·, then W-117 – W-123 ·; W-324 · (after AIS / 26AS); W-113a · (after F12); Phase 10: W-299, W-294 · (wait run logs)
Next step       : confirm the W-328 pipeline run and record it in D-694; then probe and plan W-326
Owner actions   : after deploy: §3.24 then redrive per listed user (W-316); §3.22 then §2.12 per listed user (W-313); §3.23 (W-312); §3.21 (W-304); §3.20 (W-303); §3.18, §3.19, then §2.14 (W-297, once; backup first) and its per-user §2.12 → §2.11; §3.17 → §2.13 → per user §2.12 → §2.2 → §2.11; §3.16; Q-1 (PS-8), Q-2 (AMC periods); rotate the Mongo password and JWT key pasted in session 82
Last verify     : W-328 local: ProcessingLogIntegrationTest 10/0; 200-rep probe 200/200 (28/200 before); red 161/200; validate green; W-327e3 pipeline 43612809601 success (D-693)
Files touched   : none beyond this file after the W-328 commit
Last item commit: taxplanning W-328: make the processing-log tests wait for the record
Updated         : 2026-11-14, session 96 — W-328 committed and pushed
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
