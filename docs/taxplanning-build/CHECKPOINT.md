# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-069b — transport allowance for a disabled employee
Stage           : NOT_STARTED
Plan            : — (plans/W-069b.md once written)
Approved        : —
Steps done      : —
Next step       : READ the W-069b row (needs OQ-7: "Metro Cities" and "plus dearness allowance thereon" in S4 r.280(2) Sl. 10; the employee's disability; TY 2025-26 r.2BB(2) Sl. 11 ₹3,200 — read in the CBDT consolidation, S.O. 1002(E) 13-4-2015 and S.O. 2604(E) 23-9-2015 reported); Assessee disability field; write plans/W-069b.md; STOP for approval
Last verify     : ./mvnw clean test -pl backend -Punit → 2038 pass, 0 fail, 0 Checkstyle violations (W-069)
Waiting on      : OQ-7 (for W-069b); W-052d due before Phase 9; D-054 startup wiring UNOWNED. OQ-1, OQ-2, OQ-5, OQ-6 open. Carried: TY 2025-26 goldens PROVISIONAL (W-062); DIS-S202-2A, SAL-HRA-006, SAL-LTA-CHILD-001 owed; W-066a before W-070; W-064a after W-070
Files touched   : — (untracked 'Rule - 2BB_en.pdf' at the repo root is the owner's)
Last item commit: taxplanning W-069: add the allowance exemptions — duty allowances as spent, children's allowances per child per month (D-101)
Updated         : 2026-09-27, session 9
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
