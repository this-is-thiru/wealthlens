# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : — (W-075d3 DONE)
Stage           : between items
Plan            : —
Approved        : —
Steps done      : —
Next step       : run the resume check; then WAIT for the owner's pick. Backlog order: W-075d4 (80E/80EE/80EEA/80EEB, TY 2025-26; 80EEA sanctioned to 31-3-2022) → d5 (80CCH/80GG/80G; first locate TOLA 2020, the PM CARES lead) — both sourced in sources/2026-10-07-chapter-via.md; then W-075a3 (OQ-16/17), W-076. W-073c (needs S.O. 1046(E), a local PDF). W-075c5 after W-079. Proposed, not placed: an 80D catalogue entry; a 22-claim-catalogue erratum (s.133(6) reaches (1)(b)(ii) only). Relock helper: scratchpad relock.sh (recreate per memory if a new session)
Last verify     : ./mvnw clean test -pl backend -Punit → 2477 pass, 0 fail, 0 Checkstyle violations (W-075d3, 2026-10-07)
Waiting on      : the owner — next pick. Open: OQ-1, 2, 5–8, 10–13, 15–24. D-054 startup wiring UNOWNED. Carried: TY 2025-26 goldens PROVISIONAL (its three missing Acts located — 209695, 214648, 252143 — read only for Chapter VI-A); DIS-S202-2A, SAL-HRA-006, SAL-LTA-CHILD-001 owed; D-104, D-105 proposed
Files touched   : —
Last item commit: taxplanning W-075d3: compute 80DD, 80DDB and 80U for TY 2025-26 under the 1961 Act — 80DD there is a fixed sum, so the rows now carry "the lesser of" rather than the node (D-146)
Updated         : 2026-10-07, session 12
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
