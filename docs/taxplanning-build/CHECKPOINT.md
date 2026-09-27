# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-064 — salary.json law rows
Stage           : NOT_STARTED
Plan            : — (plans/W-064.md once written)
Approved        : —
Steps done      : —
Next step       : READ the W-064 row (DR 01 R7, R8) with its errata: D-089 (cash-salary components come here, both years, cashNature sourced), D-093 (SAL-STDDED-* moved to W-063; W-064 keeps the cash-salary components and SAL-ENTMT-001), D-092 (components' salaryBaseFor with onlyWhereTermsProvide; commission in the salary base is W-064's data decision — Gestetner), D-093 (each pay component must feedsInto SAL-SALARY-INCLUDES to count in gross); write plans/W-064.md with the D-043 standing-rules line; STOP for approval
Last verify     : ./mvnw clean test -pl backend -Punit → 1908 pass, 0 fail, 0 Checkstyle violations (W-063)
Waiting on      : nothing for W-064; W-052d due before Phase 9; D-054 startup wiring UNOWNED. OQ-1, OQ-2 open. Carried: TY 2025-26 goldens log PROVISIONAL (W-062's two UNVERIFIED salary-base versions); DIS-S202-2A owed (Appendix F)
Files touched   : —
Last item commit: taxplanning W-063: add the deductions from salary — professional tax outside the new regime, and the standard deduction capped at salary (D-093)
Updated         : 2026-09-27, session 9
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
