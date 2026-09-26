# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-061 — GrossSalaryNode
Stage           : NOT_STARTED
Plan            : — (plans/W-061.md once written)
Approved        : —
Steps done      : —
Next step       : READ the W-061 row (DR 01 R7; rulebook SAL-CHARGE-001/002, SAL-DEF-001) and the D-089/D-090 errata; decide what gross salary sums (the SALARIES head of ComponentSet) and which rule it cites — SAL-DEF-001 has no row yet and a definition has no RuleVersion shape (W-059 finding 2; W-064 owns the rows) — so the ordering question may recur; check GoldenCaseRunner's dropped list (inputs.components, salary.gross) and how T1 enters at totalincome; write plans/W-061.md with the D-043 standing-rules line; STOP for approval
Last verify     : ./mvnw clean test -pl backend -Punit → 1863 pass, 0 fail, 0 Checkstyle violations (W-060)
Waiting on      : nothing for W-061; W-052d due before Phase 9; D-054 startup wiring UNOWNED. OQ-1, OQ-2 open
Files touched   : —
Last item commit: taxplanning W-060: add the component partition and the first entered income — an unknown code stops the run (D-090)
Updated         : 2026-09-26, session 8
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
