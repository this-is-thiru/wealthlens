# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-051 — CessNode
Stage           : NOT_STARTED
Plan            : — (plans/W-051.md once written)
Approved        : —
Steps done      : —
Next step       : READ the W-051 section of 03-WORK-ITEMS.md and only the sections it names (rulebook RATE-CESS-001 + its W-045n erratum: FA 2026 s.2(6) 1961 side, s.3(15)–(16) 2025 side; REBATE-ORDER-001; DR 08 T2-1); write plans/W-051.md with the D-043 standing-rules line (coverage, catalogue, both years, concept IDs); STOP for approval. Known: CessNode requires tax.afterrebate and tax.surcharge, but no surcharge node exists until W-056; a new rule means lock → law.9
Last verify     : ./mvnw clean test -pl backend -Punit → 1562 pass, 0 fail, 0 Checkstyle violations (W-050)
Waiting on      : nothing for W-051; D-054 startup wiring UNOWNED. OQ-1, OQ-2 open
Files touched   : —
Last item commit: taxplanning W-050: add the two s.516 rounding nodes and rows — total income and amount payable, one ROUNDING line each (D-076)
Updated         : 2026-09-25, session 7
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
