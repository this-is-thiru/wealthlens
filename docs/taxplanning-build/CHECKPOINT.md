# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-309 (derive the charge summary from user_charges, checklist :457) — next by board order; nothing written
Stage           : W-306 DONE (D-625), committed and pushed; W-309 not started
Done today      : session 89: Phase 10 frozen (D-622), W-304 (D-623), W-305 (D-624), W-306 (D-625)
Board           : W-306 D; W-309 · next; W-307, W-308 K; W-299 · (waits §2.14 log); W-294 · (waits §2.13 log); W-264c – d ·; W-266 ·; W-090d ·; W-091 – W-094 ·
Next step       : record W-306's pipeline run in D-625; plan W-309 with a probe run first, STOP for approval
Owner actions   : after deploy: §3.21 (W-304) and §3.20 (W-303), read-only, each database; §3.18, §3.19, then §2.14 (W-297, once; backup first) and its per-user §2.12 → §2.11; §3.17 → §2.13 → per user §2.12 → §2.2 → §2.11; §3.16; Q-1 (PS-8), Q-2 (AMC periods); rotate the Mongo password and JWT key pasted in session 82
Last verify     : W-305 pipeline 43372109985 success (D-624); W-306 validate 0, compile 0
Files touched   : none (all committed)
Last item commit: taxplanning W-306: say which trade flows the charge engine prices
Updated         : 2026-11-13, session 89 — W-306 done; next W-309
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
