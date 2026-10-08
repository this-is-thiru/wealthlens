# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : — (W-078c DONE)
Stage           : between items
Plan            : —
Approved        : —
Steps done      : —
Next step       : run the resume check; then WAIT for the owner's pick. Asked, unanswered: where to place the house-property catalogue entries (suggested W-078d; coverage S2 ss.20–25 / 1961 ss.22–27 already points there). Backlog order: W-079 → W-075c5 → W-074b … Proposed, not placed: remove LedgerSnapshot.preConstructionInstalmentsClaimed; CBDT check on s.202(2)(a)(v) vs s.22(1)(c); separate arrears input; PERQ-EMPLOYER-CONTRIBUTION whatToCheck refresh; S.O. 1046(E) PDF; 22-claim-catalogue erratum; ss.135/137/152; T5-4 no-evidence variant. Scratchpad (relock helper, PDFs) is lost on shutdown — recreate per memory rulepack-lock-recompute
Last verify     : ./mvnw clean test -pl backend -Punit → 2560 run, 0 fail, BUILD SUCCESS (W-078c, 2026-10-08); bite probes 2/2
Waiting on      : the owner — next pick and W-078d placement. Open: OQ-1, 2, 5–8, 10–13, 15, 18–24. D-054 startup wiring UNOWNED. Carried: TY 2025-26 goldens PROVISIONAL; DIS-S202-2A, SAL-HRA-006, SAL-LTA-CHILD-001 owed; D-104, D-105 proposed
Files touched   : —
Last item commit: taxplanning W-078c: leave unrealised rent out of the actual rent before the annual value — Rules 2026 r.21 and Rules 1962 r.4 read; stock-in-trade deferred (D-157)
Updated         : 2026-10-08, session 14 (W-078c committed)
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
