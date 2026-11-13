# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-294 — next by board order (W-299 waits for §2.14's run log); nothing written
Stage           : W-290 DONE (stage 6, D-610), committed and pushed; W-294 not started
Done today      : session 87: W-302 (695ac428, run 43358981189 success, D-609), W-290 (D-610) done and pushed
Board           : W-290 D; W-299 · (waits for §2.14's run log, WL-46); W-294 · next; W-264b – d ·; W-266 ·; W-090d ·; W-149 B (waits for §2.3 on prod)
Next step       : record W-290's pipeline run in D-610; then plan W-294, STOP for approval
Owner actions   : after deploy: §3.18, §3.19 (read-only), then §2.14 (W-297, once; backup first) and its per-user §2.12 → §2.11; §3.17 → §2.13 → per user §2.12 → §2.2 → §2.11; §3.16; Q-1 (PS-8); rotate the Mongo password and JWT key pasted in session 82; adopt the 13 questions' wording
Last verify     : W-290 local: TransactionServiceTest 6, TransactionServiceIdempotencyTest 12, TransactionIntegrationTest 7, 0 failures; validate 0; pipeline pending
Files touched   : none (all committed with W-290)
Last item commit: taxplanning W-290: remove the uncalled TransactionService.addTransaction
Updated         : 2026-11-13, session 87 — W-290 done; next W-294
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
