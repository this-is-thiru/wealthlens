# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-197 — BUILT, awaiting the owner's instruction to commit
Stage           : code and docs done; unit 3819/0, integration 319/0; staged, not committed
Plan            : plans/W-197.md (C1a remove the unused `PaidOtherThanInCash` and its plumbing,
                  C2 correct the two counts)
Approved        : "W-197 approved C1a C2" (2026-10-31)
Steps done      : 1–3 (build, probe and both tiers, close-out docs); the commit is not done
Next step       : on the owner's instruction, commit the named paths; then start W-198 (the two
                  one-row regime sets no node reads — keep with a reason, or fold): the resume check,
                  the board order, the row and its named docs, plans/W-198.md, the board `·` → `E`, STOP
Last verify     : W-197: unit 3819/0, integration 319/0, dump diff 0 of 338,990 (2026-10-31)
Waiting on      : the owner — the commit of W-197; OQ-32, OQ-33, OQ-34 open; W-206 (request DTOs)
                  and W-207 (a non-premium's multi-year sum) placed · at the queue's end
Files touched   : Condition.java, ConditionContext.java, ConditionJson.java,
                  MedicalTreatmentAbroadNode.java, PaidOtherThanInCash.java (deleted),
                  ConditionTest.java, PackJsonTest.java, TaxYear2025RulePackTest.java,
                  RegimeLifecycleTest.java, 02-TECH-SPEC.md, 03-WORK-ITEMS.md, STATE.md,
                  this file, plans/W-197.md (new)
Last item commit: a6c7f8c8 taxplanning W-196: record the post-commit state — CHECKPOINT moves to W-197, and the last checkpoint names the commit that now exists
Updated         : 2026-10-31, session 42 — W-197 built and staged; the commit is the owner's to call
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
