# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-088j2d — the user declares STT for a trade, or in bulk (D-293)
Stage           : READY — not started
Plan            : plans/W-088j2d.md not yet written
Approved        : —
Steps done      : —
Next step       : on "start W-088j2d": read the WI row ("its plan may propose closing it if j2a – j2c leave too little"), D-293 D1, D-298, runbook §3.3/§3.4; judge what UNKNOWN remains after j2c (no venue; non-NSE/BSE strings; pre-2004 lots) and whether a declaration is worth building; write plans/W-088j2d.md (build or close with a D-row), STOP
Last verify     : unit clean -Punit 3183 run, 0 fail; integration 269/0 (2026-10-20, W-088j2c2)
Waiting on      : the owner — "start W-088j2d"; OQ-32, OQ-33 open
Files touched   : (none)
Last item commit: taxplanning W-088j2c2: decide STT chargeability in the engine for a side with no breakdown — RS-STT-CHARGEABLE rows for s.98 Sl. 1–3 on NSE and BSE, the part rows accepting CHARGEABLE per s.196, s.198 and S.O. 5054(E) (D-298)
Updated         : 2026-10-20, session 26 — PAUSED at the session limit, everything committed (resume at W-088j2d)
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
