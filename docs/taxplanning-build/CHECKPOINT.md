# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-069 — modifies ExemptionNode: allowances
Stage           : NOT_STARTED
Plan            : — (plans/W-069.md once written)
Approved        : —
Steps done      : —
Next step       : READ the W-069 row (DR 01 R11; "SAL-ALW-NEW-SUBSET-001 is STATUS UNCLEAR — seed from the section, flag visibly") with rulebook R11 (SAL-ALW-DUTY-001, -PERS-001, -DUTY-LIST-2026, -NEW-001, -NEW-SUBSET-001, SAL-ALW-LIMITS-2025/2026), STATE's W-045d2 authoring note (1) on r.280(3) "opt-out" wording, S4 r.280, 1961 s.10(14) + r.2BB, the five allowance components D-094 deferred here (CEA, hostel, uniform, conveyance, transport-disabled), W-066 CompletedMonths/CalendarMonths for per-month caps; write plans/W-069.md with the D-043 standing-rules line; STOP for approval
Last verify     : ./mvnw clean test -pl backend -Punit → 2020 pass, 0 fail, 0 Checkstyle violations (W-068)
Waiting on      : nothing for W-069; W-052d due before Phase 9; D-054 startup wiring UNOWNED. OQ-1, OQ-2, OQ-5, OQ-6 open. Carried: TY 2025-26 goldens PROVISIONAL (W-062); DIS-S202-2A, SAL-HRA-006, SAL-LTA-CHILD-001 owed; W-066a before W-070; W-064a after W-070
Files touched   : —
Last item commit: taxplanning W-068: add the leave travel concession — per journey at the fare admitted, two per calendar block, old regime only (D-100)
Updated         : 2026-09-27, session 9
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
