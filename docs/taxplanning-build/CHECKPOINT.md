# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-088g2 — a land or building capital asset. CODE, VERIFY and REPORT done (D-425)
Stage           : 6 of 6 (REVIEWED) — awaiting the owner's word to commit
Plan            : plans/W-088g2.md — built as approved (C1a, C2a, C3a, C4a), with three deviations
                  recorded there: the boundary pair corrected to 24 / 25 months, the two part rows
                  listed in the MODELLED coverage rows, and two more count assertions moved
Approved        : "W-088g2 approved C1a C2a C3a C4a" (2026-10-31)
Steps done      : the enum constant; three rules × 2 versions; the RATE-CG-LONG-OTHER and RS-CG-TERM
                  notes; RuleIds regenerated; relock 2025-26.law.120 / 2026-27.law.123; five hand-kept
                  count and id lists; the coverage listings; LandOrBuildingCapitalGainsTest (9); three
                  deletion probes (9 / 5 / 6 failures) reverted with sha256 verified; the WI rows,
                  the board `D`, D-425, STATE 7–8
Next step       : the commit on the owner's word, then the post-commit state line pointing at W-088g.
                  No R15: the deploy runbook does not change, WL-A-2 not republished
Last verify     : ./mvnw test -pl backend -Punit → 3841 run, 0 failures, 0 errors, BUILD SUCCESS;
                  Checkstyle and spotless green. Integration tier not run — the plan scoped unit only,
                  and nothing stored, seeded or wired changed
Waiting on      : the owner — the commit. Still open from before: the resume check's grep wording;
                  OQ-32 – OQ-37; the BSE segments for W-208 and the plan mechanism for W-210
Files touched   : AssetType.java, RuleIds.java, RATE-CG-LONG-OTHER.json, rule-sets.json, coverage.json,
                  both locks, the three new rule files, the four count and id test files, the new
                  golden, 03-WORK-ITEMS.md, STATE.md, plans/W-088g2.md, this file
Last item commit: taxplanning W-088g1 … (D-424) — f3027724, then the state line 1f975262; W-088g2
                  has no commit yet
Updated         : 2026-10-31, session 43 — W-088g2 built and verified; the tree holds the item
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.

*Known false mismatch:* once the post-commit state line exists, the grep returns **that** commit rather
than the item commit named above, because state lines carry the item prefix too. Compare the **item**
commit; the tree, not the grep, is what decides. It has fired for W-179's planning (`f461363c` against
`866f3dd2`), W-181's (`c620ba99` against `c76dad9a`), W-182's (`592ff6a6` against `e494326f`), W-183's
(`ac3a9fce` against `ecf681cd`), W-184's (`235d2566` against `f511544a`, then `a0fe2665`), W-088g's
(`c59d8223` against `c030e04f`) and W-088g1's (`1f975262` against `f3027724`).
