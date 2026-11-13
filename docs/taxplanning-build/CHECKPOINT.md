# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : batch W-091 – W-094: W-091 done (D-653), committed and pushed; W-092 next
Stage           : W-091 closed (owner "commit it and push", session 91); W-092 – W-094 E under plans/W-091.md C1a
Done today      : session 91: W-090d (D-652), W-091 (D-653)
Board           : W-090d D, W-091 D; W-092 – W-094 E; W-299, W-294 · (wait run logs); W-307, W-308 K; W-311 · (after Phase 10, widened)
Next step       : W-092: read TY 2025-26 ITR-1 (Gazette 2026/271423 pp.16 on) and ITR-2 (2026/271435 pp.38 on) schedule layouts into a sources file; then plan the schedule rows (probe first)
Owner actions   : after deploy: §3.24 then redrive per listed user (W-316); §3.22 then §2.12 per listed user (W-313); §3.23 (W-312); §3.21 (W-304); §3.20 (W-303); §3.18, §3.19, then §2.14 (W-297, once; backup first) and its per-user §2.12 → §2.11; §3.17 → §2.13 → per user §2.12 → §2.2 → §2.11; §3.16; Q-1 (PS-8), Q-2 (AMC periods); rotate the Mongo password and JWT key pasted in session 82
Last verify     : W-091 targeted 499/0, route 2/0, validate clean; pipeline run for the push to be recorded in D-653
Files touched   : none (all committed)
Last item commit: taxplanning W-091: choose the ITR form for a computed return, with every form's verdict
Updated         : 2026-11-13, session 91 — W-091 closed and pushed
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
