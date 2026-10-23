# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-155c — the notified entries' bases, B, and each entry's facts by rule data (D-336; R4 – R6)
Stage           : NOT STARTED — plan to write
Plan            : (none yet) — W-155b's proposals to weigh: RetirementPayment:62 (R6), SEC-SALARY-RETIREMENT disqualifier enforcedBy, OtherSourcesNode.reaches onto RowReach
Approved        : W-155 split (D-336); W-155a (D-337), W-155b (D-338) done
Steps done      : —
Next step       : read W-155c's row and D-336, write plans/W-155c.md, explain it, ask approval (do NOT code). Also still open: where to place the REG basis-set finding (STATE Notes for resume, session 30)
Last verify     : targeted 454/0; unit -Punit 3621/0; integration 281/0 (2026-10-23, W-155b)
Waiting on      : the owner — placement of the REG basis-set finding; OQ-32, OQ-33 open
Files touched   : (none)
Last item commit: taxplanning W-155b: state the voluntary-retirement bars as rule data — RS-RET-BAR's ordered conditions-only rows, reached by the payment's facts and the ledger's year per entry, so RetirementNode holds no entry test for them (D-338)
Updated         : 2026-10-23, session 31 — W-155b approved and committed; next W-155c
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
