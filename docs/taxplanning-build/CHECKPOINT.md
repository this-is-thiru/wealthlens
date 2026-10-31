# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-088g2 — a land or building capital asset. Done (D-425), committed 927b2581
Stage           : 6 of 6 (REVIEWED) — nothing in flight
Plan            : plans/W-088g2.md — built as approved (C1a, C2a, C3a, C4a), with three deviations
                  recorded there: the boundary pair corrected to 24 / 25 months, the two part rows
                  listed in the MODELLED coverage rows, and two more count assertions moved
Approved        : "W-088g2 approved C1a C2a C3a C4a", then "commit it" (2026-10-31)
Steps done      : all ten — the enum constant, three rules × 2 versions, both notes, RuleIds, relock
                  2025-26.law.120 / 2026-27.law.123, the count and id lists, the coverage listings,
                  LandOrBuildingCapitalGainsTest (9), three deletion probes (9 / 5 / 6) reverted with
                  sha256 verified, the WI rows, the board `D`, D-425, STATE 7–8
Next step       : W-088g — the cap itself (S2 s.197(3) / 1961 s.112(1)(a) second proviso), whose asset
                  kind now exists; start at READ / EXPLAIN. No R15 anywhere in this split so far — the
                  deploy runbook does not change, WL-A-2 not republished
Last verify     : ./mvnw test -pl backend -Punit → 3841 run, 0 failures, 0 errors, BUILD SUCCESS;
                  Checkstyle and spotless green; doc guards FinishedPlanTest 5 / CoverageBacklogTest 4.
                  Integration tier not run — nothing stored, seeded or wired changed
Waiting on      : the owner — whether W-088g is the next item to start. Still open from before: the
                  resume check's grep wording; OQ-32 – OQ-37; the BSE segments for W-208 and the
                  plan mechanism for W-210
Files touched   : this file and STATE.md only. Committed: 927b2581 (the item: 20 files — the enum, three
                  rules, two notes, coverage, both locks, RuleIds, five test files, the new golden,
                  the WI rows, STATE, the plan)
Last item commit: taxplanning W-088g2: add the land-or-building asset kind with its twenty-four-month term and its part rows — so a land sale is charged at 12.5 % instead of refused, because the bucket meets the term's refusal before the part's and no STT condition can ever hold for it (D-425)
Updated         : 2026-10-31, session 43 — W-088g2 committed as 927b2581; the tree is clean but for
                  this file and STATE, which the state-line commit carries
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
(`c59d8223` against `c030e04f`), W-088g1's (`1f975262` against `f3027724`) and W-088g2's (this state
line against `927b2581`).
