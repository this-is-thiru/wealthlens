# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : none in flight — W-090a1 done (D-518), committed (subject in Last item commit), not pushed
Stage           : 6 → next: plan W-090a2 (§2 template), ask for approval; STOP before building
Done today      : session 80: W-090a1 planned, approved, built, verified, committed
Board           : W-090a1 D; W-090a2 ·; W-090b ·; W-090c ·; W-090d ·; W-090e K; W-258 ·; W-246 K
Next step       : resume at W-090a2: run the resume check, read plans/W-090a.md, plans/W-090a1.md and the W-090a2 row (03-WORK-ITEMS:1422). Fetch Gazette 271092 (Rules) and 265620 (Act) again (session-79 scratch copies are gone); read (l) s.393(3) Sl. 5, (m) s.391(2) / 392(3), (n) Chapter XIII Part A first. a2 needs the catalogue's answer kind from taxplanning, which sees only the engine's named interfaces, so it comes through api. W-259 placed (D-519): the synthetic generator keeps every row kind; Phase 14, board order.
Owner actions   : push review/taxplanning (W-090a1 commit unpushed); earlier standing actions unchanged (session 79 list)
Last verify     : W-090a1 unit 4989 / 0 / 0 (Checkstyle + Spotless on), integration 535 / 0 / 0
Files touched   : none — all committed
Last item commit: taxplanning W-090a1: add declarations, the questions the person answers that the engine cannot compute
Updated         : 2026-11-09, session 80 — W-090a1 committed; next W-090a2
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
