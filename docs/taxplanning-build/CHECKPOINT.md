# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-066c — retirement limits set by notification (D-103)
Stage           : BLOCKED — sourcing
Plan            : —
Approved        : —
Steps done      : —
Next step       : WAIT for the owner's sourcing: the gratuity, leave-encashment and retrenchment limit notifications in force for TY 2025-26 and TY 2026-27 (s.536(2)(j) carries 1961 ones forward), and any later ones. Then READ the W-066c row and write plans/W-066c.md; STOP for approval. W-066d is blocked likewise (Payment of Gratuity Act s.4, ID Act s.25F(b), labour codes). Moving to Phase 8 first needs the owner to place W-066c/d after it (R4)
Last verify     : ./mvnw clean test -pl backend -Punit → 2125 pass, 0 fail, 0 Checkstyle violations (W-064a)
Waiting on      : the owner — W-066c/d sourcing or re-placement. W-052d due before Phase 9; D-054 startup wiring UNOWNED. OQ-1, OQ-2, OQ-5, OQ-6, OQ-7, OQ-8 open. Carried: TY 2025-26 goldens PROVISIONAL (W-062, W-066a, W-064a); DIS-S202-2A, SAL-HRA-006, SAL-LTA-CHILD-001 owed; nil lines for LTA and allowances in an excluding regime proposed (D-104); EmployerKind vs taxplanning EmployerType reconciliation proposed (D-105)
Files touched   : —
Last item commit: taxplanning W-064a: add the entertainment allowance deduction — Government employees, TY 2025-26 old regime only, a year's pack keeping only its own rules (D-105)
Updated         : 2026-09-27, session 9
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
