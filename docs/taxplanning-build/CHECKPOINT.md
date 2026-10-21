# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-088o — capital-gains parts as rule data (D-250, narrowed by D-259)
Stage           : READY — not started
Plan            : plans/W-088o.md holds the sequencing plan; the build plan is written when taken
Approved        : —
Steps done      : —
Next step       : on "start W-088o": read its WI row (D-259: CapitalGainsPart only — parts as versioned rule data stating the bucket facts each collects and whether a long-term loss may reach it, s.108(2)(b)), plans/W-088o.md, reviews/2026-10-19-branch-extendability-review.md E3, CapitalGainsPart and its readers (CapitalGainsNode, CapitalGainsAbsorption, RS-CG-PART BucketPart, rate rows), D-289 – D-305; write the build plan (a split if > §7), STOP
Last verify     : unit clean -Punit 3218 run, 0 fail; integration 281/0 (2026-10-21, W-088k4b)
Waiting on      : the owner — "start W-088o"; OQ-32, OQ-33 open
Files touched   : (none)
Last item commit: taxplanning W-088k4b: correct the bonus lots processed before W-088k4a — a one-time SUPER_USER call records each owner-supplied allotment date and re-dates the lots, transactions and their sales from the ex-date; runbook §2.4, W-150 / WL-34 (D-308)
Updated         : 2026-10-21, session 27 — PAUSED at the session limit, everything committed (resume at W-088o)
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
