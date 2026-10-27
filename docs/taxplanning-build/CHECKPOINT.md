# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-176 — one deploy runbook (charge engine, trade ledger, tax planning), run from the start on staging and prod
Stage           : EXPLAINED — C1 (file location), C2 (clear: mongosh / endpoint), C3 (staging test guide), C4 (old files) asked
Plan            : plans/W-176.md (placement: owner "1 yes, 2 yes" — before W-163; D-379 to record with the plan approval)
Approved        : —
Steps done      : —
Next step       : On "W-176 approved C…": place the W-176 row before W-163 (WI), D-379; step 1 of plans/W-176.md
Last verify     : unit -Punit 3760/0; integration 309/0 (2026-10-27, W-162c)
Waiting on      : the owner — W-176 C1 – C4; open: api-collection term requests (submodule, owner's call); per-bucket term audit record, keep-old-labels option; nil-line proposal, PaidOtherThanInCash, REG basis-set finding; OQ-32, OQ-33, OQ-34
Files touched   : (none)
Last item commit: taxplanning W-162c: remove the charge catalogue's deductibility flag — field, seed gate, backfill and JSON key go, RS-CG-CHARGE is the one place the law lives; runbook §2.10 unsets the stored flag, W-175, WL-40; W-162 closed (D-378)
Updated         : 2026-10-28, session 36 — PAUSED at the owner's session-limit notice; everything committed (not pushed: W-162a – c and the pause commit); resume at W-176 EXPLAINED (ask for approval, do not build)
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
