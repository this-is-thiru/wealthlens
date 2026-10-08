# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : — (W-078b DONE)
Stage           : between items
Plan            : —
Approved        : —
Steps done      : —
Next step       : run the resume check; then WAIT for the owner's pick. Backlog order: W-078c (unrealised rent, needs r.4 / its 2026 successor) → W-079 …; W-075c5 after W-079; W-074b (T4-3a/b). Proposed, not placed: house-property catalogue entry (then MODELLED for S2 ss.20, 22–25 / 1961 ss.22, 24–27); remove LedgerSnapshot.preConstructionInstalmentsClaimed; CBDT check on s.202(2)(a)(v) vs s.22(1)(c); separate arrears input; PERQ-EMPLOYER-CONTRIBUTION whatToCheck refresh; S.O. 1046(E) PDF; 22-claim-catalogue erratum; ss.135/137/152; T5-4 no-evidence variant. Session scratchpad (relock helper, Gazette PDFs) is lost on shutdown — recreate per memory rulepack-lock-recompute
Last verify     : ./mvnw clean test -pl backend -Punit → 2555 run, 0 fail, BUILD SUCCESS (W-078b, 2026-10-08); bite probes 5/5
Waiting on      : the owner — next pick. Open: OQ-1, 2, 5–8, 10–13, 15, 18–24. D-054 startup wiring UNOWNED. Carried: TY 2025-26 goldens PROVISIONAL; DIS-S202-2A, SAL-HRA-006, SAL-LTA-CHILD-001 owed; D-104, D-105 proposed
Files touched   : —
Last item commit: taxplanning W-078b: add interest on borrowed capital under income from house property — the five instalments, the own-residence limits, the new regime and the s.130–131 bar (D-156)
Updated         : 2026-10-08, session 14 (W-078b committed)
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
