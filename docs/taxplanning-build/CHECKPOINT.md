# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-316 (a rights issue or buyback blocks trades but can never be performed, G-5) — next by board order; needs the owner's product call
Stage           : batch W-313, W-312, W-266 DONE (D-637 – D-639), committed and pushed; W-316 not started
Done today      : session 89: Phase 10 frozen; W-304, W-305, W-306, W-309, W-264c; batches W-317/W-315/W-314 and W-313/W-312/W-266 (D-622 – D-639)
Board           : W-313, W-312, W-266 D; W-316 · next; W-264d ·; W-090d ·; W-091 – W-094 ·; W-299, W-294 · (wait run logs); W-307, W-308 K; W-311 · (after Phase 10)
Next step       : record the batch's pipeline run in D-637; plan W-316 (process rights/buyback, or stop holding trades for them: owner's call) with a probe run first, STOP
Owner actions   : after deploy: §3.22 then §2.12 per listed user (W-313), §3.23 (W-312), §3.21 (W-304), §3.20 (W-303), read-only, each database; §3.18, §3.19, then §2.14 (W-297, once; backup first) and its per-user §2.12 → §2.11; §3.17 → §2.13 → per user §2.12 → §2.2 → §2.11; §3.16; Q-1 (PS-8), Q-2 (AMC periods); rotate the Mongo password and JWT key pasted in session 82
Last verify     : batch: red 4; 14 classes 316 / 0; probes 1 and 2 fail, restored; §3.22, §3.23 on a throwaway mongo as by hand; WL-A-2 read back; validate 0; guards 0
Files touched   : none (all committed)
Last item commit: taxplanning W-266: name the Excel classes and the shared-to-portfolio imports CLAUDE.md describes
Updated         : 2026-11-13, session 89 — batch done; next W-316
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
