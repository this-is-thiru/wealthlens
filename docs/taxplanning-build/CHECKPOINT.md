# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-176 — DONE (D-379); next item W-163 (not started)
Stage           : DONE
Plan            : plans/W-176.md (placement: owner "1 yes, 2 yes" — before W-163; D-379 to record with the plan approval)
Approved        : "W-176 approved C1a C2a C3a C4a" (session 37, 2026-10-28)
Steps done      : 1 – 4 (runbook; 1A uses deleteMany, not drop — keeps ChargeIndexInitializer's unique indexes)
Next step       : republish WL-A-2 if not yet read back identical; then W-163 READ → EXPLAIN only on the owner's word
Last verify     : unit -Punit 3760/0 (2026-10-28, W-176); link check 0 missing
Waiting on      : the owner — next item; open: CLAUDE.md "backfill" 503 wording (no such endpoint); api-collection term requests (submodule, owner's call); per-bucket term audit record, keep-old-labels option; nil-line proposal, PaidOtherThanInCash, REG basis-set finding; OQ-32, OQ-33, OQ-34
Files touched   : (none)
Last item commit: taxplanning W-176: make one deploy runbook for the charge engine, trade ledger and tax planning — run from a fresh start: clear the engine's data (1A), seed (1B), then every step with the 2026-10-27 counts; old runbooks become pointers (D-379)
Updated         : 2026-10-28, session 37 — W-176 done and committed (not pushed)
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
