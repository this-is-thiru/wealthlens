# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : — (W-078g2 DONE)
Stage           : between items
Plan            : —
Approved        : —
Steps done      : —
Next step       : RESUME HERE: run the resume check; then WAIT for the owner's pick. Backlog: W-078h (notice channel + SAL-HRA-006) → W-078i (tax-report PDF notice) → W-078j (sourcing, 102 UNVERIFIED versions + S.O. 1046(E) — split at plan) → W-079 → W-075c5 → W-074b → W-079a → W-080 → W-080a → … Relock: run TaxYear2025RulePackTest/RulePackLoadTest for changed checksums; new rules need the Zz helper (memory rulepack-lock-recompute)
Last verify     : ./mvnw test -pl backend -Punit → 2578 run, 0 fail, BUILD SUCCESS (W-078g2, 2026-10-09); probes 1, 2 bit
Waiting on      : the owner — next pick. Open OQs: none (D-163). TY 2025-26 goldens PROVISIONAL until W-078j
Files touched   : —
Last item commit: taxplanning W-078g2: forbid the new regime's other 34 exemptions and deductions through its s.202(2)(a) set, each nil line citing the provision (D-166)
Updated         : 2026-10-09, session 15
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
