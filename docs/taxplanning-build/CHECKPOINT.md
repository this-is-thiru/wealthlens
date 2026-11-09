# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : none in flight — W-090c2 done (D-529), pushed; D-530 placed W-263, W-262, W-264a – d before W-090d (not committed)
Stage           : 6 → next: plan W-263 (§2 template), ask for approval; STOP before building
Done today      : session 81: W-090b, W-260, W-090c1a, W-090c1b1; session 82: W-090c1b2 (D-528), W-090c2 (D-529)
Board           : W-090c2 D; W-261 K; W-263 ·; W-262 ·; W-264 —; W-264a ·; W-264b ·; W-264c ·; W-264d ·; W-090d ·
Next step       : resume at W-263 (500s for a caller's mistake): run the resume check, read ControllerAdviser and grep every binding failure, then plan it; a local app may still run on 127.0.0.1:8080 (debug 5005) with container wl-local-mongo — stop both when the owner is done
Owner actions   : rotate the Mongo password and JWT key pasted in session 82; adopt the 13 questions' wording
Last verify     : W-090c2 unit 5103 / 0 / 0, integration 539 / 0 / 0; probes caught (I20 1 fail; boundary 1 fail with lock matched), reverted byte for byte
Files touched   : 03-WORK-ITEMS.md (W-262 – W-264d rows), STATE.md (board, D-530) — placement, to commit with W-263
Last item commit: taxplanning W-090c2: ship TY 2026-27's ITR-1 and ITR-2 as form rows
Updated         : 2026-11-09, session 82 — all pushed at f5bef5b0; D-530 placed W-263, W-262, W-264a – d; next W-263
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
