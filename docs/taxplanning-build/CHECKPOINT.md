# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-061 — GrossSalaryNode (+ the SAL-SALARY-INCLUDES row, s.16 MODELLED)
Stage           : IMPLEMENT — plan approved (D-091); W-061a committed
Plan            : plans/W-061.md
Approved        : "approved — proceed as recommended" — 2026-09-26 (D-091)
Steps done      : 1, 2 (W-061a)
Next step       : step 3 — source 1961 s.17(1) for TY 2025-26 (read it, or author UNVERIFIED with whatToCheck); write law/rules/SAL-SALARY-INCLUDES.json (HeadTotal SALARIES; 2026-27 on S2 s.16); locks + RuleIds + inventory; coverage 2025 s.16 → MODELLED; catalogue entry. NB: I3 — the row needs its node in the same commit (W-061 step 4)
Last verify     : ./mvnw clean test -pl backend -Punit → 1870 pass, 0 fail, 0 Checkstyle violations (W-061a)
Waiting on      : nothing; W-052d due before Phase 9; D-054 startup wiring UNOWNED. OQ-1, OQ-2 open
Files touched   : plans/W-061.md, 03-WORK-ITEMS.md (W-061's; the W-061a row there lands with W-061's commit)
Last item commit: taxplanning W-061a: let a rule row carry a definition — what an amount is, first a head's total (D-091)
Updated         : 2026-09-26, session 8
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
