# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-088u9a / u9b / u9d — remove the one-time operations — BLOCKED on the owner's logged runs; next unblocked: W-088v
Stage           : BLOCKED (u9a: runbook §2.1 prod; u9b: §2.0 on every database; u9d: §2.3 prod)
Plan            : — (each written when its run is logged)
Approved        : —
Steps done      : —
Next step       : the owner either logs a run (then "start W-088u9a/b/d") or says "start W-088v" (Checkstyle limits; plans/W-088v.md not yet written). Do not pick W-088v without that word (R6)
Last verify     : unit clean -Punit 3103 run, 0 fail; integration 269/0 (2026-10-20, W-088u8c)
Waiting on      : the owner — deploy prod from this build or later; runbook §2.0 dry runs (staging, prod), §2.1 and §2.3 on prod, each logged; then the matching removal; OQ-32, OQ-33 open
Files touched   : (none)
Last item commit: taxplanning W-088u8c: retire the last two startup runners — the temporary-transaction move deleted, the W-086a FY rewrite a SUPER_USER operation for prod's 163 rows; W-088u9d placed (D-285)
Updated         : 2026-10-20, session 25 — W-088u8 closed
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
