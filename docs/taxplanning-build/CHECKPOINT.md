# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-067 — ExemptionNode: HRA (W-067a done)
Stage           : IN_PROGRESS
Plan            : plans/W-067.md
Approved        : "W-067 approved — proceed as recommended" (D-099; covers W-067a)
Steps done      : 1, 2 (W-067a committed)
Next step       : plan step 3 — the row SAL-HOUSE-RENT-ALLOWANCE (both years) + locks + ids; coverage + catalogue
Last verify     : ./mvnw clean test -pl backend -Punit → 1982 pass, 0 fail, 0 Checkstyle violations (W-067a)
Waiting on      : nothing for W-067; W-052d due before Phase 9; D-054 startup wiring UNOWNED. OQ-1, OQ-2, OQ-5 open. Carried: TY 2025-26 goldens PROVISIONAL (W-062); DIS-S202-2A owed; W-066a before W-070; W-064a after W-070
Files touched   : —
Last item commit: taxplanning W-067a: add HRA's inputs and shapes — tenancies, the amount in excess, and a percentage by location (D-099)
Updated         : 2026-09-27, session 9
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
