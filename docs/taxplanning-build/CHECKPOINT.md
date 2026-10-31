# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-088g1 — the Cost Inflation Index from 2001-02. Done (D-424), committed f3027724
Stage           : 6 of 6 (REVIEWED) — nothing in flight
Plan            : plans/W-088g1.md — approved C1a (source and decide, no pack change), C2a (a
                  capital-gains-only RATE-CG-COST-INFLATION-INDEX, added by W-088g)
Approved        : "W-088g1 approved C1a C2a" (2026-10-31)
Steps done      : the whole item — every year 2001-02 … 2026-27 read in its own Gazette copy (six
                  copies hashed), the sourcing file, the two findings (widening the shared table
                  would lift PerquisiteNode's decided refusal; a rule with no reader is invisible
                  to I3), C2a decided, D-424, the board `W-088g1 D`, the WI row, STATE fields 6–8
Next step       : "start W-088g2" — the land or building asset kind with its CG-PART row. The order
                  against W-088g2 was the owner's and W-088g2 is now the board's first open item.
                  No R15 here: the deploy runbook does not change, so WL-A-2 is not republished
Last verify     : doc guards `FinishedPlanTest,CoverageBacklogTest` 9 / 0 / 0 after the close-out;
                  no unit or integration run — no backend path changed (`git diff --stat --
                  backend/src` empty)
Waiting on      : the owner — this file's commit. Still open from before: the resume check's grep
                  wording; OQ-32 – OQ-37; the BSE segments for W-208 and the plan mechanism for W-210
Files touched   : this file and STATE.md only. Committed: f3027724 (the item: 5 files — the
                  plan, the source, the WI row, STATE, CHECKPOINT)
Last item commit: taxplanning W-088g1: source the Cost Inflation Index for every year from 2001-02 and decide its shape — all 26 years now stand on a hashed Gazette copy, and the index becomes a capital-gains-only rule added with its reader, because widening the shared table would silently lift the refusal D-115 decided and a rule no node reads is invisible to the build (D-424)
Updated         : 2026-10-31, session 43 — W-088g1 committed as f3027724; the tree is clean but for
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
(`c59d8223` against `c030e04f`) and W-088g1's (this state line against `f3027724`).
