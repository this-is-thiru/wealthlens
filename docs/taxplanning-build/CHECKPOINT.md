# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-066 — ProrationBasis family
Stage           : NOT_STARTED
Plan            : — (plans/W-066.md once written)
Approved        : —
Steps done      : —
Next step       : READ the W-066 row ("DR 15 C3 — named basis per rule; no caller divides") and its Appendix E/F notes (CompletedMonths, CalendarMonths, Days; OccupationPeriods; W-066b follows); write plans/W-066.md with the D-043 standing-rules line; STOP for approval
Last verify     : ./mvnw clean test -pl backend -Punit → 1928 pass, 0 fail, 0 Checkstyle violations (W-065)
Waiting on      : nothing for W-066; W-052d due before Phase 9; D-054 startup wiring UNOWNED. OQ-1, OQ-2, OQ-5 open. Carried: TY 2025-26 goldens log PROVISIONAL (W-062); DIS-S202-2A owed (Appendix F); W-066a (retirement) before W-070; W-064a after W-070
Files touched   : —
Last item commit: taxplanning W-065: add income under Salaries — gross salary less the deductions from salary, never below nil (D-096)
Updated         : 2026-09-27, session 9
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
