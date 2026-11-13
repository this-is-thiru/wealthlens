# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-304 (a trade no broker card covers is reported as priced, G-1) — next by board order; nothing written
Stage           : W-303 DONE (stage 6, D-619), committed and pushed; W-304 not started; Q-2 (AMC periods) still open
Done today      : session 87: W-302, W-290, W-264b done and pushed (D-609 – D-618); W-303 built (D-619)
Board           : W-303 D; W-304 · next; W-305 – W-309 ·; W-299 · (waits §2.14 log); W-294 · (waits §2.13 log); W-264c – d ·; W-266 ·; W-090d ·; W-149 B
Next step       : record W-303's pipeline run in D-619; then plan W-304 (first test: W-264b report §6.2), STOP for approval
Owner actions   : after deploy: §3.20 (W-303, read-only, each database); §3.18, §3.19 (read-only), then §2.14 (W-297, once; backup first) and its per-user §2.12 → §2.11; §3.17 → §2.13 → per user §2.12 → §2.2 → §2.11; §3.16; Q-1 (PS-8), Q-2 (AMC periods); rotate the Mongo password and JWT key pasted in session 82; adopt the 13 questions' wording
Last verify     : W-303 local: 7 charge classes 219 / 0; validate 0; pipeline pending
Files touched   : none (all committed with W-303)
Last item commit: taxplanning W-303: charge exit load on the redemption value
Updated         : 2026-11-13, session 87 — W-303 done; next W-304
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
