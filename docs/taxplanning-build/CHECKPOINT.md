# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-196 — BUILT, awaiting the owner's instruction to commit
Stage           : code, tests, pack note and docs done; unit 3821/0, integration 319/0; staged, not committed
Plan            : plans/W-196.md (C1a cash → nil line, C2a spreading stays a refusal + a row
                  placed, C3a a barred premium still bars a senior's medical expenditure)
Approved        : "W-196 approved C1a C2a C3a" (2026-10-31)
Steps done      : 1–3 (build, probes and both tiers, close-out docs); the commit is not done
Next step       : on the owner's instruction, commit the eight staged paths; then start W-197
                  (`PaidOtherThanInCash` unused: wire it to the cash bar or remove it) — the
                  resume check, the board order, plans/W-197.md, the board `·` → `E`, STOP
Last verify     : W-196: unit 3821/0, integration 319/0 (2026-10-31)
Waiting on      : the owner — OQ-32, OQ-33, OQ-34 open; W-206 (request DTOs) and W-207 (a
                  non-premium's multi-year sum) placed · at the queue's end, the owner's to place
Files touched   : HealthInsuranceDeduction.java, DED-HEALTH-CASH-PERMITTED.json,
                  HealthInsuranceDeductionTest.java, 02-TECH-SPEC.md, 03-WORK-ITEMS.md,
                  STATE.md, this file, plans/W-196.md (new)
Last item commit: taxplanning W-195: keep §2.5's export as the history and remove the old stored term labels — the label came from the duplicate law with no rule, pack or year, so it is not an audit record (D-411)
Updated         : 2026-10-31, session 42 — W-196 built and staged; the commit is the owner's to call
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
