# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-311 done (D-659), committed and pushed; api-collection 1733ad3
Stage           : closed (owner "commit it and push", session 92); watch the push's pipeline and record its run in D-659
Done today      : session 92: W-311 (D-659)
Board           : W-311 D; Phase 10 left: W-299, W-294 · (wait run logs §2.14 / §2.13); W-307, W-308 K; Phase 12 next: W-112a
Next step       : confirm the pipeline for the W-311 push and record it in D-659; then W-112a by board order (or W-299 / W-294 once the owner has the run logs)
Owner actions   : after deploy: §3.24 then redrive per listed user (W-316); §3.22 then §2.12 per listed user (W-313); §3.23 (W-312); §3.21 (W-304); §3.20 (W-303); §3.18, §3.19, then §2.14 (W-297, once; backup first) and its per-user §2.12 → §2.11; §3.17 → §2.13 → per user §2.12 → §2.2 → §2.11; §3.16; Q-1 (PS-8), Q-2 (AMC periods); rotate the Mongo password and JWT key pasted in session 82
Last verify     : W-311 targeted 133 / 0, validate 0; pipeline pending
Files touched   : none beyond this file after the commit
Last item commit: taxplanning W-311: remove the test routes — the sale replay, /request — and serve /request2's asset filter at /assets/search
Updated         : 2026-11-13, session 92 — W-311 closed and pushed
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
