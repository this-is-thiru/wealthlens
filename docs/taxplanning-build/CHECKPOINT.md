# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-088u8c — retire the FY and temporary-transaction runners; the runbook's final rewrite (W-088u8 split, D-281)
Stage           : BLOCKED — waits on the owner's two read-only checks from both databases
Plan            : plans/W-088u8.md (split); plans/W-088u8c.md not yet written
Approved        : —
Steps done      : —
Next step       : when the owner pastes both checks (FY rows count; temporary_transactions present?) for staging and prod: record them, write plans/W-088u8c.md (delete runner(s) with nothing left; else a one-time SUPER_USER run first + WL child), STOP. Until then nothing in order can start (R4); W-088u9a/b wait on logged runs
Last verify     : unit clean -Punit 3103 run, 0 fail; integration 266/0 (2026-10-20, W-088u8b)
Waiting on      : the owner — the two read-only checks; the W-088u7 prod run (§2.1); after deploy, §2.0's dry run; OQ-32, OQ-33 open
Files touched   : (none)
Last item commit: taxplanning W-088u8b: rebuild trade_outcomes only when a SUPER_USER asks — dry run first, idempotent per user, the caller recorded; kept as a standing operation, W-088u9c closed (D-284)
Updated         : 2026-10-20, session 25 — W-088u8b done; W-088u8c blocked on the owner
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
