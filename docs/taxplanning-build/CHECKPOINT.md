# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-092 + W-093 done (D-654, D-655), committed and pushed
Stage           : closed (owner "commit it and push", session 91)
Done today      : session 91: W-090d (D-652), W-091 (D-653), W-092/W-093 (D-654, D-655)
Board           : W-091 – W-093 D; W-094 E; W-093a ·; W-299, W-294 · (wait run logs); W-307, W-308 K; W-311 · (after Phase 10)
Next step       : record this push's pipeline in D-655; W-094: T21 left are 6, 8 (need ITR-3 / ITR-UN, parked W-090c3 / W-090e) — propose closing W-094 with those placed on the parked rows; then W-093a
Owner actions   : after deploy: §3.24 then redrive per listed user (W-316); §3.22 then §2.12 per listed user (W-313); §3.23 (W-312); §3.21 (W-304); §3.20 (W-303); §3.18, §3.19, then §2.14 (W-297, once; backup first) and its per-user §2.12 → §2.11; §3.17 → §2.13 → per user §2.12 → §2.2 → §2.11; §3.16; Q-1 (PS-8), Q-2 (AMC periods); rotate the Mongo password and JWT key pasted in session 82
Last verify     : W-092 first pass pipeline 43435925624 FAILED (ContentPerturbationTest, fixed in D-655); second pass targeted 654/0 + taxengine rules/api/golden 1893/0, route 2/0, validate clean
Files touched   : none (all committed)
Last item commit: taxplanning W-092: fill ITR-1 Parts C, D and ITR-2 VI-A, B-TI, B-TTI cells (second pass), closing W-092 and W-093
Updated         : 2026-11-13, session 91 — W-092/W-093 closed and pushed
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
