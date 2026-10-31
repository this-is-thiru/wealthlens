# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-088g — split and placed, closed for now (D-423)
Stage           : 6 of 6 (REVIEWED) — nothing in flight
Plan            : plans/W-088g.md — approved C1a, C2a, C3a; built nothing
Approved        : "W-088g approved C1a C2a C3a" (2026-10-31)
Steps done      : the whole item — the law read verbatim in both Acts, the source file, the two
                  placements (W-088g1 the index years, W-088g2 the asset kind with its part row),
                  OQ-36 and OQ-37 raised, D-423, the board `W-088g —`, the WI rows, STATE
Next step       : "start W-088g1" — the Cost Inflation Index from 2001-02, at EXPLAIN. The order
                  against W-088g2 is the owner's to set; W-088g1 is the board's first open item.
                  Remember R15: no deploy-runbook change here, so WL-A-2 is not republished
Last verify     : doc guards `FinishedPlanTest,CoverageBacklogTest` — result in the report; no unit
                  or integration run (no backend path changed)
Waiting on      : the owner — the commit word. Still open from before: the resume check's grep
                  wording; OQ-32 – OQ-37; the BSE segments for W-208 and the plan mechanism for W-210
Files touched   : plans/W-088g.md, sources/2026-10-31-land-building-cap.md (both new),
                  03-WORK-ITEMS.md, STATE.md (D-423, board, fields 7 and 8), this file
Last item commit: taxplanning W-184: record the post-commit state — CHECKPOINT names 90ce4598 as the commit that now exists and points at W-185, so the next session resumes on a clean tree (D-422)
Updated         : 2026-10-31, session 43 — W-088g closed by D-423; the tree is clean but for the
                  five documents above
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.

*Known false mismatch:* after this item's commit, the grep returns that item commit or a later state
line rather than the one above. Compare the **item** commit named above; the tree, not the grep, is
what decides. It has fired for W-179's planning (`f461363c` against `866f3dd2`), W-181's
(`c620ba99` against `c76dad9a`), W-182's (`592ff6a6` against `e494326f`), W-183's (`ac3a9fce` against
`ecf681cd`), W-184's (`235d2566` against `f511544a`) and W-184's state line (`a0fe2665` against
`90ce4598`).
