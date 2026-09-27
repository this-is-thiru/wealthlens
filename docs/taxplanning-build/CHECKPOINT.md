# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-068 — modifies ExemptionNode: LTA
Stage           : NOT_STARTED
Plan            : — (plans/W-068.md once written)
Approved        : —
Steps done      : —
Next step       : READ the W-068 row (DR 01 R10; "calendar blocks; four independent caps") with rulebook R10 SAL-LTA-*, DR 13 R-12 (blocks 2022-2025 / 2026-2029 straddle the shipped years), S4 r.278 and 1961 s.10(5) + r.2B, the LTA component (W-064), ExemptionNode (W-067), M3.4 ledgers for block state; write plans/W-068.md with the D-043 standing-rules line; STOP for approval
Last verify     : ./mvnw clean test -pl backend -Punit → 1996 pass, 0 fail, 0 Checkstyle violations (W-067)
Waiting on      : nothing for W-068; W-052d due before Phase 9; D-054 startup wiring UNOWNED. OQ-1, OQ-2, OQ-5, OQ-6 open. Carried: TY 2025-26 goldens PROVISIONAL (W-062); DIS-S202-2A, SAL-HRA-006 owed; W-066a before W-070; W-064a after W-070
Files touched   : —
Last item commit: taxplanning W-067: add the HRA exemption — per period of occupation, the least of three limbs, old regime only (D-099)
Updated         : 2026-09-27, session 9
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
