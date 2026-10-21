# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-088j2f — source Chapter VII's commencement; date limb on STT-PURCHASE-DELIVERY (D-300 C2)
Stage           : READY — not started
Plan            : plans/W-088j2f.md not yet written
Approved        : —
Steps done      : —
Next step       : on "start W-088j2f": read its WI row, D-300, sources/2026-10-20-securities-transaction-tax.md (s.1 of FA (No. 2) 2004 and any commencement notification for Chapter VII), STT-PURCHASE-DELIVERY.json, CapitalGainsStt (an acquired-on date fact would be new to the set); try egazette / India Code first, else a ChatGPT prompt; write plans/W-088j2f.md, STOP
Last verify     : unit clean -Punit 3191 run, 0 fail; integration 269/0 (2026-10-21, W-088j2e)
Waiting on      : the owner — "start W-088j2f"; OQ-32, OQ-33 open
Files touched   : (none)
Last item commit: taxplanning W-088j2e: derive Chapter VII s.98 Sl. 1 only for a buy by purchase — a bonus or demerger lot settles under S.O. 5054(E), a rights or unrecorded-mode buy is refused; W-088j2f placed (D-300)
Updated         : 2026-10-21, session 27 — W-088j2e done
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
