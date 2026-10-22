# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-153b — the deductions' reach within the head by rule data (D-318; K2, K3)
Stage           : READY — not started
Plan            : written when taken (plans/W-153b.md)
Approved        : —
Steps done      : —
Next step       : on "start W-153b": read its WI row, plans/W-153.md, plans/W-153a.md (isAboutItem, OtherSourcesNode.KIND/factsOf), OtherSourcesNode:169, :192, :210, DividendInterestAllowance:74, the OS-BANKER-COMMISSION / OS-DIVIDEND-INTEREST / OS-OTHER-EXPENDITURE rows and their readers (DepositInterestDeduction.RULES, SurchargeNode); write plans/W-153b.md, STOP
Last verify     : targeted 388/0; unit -Punit 3262/0; integration 281/0 (2026-10-22, W-153a)
Waiting on      : the owner — "start W-153b"; OQ-32, OQ-33 open
Files touched   : (none)
Last item commit: taxplanning W-153a: state which interest the s.153 rows reach as rule data — othersources.kind conditions on DED-SAVINGS-INTEREST and DED-DEPOSIT-INTEREST-SENIOR, person and item conditions tested apart, so DepositInterestDeduction holds no kind test (W-153 split D-318; D-319)
Updated         : 2026-10-22, session 29 — W-153a committed
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
