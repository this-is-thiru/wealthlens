# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-088u8b — the trade_outcomes rebuild as a SUPER_USER operation (W-088u8 split, D-281; per-user idempotent, D-282)
Stage           : READY — not started
Plan            : plans/W-088u8.md (split + Revision 1); plans/W-088u8b.md not yet written
Approved        : —
Steps done      : —
Next step       : on "start W-088u8b": read TradeOutcomeMigrationRunner, TransactionBasedTradeOutcomeMigration (catch-all per user, author stamping in TradeMatchingService.buildAuditMetadata), the W-088u7 correction (shares rebuiltRows); write plans/W-088u8b.md (per-user skip of users with any row; dry run; the caller as author; failures reported; runner deleted; runbook §2 step; R15 + idempotence test), STOP
Last verify     : unit clean -Punit 3103 run, 0 fail; integration 261/0 (2026-10-20, W-088u8a)
Waiting on      : the owner — "start W-088u8b"; the two read-only checks for W-088u8c; the W-088u7 prod run; OQ-32, OQ-33 open
Files touched   : (none)
Last item commit: taxplanning W-088u8a: seed the tax policies only when a SUPER_USER asks — dry run first, idempotent per document, the caller recorded; one-time operations made idempotent and placed for removal (D-282, D-283)
Updated         : 2026-10-20, session 25 — W-088u8a done
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
