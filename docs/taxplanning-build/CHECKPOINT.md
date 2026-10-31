# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-088g — split and placed, closed (D-423). Committed c030e04f
Stage           : 6 of 6 (REVIEWED) — nothing in flight
Plan            : plans/W-088g.md — approved C1a, C2a, C3a; built nothing
Approved        : "W-088g approved C1a C2a C3a"; "commit it" for the item (2026-10-31)
Steps done      : the whole item — the law read verbatim in both Acts, the source file, the two
                  placements (W-088g1 the index years, W-088g2 the asset kind with its part row),
                  OQ-37 and OQ-36 raised, D-423, the board `W-088g —`, the WI rows, STATE
Next step       : "start W-088g1" — the Cost Inflation Index from 2001-02, at EXPLAIN. The order
                  against W-088g2 is the owner's to set; W-088g1 is the board's first open item.
                  No R15 here: the deploy runbook does not change, so WL-A-2 is not republished
Last verify     : doc guards `FinishedPlanTest,CoverageBacklogTest` 9 / 0 / 0 after the close-out,
                  and the plan guard proven able to fail (an open step added, `W-088g:` named,
                  reverted, `cmp` clean); no unit or integration run — no backend path changed
Waiting on      : the owner — this file's commit, then W-088g1. Still open from before: the resume
                  check's grep wording; OQ-32 – OQ-37; the BSE segments for W-208 and the plan
                  mechanism for W-210
Files touched   : this file and STATE.md only. Committed: c030e04f (the item: 5 files — the plan,
                  the source, the WI rows, STATE, CHECKPOINT)
Last item commit: taxplanning W-088g: split the land-or-building cap into the index years and the asset kind — the provision is expressible in the pack's own vocabulary, but nothing can reach it: no AssetType is land or building, all five CG-PART rows require EQUITY, and 22 years of the Cost Inflation Index are absent (D-423)
Updated         : 2026-10-31, session 43 — W-088g committed as c030e04f; the tree is clean but for
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
(`ac3a9fce` against `ecf681cd`) and W-184's (`235d2566` against `f511544a`, then `a0fe2665`).
