# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : — (W-078g DONE)
Stage           : between items
Plan            : —
Approved        : —
Steps done      : —
Next step       : RESUME HERE: run the resume check; then WAIT for the owner's pick. Backlog: W-078g2 (the other 34 rows onto DIS-NEW-TAX-REGIME-NO-EXEMPTION; nil-line wording per node — STATE notes list nine nodes) → W-078h → W-078i → W-078j (sourcing, 102 UNVERIFIED versions + S.O. 1046(E)) → W-079 → W-075c5 → W-074b → W-079a → W-080 → W-080a → … Relock: run TaxYear2025RulePackTest/RulePackLoadTest for changed checksums; new rules need the Zz helper (memory rulepack-lock-recompute)
Last verify     : ./mvnw test -pl backend -Punit → 2578 run, 0 fail, BUILD SUCCESS (W-078g, 2026-10-09); targeted 296/296; new tests 22/22; probes 1–4 bit
Waiting on      : the owner — next pick. Open OQs: none (D-163). TY 2025-26 goldens PROVISIONAL until W-078j
Files touched   : —
Last item commit: taxplanning W-078g: forbid professional tax in the new regime through its s.202(2)(a) disallowance set, and make every node ask the context whether a rule applies (D-165)
Updated         : 2026-10-09, session 15
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
