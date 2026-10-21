# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-152 — the averaging period as rule data (D-313; audit P2)
Stage           : READY — not started
Plan            : written when taken (plans/W-152.md)
Approved        : —
Steps done      : —
Next step       : on "start W-152": read its WI row, the audit file § W-088l1 P2, input/Separation.java:23, node/NotifiedRetirementLimits.java:44 and their readers, the retirement rows (RS-RET-*) and their notes, and sources/ for the 10-month average (the law is not identified yet — source it or BLOCK); write plans/W-152.md, STOP
Last verify     : targeted 171/0; unit -Punit 3249/0; integration 281/0 (2026-10-21, W-151)
Waiting on      : the owner — "start W-152"; OQ-32, OQ-33 open
Files touched   : (none)
Last item commit: taxplanning W-151: state s.516 rounding as rule data — the rows give the multiple and the round-up point and the rounding step applies them, so the ₹10 pin in Java goes (D-076's refusal reversed, D-316)
Updated         : 2026-10-21, session 28 — W-151 committed
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
