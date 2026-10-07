# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : — (W-075d0 DONE)
Stage           : between items
Plan            : —
Approved        : —
Steps done      : —
Next step       : run the resume check; then WAIT for the owner's pick. Backlog order: W-075d1 (80C/80CCC/80CCD/80CCE, TY 2025-26) → d2 (80D) → d3 (80DD/80DDB/80U) → d4 (80E–80EEB) → d5 (80CCH/80GG/80G) — all sourced in sources/2026-10-07-chapter-via.md; then W-075a3 (OQ-16/17), W-076. W-073c (needs S.O. 1046(E), a local PDF). W-075c5 after W-079. Proposed, not placed: an 80D catalogue entry; a 22-claim-catalogue erratum (s.133(6) reaches (1)(b)(ii) only)
Last verify     : ./mvnw clean test -pl backend -Punit → 2464 pass, 0 fail, 0 Checkstyle violations (W-075c6, 2026-10-07; W-075d0 was docs only)
Waiting on      : the owner — next pick. Open: OQ-1, 2, 5–8, 10–13, 15–24. D-054 startup wiring UNOWNED. Carried: TY 2025-26 goldens PROVISIONAL (its three missing Acts now located — 209695, 214648, 252143 — but read only for Chapter VI-A); DIS-S202-2A, SAL-HRA-006, SAL-LTA-CHILD-001 owed; D-104, D-105 proposed
Files touched   : —
Last item commit: taxplanning W-075d0: record the 1961 Chapter VI-A as it stood for AY 2026-27 — the 2019 text and nine amending Acts read in the Gazette, for the TY 2025-26 rows (D-143)
Updated         : 2026-10-07, session 12
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
