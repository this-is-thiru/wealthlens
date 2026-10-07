# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : — (W-075c6 DONE)
Stage           : between items
Plan            : —
Approved        : —
Steps done      : —
Next step       : run the resume check; then WAIT for the owner's pick. Backlog order: W-073c (needs S.O. 1046(E) — a local PDF from incometaxindia.gov.in), W-075d (TY 2025-26 Chapter VIII — needs a sourcing round: 1961 Chapter VI-A as amended to AY 2026-27), W-075a3 (OQ-16/17), W-076; W-075c5 (80TTA/TTB) after W-079. Proposed, not placed: an 80D catalogue entry; an erratum in 22-claim-catalogue (s.133(6) reaches (1)(b)(ii) only)
Last verify     : ./mvnw clean test -pl backend -Punit → 2464 pass, 0 fail, 0 Checkstyle violations (W-075c6, 2026-10-07)
Waiting on      : the owner — next pick. Open: OQ-1, 2, 5–8, 10–13, 15–24 (OQ-22 from W-075c4; OQ-23, OQ-24 from W-075c6). D-054 startup wiring UNOWNED. Carried: TY 2025-26 goldens PROVISIONAL; DIS-S202-2A, SAL-HRA-006, SAL-LTA-CHILD-001 owed; D-104, D-105 proposed
Files touched   : —
Last item commit: taxplanning W-075c6: add the deduction for donations — in full or at half, the limited sums within 10% of adjusted gross total income, before s.134, TY 2026-27 (D-142)
Updated         : 2026-10-07, session 12
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
