# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : none in flight — W-262 done (D-533), committed and pushed on the owner's "commit it and push"
Stage           : 6 → next: plan W-264a (§2 template), ask for approval; STOP before building
Done today      : session 83: W-265 marked D; W-263 (269ff30a) and W-262 committed and pushed; W-266 placed
Board           : W-263 D; W-262 D; W-264 —; W-264a ·; W-264b ·; W-264c ·; W-264d ·; W-266 ·; W-090d ·
Next step       : resume at W-264a (gap audit: portfolio — V1 and V2 buy and sell, FIFO lots, holdings views, temporary transactions and redrive, Excel import and export, idempotency): run the resume check, read the W-264 and W-264a rows and D-530, then plan the audit; a local app may still run on 127.0.0.1:8080 (debug 5005) with container wl-local-mongo — stop both when the owner is done
Owner actions   : check the pipeline on the W-262 commit (full tiers were not run locally); rotate the Mongo password and JWT key pasted in session 82; adopt the 13 questions' wording
Last verify     : W-262 targeted unit 156 / 0 / 0, integration (HeldAssets + DraftComputation) 48 / 0 / 0; probes caught (11, 1, 3 fails), restored; the commit gate's unit tier
Files touched   : none — all committed
Last item commit: taxplanning W-262: list held assets by term, with Excel
Updated         : 2026-11-11, session 83 — W-262 committed and pushed with api-collection; next W-264a
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
