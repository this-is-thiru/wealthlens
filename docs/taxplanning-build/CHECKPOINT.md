# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-155b — the voluntary-retirement bars by rule data (D-336; R3)
Stage           : READ — W-155a committed; plan for W-155b being written
Plan            : plans/W-155b.md (to write)
Approved        : —
Steps done      : —
Next step       : check whether InputsFingerprint is stored anywhere (receipts, taxplanning); read RetirementNode:239–250, LedgerSnapshot, RET-VOLUNTARY-RETIREMENT; write plans/W-155b.md; STOP
Last verify     : targeted 208/0; unit -Punit 3619/0; integration 281/0 (2026-10-23, W-155a)
Waiting on      : — (writing W-155b plan); the owner — where to place the unused REG basis sets finding (RS-REG-NEW-TAX-REGIME-NO-EXEMPTION / -NO-SETOFF, raised session 30); OQ-32, OQ-33 open
Files touched   : (none)
Last item commit: taxplanning W-155a: choose the commutation and voluntary-retirement computations by the rows' shape — the entry row's CommutedPensionShare, and RS-RET-YEAR-LIMIT's conditions-only reach row, so RetirementNode holds no entry test for them (W-155 split D-336; D-337)
Updated         : 2026-10-23, session 30 — W-155a done and committed
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
