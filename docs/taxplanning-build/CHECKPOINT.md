# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-184 — closed. Item 90ce4598
Stage           : 6 of 6 (REVIEWED) — nothing in flight
Plan            : plans/W-184.md — built as C1a, C2a, C3a, C4a, C5a
Approved        : "W-184 approved C1a C2a C3a C4a C5a"; "commit it" for the item (2026-10-31)
Steps done      : the item — `SectionRef.namedBy`, the two claims, the baseline (96 listed / 0 orphaned),
                  16 listings added across 11 rows, four deliberate violations each seen failing and
                  reverted (D-422)
Next step       : "start W-185" opens the next item at EXPLAIN. Remember R15: if that item changes the
                  deploy runbook file, republish WL-A-2 after its commit and read it back
Last verify     : clean install green; unit 3832 / 0 / 0; integration 323 / 0 / 0 (OrbStack); doc guards
                  `FinishedPlanTest,CoverageBacklogTest` 9 / 0 / 0 after the doc edits; no relock; no R15
                  (nothing in the deploy runbook changes)
Waiting on      : the owner — this file's commit, then W-185. Still open from before: the resume check's
                  grep wording; OQ-32, OQ-33, OQ-34, OQ-35; the BSE segments for W-208 and the plan
                  mechanism for W-210
Files touched   : this file and STATE.md only. Committed: 90ce4598 (the item: 8 files — SectionRef.java,
                  content/coverage.json, CoverageCitationTest.java, citation-baseline.json, the W-184 plan,
                  the WI row, STATE, this file)
Last item commit: taxplanning W-184: check the coverage register's listings against the rules' citations in both directions — a MODELLED row could list a rule that never names its section and no build step saw it, so `namedBy` reads every section a provision names and `CoverageCitationTest` makes each pairing rest on a citation or a stated reason, and the eleven orphaned rules are given the row whose section their rule names (D-422)
Updated         : 2026-10-31, session 43 — W-184 closed on 90ce4598; the tree is clean but for this file and
                  STATE, which the state-line commit carries
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.

*Known false mismatch:* after a docs-only post-commit state line, the grep returns that state-line commit
rather than the item commit, because the state lines carry the item prefix too. Compare the **item** commit
named above; the tree, not the grep, is what decides. It fired for W-179's planning (`f461363c` against
`866f3dd2`), for W-181's planning (`c620ba99` against `c76dad9a`), for W-182's planning (`592ff6a6` against
`e494326f`), for W-183's planning (`ac3a9fce` against `ecf681cd`) and for W-184's planning (`235d2566`
against `f511544a`).
