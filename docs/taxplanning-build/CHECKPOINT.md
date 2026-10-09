# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : — (W-078h DONE)
Stage           : between items
Plan            : —
Approved        : —
Steps done      : —
Next step       : RESUME HERE: run the resume check; then WAIT for the owner's pick. Backlog: W-078i (tax-report PDF known-errors notice) → W-078j (sourcing: 103 UNVERIFIED versions incl. SAL-HOUSE-RENT-LANDLORD-PAN TY 2025-26, + S.O. 1046(E) — split at plan) → W-079 → W-075c5 → W-074b → W-079a → W-080 → W-080a → … Relock: TaxYear2025RulePackTest/RulePackLoadTest messages; new rules need the Zz helper (memory rulepack-lock-recompute)
Last verify     : ./mvnw test -pl backend -Punit → 2590 run, 0 fail, BUILD SUCCESS, 0 Checkstyle violations (W-078h, 2026-10-09); targeted 88/88; probe 1 bit
Waiting on      : the owner — next pick. Open OQs: none (D-163). TY 2025-26 goldens PROVISIONAL until W-078j
Files touched   : —
Last item commit: taxplanning W-078h: let a node annotate its own trace lines, and ask for the landlord's particulars where rent paid exceeds the r.205(2) threshold (D-167)
Updated         : 2026-10-09, session 15
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
