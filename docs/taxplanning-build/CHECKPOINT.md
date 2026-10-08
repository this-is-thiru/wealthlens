# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : — (W-076b DONE)
Stage           : between items
Plan            : —
Approved        : —
Steps done      : —
Next step       : WAIT for the owner's pick. Backlog: W-075a3 (OQ-16/17) → W-077 (T5 goldens) → W-078 …; W-075c5 after W-079; W-074b (T4-3a/b). Proposed, not placed: PERQ-EMPLOYER-CONTRIBUTION whatToCheck refresh (interim FA 2024 searched); S.O. 1046(E) India Code PDF to make 9.5% READ; 22-claim-catalogue erratum; ss.135/137/152
Last verify     : ./mvnw clean test -pl backend -Punit → 2498 pass, 0 fail, BUILD SUCCESS (W-076b, 2026-10-08)
Waiting on      : the owner — next pick. Open: OQ-1, 2, 5–8, 10–13, 15–24. D-054 startup wiring UNOWNED. Carried: TY 2025-26 goldens PROVISIONAL; DIS-S202-2A, SAL-HRA-006, SAL-LTA-CHILD-001 owed; D-104, D-105 proposed
Files touched   : —
Last item commit: taxplanning W-076b: add catalogue entries for disability, loans, donations and rent paid — every built Chapter VIII section now marked modelled (D-152)
Updated         : 2026-10-08, session 13
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
