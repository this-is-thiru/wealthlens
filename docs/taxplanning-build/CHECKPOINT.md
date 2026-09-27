# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-068 — ExemptionNode: LTA (W-068a done)
Stage           : IN_PROGRESS
Plan            : plans/W-068.md
Approved        : "W-068 approved — proceed as recommended" (D-100; covers W-068a)
Steps done      : 1, 2 (W-068a committed)
Next step       : plan step 3 — the r.2B 1998 amendment (compendium: IT (First Amdt.) Rules 1998, w.r.e.f. 1-10-1997, as amended by SO 201(E) 12-3-1998; cite UNVERIFIED); rows; locks, ids; coverage, catalogue
Last verify     : ./mvnw clean test -pl backend -Punit → 2004 pass, 0 fail, 0 Checkstyle violations (W-068a)
Waiting on      : nothing for W-068; W-052d due before Phase 9; D-054 startup wiring UNOWNED. OQ-1, OQ-2, OQ-5, OQ-6 open. Carried: TY 2025-26 goldens PROVISIONAL (W-062); DIS-S202-2A, SAL-HRA-006 owed; W-066a before W-070; W-064a after W-070
Files touched   : —
Last item commit: taxplanning W-068a: add LTA's inputs — journeys, and the ledger by calendar block, since a tax year spans two (D-100)
Updated         : 2026-09-27, session 9
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
