# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-062 — SalaryBaseNode
Stage           : NOT_STARTED
Plan            : — (plans/W-062.md once written)
Approved        : —
Steps done      : —
Next step       : READ the W-062 row (DR 01 SAL-DEF-HRA-001 etc.; "basic + DA where the terms provide; one function, five rules"), rulebook R7 SAL-DEF-HRA/GRAT/NPS/80C-NPS/RPF-001 and the H-4 verdict; SalaryComponent has no daFormsPartOfRetirementBenefits field yet (D-090: added by the item that reads it — this one); which rules exist (none of SAL-DEF-HRA-* are rows; their consumers are W-067 HRA, gratuity, NPS W-076…) — so the D-084/D-089 ordering question will recur: a base with no consuming rule; write plans/W-062.md with the D-043 standing-rules line; STOP for approval
Last verify     : ./mvnw clean test -pl backend -Punit → 1878 pass, 0 fail, 0 Checkstyle violations (W-061)
Waiting on      : nothing for W-062; W-052d due before Phase 9; D-054 startup wiring UNOWNED. OQ-1, OQ-2 open
Files touched   : —
Last item commit: taxplanning W-061: add gross salary — the total of the SALARIES head, as the Act's definition of salary states (D-091)
Updated         : 2026-09-26, session 8
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
