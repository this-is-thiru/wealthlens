# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : none in flight — W-263 done (D-532), committed and pushed on the owner's "commit it and push"
Stage           : 6 → next: plan W-262 (§2 template), ask for approval; STOP before building
Done today      : session 83: W-265 marked D on the owner's word (CI green on d86d8012); W-263 planned, approved, built, committed
Board           : W-265 D; W-263 D; W-262 ·; W-264 —; W-264a – d ·; W-090d ·
Next step       : resume at W-262 (held assets by term, with Excel): run the resume check, read the W-262 row, D-370, D-444 and CapitalGainsViewService, then plan it; a local app may still run on 127.0.0.1:8080 (debug 5005) with container wl-local-mongo — stop both when the owner is done
Owner actions   : rotate the Mongo password and JWT key pasted in session 82; adopt the 13 questions' wording
Last verify     : W-263 unit 5140 / 0 / 0; CI's verify in a worktree 5692 / 0 / 0, all coverage checks met; probes caught (15 red before the change; asOf 1 fail), restored
Files touched   : none — all committed
Last item commit: taxplanning W-263: answer a caller's mistake with its own status
Updated         : 2026-11-11, session 83 — W-263 committed and pushed; next W-262
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
