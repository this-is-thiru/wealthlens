# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : — (W-075d5 DONE; W-075d complete)
Stage           : between items
Plan            : —
Approved        : —
Steps done      : —
Next step       : run the resume check; then WAIT for the owner's pick. Backlog order: W-075a3 (Schedule XV (c), (j), (k), (l) — waits on OQ-16 (which salary for (c); 1961 80C(2)(iii) "one-fifth of the salary") and OQ-17 (para 2's reach)) → W-076 (ChapterEightNode) → W-077 … ; W-073c (needs S.O. 1046(E), a local PDF); W-075c5 after W-079. Proposed, not placed: an 80D catalogue entry; a 22-claim-catalogue erratum (s.133(6) reaches (1)(b)(ii) only). Relock helper: scratchpad relock.sh (recreate per memory if a new session)
Last verify     : ./mvnw clean test -pl backend -Punit → 2481 pass, 0 fail, 0 Checkstyle violations (W-075d5, 2026-10-07)
Waiting on      : the owner — next pick; W-075a3 needs answers to OQ-16, OQ-17. Open: OQ-1, 2, 5–8, 10–13, 15–24. D-054 startup wiring UNOWNED. Carried: TY 2025-26 goldens PROVISIONAL (its three missing Acts located — 209695, 214648, 252143 — read only for Chapter VI-A); DIS-S202-2A, SAL-HRA-006, SAL-LTA-CHILD-001 owed; D-104, D-105 proposed
Files touched   : —
Last item commit: taxplanning W-075d5: compute 80CCH, 80GG and 80G for TY 2025-26 under the 1961 Act, PM CARES read in TOLA 2020 — every Chapter VIII section now computes for both years (D-148)
Updated         : 2026-10-07, session 12
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
