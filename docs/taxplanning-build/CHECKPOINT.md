# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-088f2 — the capital-gains rate basis as rule data (D-249)
Stage           : READY — not started
Plan            : plans/W-088f2.md not yet written
Approved        : —
Steps done      : —
Next step       : on "start W-088f2": read the WI row, D-249 / D-241 (STT mapping in Java), the adapter's refusal naming W-088f2, sources/2026-10-18-capital-gains-basis.md; write plans/W-088f2.md (R15: "Post-deployment actions"), STOP
Last verify     : unit clean -Punit 3103 run, 0 fail; integration 269/0 (2026-10-20, W-088u8c; W-088v changed no main code)
Waiting on      : the owner — "start W-088f2"; OQ-32, OQ-33 open
Files touched   : (none)
Last item commit: taxplanning W-088v: make the written method and parameter limits match the enforced ones — CLAUDE.md now says 60 lines and 7 parameters, as Checkstyle does; the baseline maximum corrected to 96 (D-288)
Updated         : 2026-10-20, session 25 — W-088v done; PAUSED at the session limit, everything committed (resume at W-088f2)
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
