# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : — (W-078a DONE)
Stage           : between items
Plan            : —
Approved        : —
Steps done      : —
Next step       : RESUME HERE (session 14): run the resume check; then WAIT for the owner's pick. Backlog order: W-078b (house-property interest: s.22(1)(b),(c),(2)–(6), FA 2026 s.38; 1961 s.24(b) — pre-construction inside the cap both years, DR 01 R16 addendum; new-regime disallowance; ss.130–132 bar; a loss refused until W-081) → W-078c (unrealised rent, needs r.4 / its 2026 successor) → W-079 …; W-075c5 after W-079; W-074b (T4-3a/b). Proposed, not placed: house-property catalogue entry; separate arrears input; PERQ-EMPLOYER-CONTRIBUTION whatToCheck refresh (interim FA 2024 searched); S.O. 1046(E) India Code PDF (makes 9.5% READ); 22-claim-catalogue erratum; ss.135/137/152; T5-4 no-evidence variant (Form 12BB). The session-13 scratchpad (Gazette PDFs, relock.sh, append_arg.py) is gone after the shutdown — recreate per memory rulepack-lock-recompute
Last verify     : ./mvnw clean test -pl backend -Punit → 2533 pass, 0 fail, BUILD SUCCESS (W-078a, 2026-10-08)
Waiting on      : the owner — next pick. Open: OQ-1, 2, 5–8, 10–13, 15, 18–24. D-054 startup wiring UNOWNED. Carried: TY 2025-26 goldens PROVISIONAL; DIS-S202-2A, SAL-HRA-006, SAL-LTA-CHILD-001 owed; D-104, D-105 proposed
Files touched   : —
Last item commit: taxplanning W-078a: add income from house property before interest — annual value, the own-residence nil for two houses, thirty per cent and arrears, into gross total income (D-155)
Updated         : 2026-10-08, session 13 (paused at the owner's request — session limit; all work committed)
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
