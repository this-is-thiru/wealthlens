# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-327e3 done (D-693), board D — W-327 complete; next by board order W-328 (intermittent processing-log test; not yet planned)
Stage           : DONE (session 96, 2026-11-14) — committed and pushed
Done today      : session 96: W-327d2 (D-688); W-327e split (D-689); W-327e1 (D-690); W-328 placed (D-691); W-327e2 (D-692); W-327e3 (D-693), W-327 complete
Board           : W-327, W-327a – e3 D; W-328 ·; W-326 ·; W-324 · (after AIS / 26AS); W-113a · (after F12); Phase 10: W-299, W-294 · (wait run logs)
Next step       : confirm the W-327e3 pipeline run and record it in D-693; then plan W-328 (reproduce the processing-log race first)
Owner actions   : after deploy: §3.24 then redrive per listed user (W-316); §3.22 then §2.12 per listed user (W-313); §3.23 (W-312); §3.21 (W-304); §3.20 (W-303); §3.18, §3.19, then §2.14 (W-297, once; backup first) and its per-user §2.12 → §2.11; §3.17 → §2.13 → per user §2.12 → §2.2 → §2.11; §3.16; Q-1 (PS-8), Q-2 (AMC periods); rotate the Mongo password and JWT key pasted in session 82
Last verify     : W-327e3 local: unit 4514/0 with the pin, DraftComputation + Modulith 61/0, validate green; 2 red checks; W-327e2 pipeline 43610351964 success (D-692)
Files touched   : none beyond this file after the W-327e3 commit
Last item commit: taxplanning W-327e3: give the Agniveer line its reason and pin every engine line to read in words
Updated         : 2026-11-14, session 96 — W-327e3 committed and pushed, W-327 complete
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
