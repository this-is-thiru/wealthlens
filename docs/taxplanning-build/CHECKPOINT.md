# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-288 (mutation score of the tax-planning engine) — next by board order; nothing written
Stage           : W-287 DONE (D-572), committed and pushed; W-288 not started
Done today      : session 85: W-287 built and committed (D-572); W-288 placed (D-573)
Board           : W-287 D; W-288 ·; W-283 – W-285 ·; W-264b – d ·; W-266 ·; W-090d ·
Next step       : resume: run the resume check below; then plan W-288 (why taxplanning.engine's mutants live; tests to lift the profile to 85), STOP for approval
Owner actions   : answer Q-1 (PS-8); run runbook 3.10 – 3.15 after deploy; rotate the Mongo password and JWT key pasted in session 82; adopt the 13 questions' wording
Last verify     : unit tier 5,176 / 0; W-287 scoped PIT 99 %; whole mutation profile 79 % (W-288)
Files touched   : none
Last item commit: taxplanning W-287: put the trade ledger under mutation testing
Updated         : 2026-11-12, session 85 — W-287 done; W-288 next
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
