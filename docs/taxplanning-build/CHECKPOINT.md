# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-054 — surcharge ladders for all four (year × regime) (Phase 6 opens)
Stage           : NOT_STARTED
Plan            : — (plans/W-054.md once written)
Approved        : —
Steps done      : —
Next step       : READ the W-054 row (Phase 6 table in 03-WORK-ITEMS.md; ⚠ entry is ₹50,00,000; default regime has no 37% band) and DR 01 R5 (+ its W-045d2 renames RATE-SUR-FIRST-SCHEDULE / RATE-SUR-NEW-TAX-REGIME) and FA 2026 s.2(1)/s.3(1)/First Schedule surcharge text; write plans/W-054.md with the D-043 standing-rules line (both years from the start — W-052c pattern); STOP for approval. Known: W-055/W-056 consume these ladders; W-056 adds tax.surcharge to CessNode and NetPayableNode (errata D-079, D-080)
Last verify     : ./mvnw clean test -pl backend -Punit → 1704 pass, 0 fail, 0 Checkstyle violations (W-053)
Waiting on      : nothing for W-054; W-052d due before Phase 9; D-054 startup wiring UNOWNED. OQ-1, OQ-2 open
Files touched   : —
Last item commit: taxplanning W-053: add the T1 golden tests — every DR 08 T1 case end to end in both years; the First Schedule pipeline gains its rebate step (D-083)
Updated         : 2026-09-25, session 7
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
