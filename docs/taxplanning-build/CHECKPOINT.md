# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-088v — enforce the written limits (Checkstyle MethodLength 40, ParameterNumber 5, a shrinking baseline) (D-250; review S5)
Stage           : READY — not started (next in the build order)
Plan            : plans/W-088v.md not yet written
Approved        : —
Steps done      : —
Next step       : on "start W-088v": read checkstyle.xml / checkstyle-suppressions.xml / CheckstyleBaselineTest, measure what MethodLength 40 and ParameterNumber 5 flag repo-wide (tools/method_lengths.py), write plans/W-088v.md, STOP. Phase 15 (W-147 – W-149) follows the one deploy after Phase 14
Last verify     : unit clean -Punit 3103 run, 0 fail; integration 269/0 (2026-10-20, W-088u8c)
Waiting on      : the owner — "start W-088v"; OQ-32, OQ-33 open. No deploy until Phases 0–14 are done (D-287): do not ask for runbook runs before then
Files touched   : (none)
Last item commit: taxplanning W-088u8c: retire the last two startup runners — the temporary-transaction move deleted, the W-086a FY rewrite a SUPER_USER operation for prod's 163 rows; W-088u9d placed (D-285)
Updated         : 2026-10-20, session 25 — Phase 15 created (D-286)
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
