# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : — (W-075c4 DONE)
Stage           : between items
Plan            : —
Approved        : —
Steps done      : —
Next step       : run the resume check; then WAIT for the owner's pick. Backlog order: W-073c (needs S.O. 1046(E) — a local PDF from incometaxindia.gov.in), W-075c6 (80G, s.133 — its plan settles the order against s.134, STATE notes), W-075d (TY 2025-26 Chapter VIII — needs a sourcing round), W-075a3 (OQ-16/17), W-076; W-075c5 (80TTA/TTB) after W-079. Proposed, not placed: an 80D catalogue entry
Last verify     : ./mvnw clean test -pl backend -Punit → 2451 pass, 0 fail, 0 Checkstyle violations (W-075c4, 2026-10-07)
Waiting on      : the owner — next pick. OQ-22 raised (D-141). Open: OQ-1, 2, 5–8, 10–13, 15–22 (see STATE). D-054 startup wiring UNOWNED. Carried: TY 2025-26 goldens PROVISIONAL; DIS-S202-2A, SAL-HRA-006, SAL-LTA-CHILD-001 owed; D-104, D-105 proposed
Files touched   : —
Last item commit: taxplanning W-075c4: add the deduction for rent paid without HRA — the least of rent over 10% of total income, ₹5,000 a month and 25% of it, on total income before s.134, TY 2026-27 (D-141)
Updated         : 2026-10-07, session 12
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
