# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-184 — the coverage register against the rules' citations (D-422)
Stage           : 5 of 6 (REPORTED) — built, verified and staged; awaiting the owner's commit word
Plan            : plans/W-184.md — built as C1a, C2a, C3a, C4a, C5a
Approved        : "W-184 approved C1a C2a C3a C4a C5a" (2026-10-31)
Steps done      : `SectionRef.namedBy`, `CoverageCitationTest`, the baseline (96 listed / 0 orphaned), 16
                  listings added across 11 rows, four deliberate violations each seen failing and reverted;
                  D-422, the board `W-184 D`, the WI row, this plan, STATE
Next step       : the owner's "commit it" — commit the named paths, then rewrite this file for the
                  post-commit state; after that, "start W-185" opens the next item at EXPLAIN
Last verify     : clean install green; unit 3832 / 0 / 0; integration 323 / 0 / 0 (OrbStack); doc guards
                  `FinishedPlanTest,CoverageBacklogTest` re-run after these edits; no relock; no R15
                  (nothing in the deploy runbook changes)
Waiting on      : the owner — this item's commit. Still open from before: the resume check's grep wording;
                  OQ-32, OQ-33, OQ-34, OQ-35; the BSE segments for W-208, the plan mechanism for W-210
Files touched   : SectionRef.java, content/coverage.json, CoverageCitationTest.java, the citation baseline,
                  plans/W-184.md, 03-WORK-ITEMS.md, STATE.md, this file — all uncommitted
Last item commit: taxplanning W-183: add a build guard for unread private fields and constants — no stock tool sees them in this codebase (PMD ignores annotated classes, SpotBugs ignores inlined constants), so an ArchUnit rule plus a source scan does, and its first run removed eight dead members (D-421)
Updated         : 2026-10-31, session 43 — W-183 closed on f511544a and 235d2566; W-184 built and verified,
                  awaiting the owner's commit word
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
