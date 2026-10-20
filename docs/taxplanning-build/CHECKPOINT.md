# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-088j — STT evidence in three states (D-249)
Stage           : READY — not started
Plan            : plans/W-088j.md not yet written
Approved        : —
Steps done      : —
Next step       : on "start W-088j": read the WI row, D-249 F1, plans/W-088f.md F1, D-290/D-291 (stt.* facts PAID / NOT_PAID in RS-CG-PART), CapitalGainsSummaryService.boreStt, TradeOutcomeRecorder; write plans/W-088j.md (R15: "Post-deployment actions"), STOP
Last verify     : unit clean -Punit 3133 run, 0 fail; integration 269/0 (2026-10-20, W-088f2b)
Waiting on      : the owner — "start W-088j"; OQ-32, OQ-33 open
Files touched   : (none)
Last item commit: taxplanning W-088f2b: apply S.O. 5054(E)'s waiver from a rule row — acquisitions before 1-10-2004 and bonus or demerger allotments keep the listed-equity rate without STT on the buy; ItemDateBefore added (D-291)
Updated         : 2026-10-20, session 26 — W-088f2b done (D-291), W-088f2 closed; next W-088j
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
