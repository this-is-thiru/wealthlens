# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-065 — SalaryIncomeNode (next in table order) — BUT W-064a is unplaced
Stage           : NOT_STARTED
Plan            : —
Approved        : —
Steps done      : —
Next step       : ASK the owner where W-064a (entertainment allowance, D-094) goes — before W-065 or later (R4: I do not reorder). Then READ the chosen item's row and write its plan; STOP for approval
Last verify     : ./mvnw clean test -pl backend -Punit → 1918 pass, 0 fail, 0 Checkstyle violations (W-064)
Waiting on      : owner: W-064a placement; W-052d due before Phase 9; D-054 startup wiring UNOWNED. OQ-1, OQ-2, OQ-5 open. Carried: TY 2025-26 goldens log PROVISIONAL (W-062's two UNVERIFIED versions); DIS-S202-2A owed (Appendix F)
Files touched   : —
Last item commit: taxplanning W-064: add the cash-salary components — the pay lines that make gross salary and the salary bases real (D-094)
Updated         : 2026-09-27, session 9
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
