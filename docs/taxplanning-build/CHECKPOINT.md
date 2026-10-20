# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-088v — enforce the written limits (Checkstyle MethodLength 40, ParameterNumber 5, a shrinking baseline) (D-250; review S5)
Stage           : READY — not started
Plan            : plans/W-088v.md not yet written
Approved        : —
Steps done      : —
Next step       : on "start W-088v": read checkstyle.xml / checkstyle-suppressions.xml / CheckstyleBaselineTest, measure what MethodLength 40 and ParameterNumber 5 would flag repo-wide (tools/method_lengths.py, corrected in W-088u4), write plans/W-088v.md, STOP
Last verify     : unit clean -Punit 3099 run, 0 fail; integration 258/0 (2026-10-20, W-088u7)
Waiting on      : the owner — "start W-088v"; the W-088u7 prod run (runbook C4); OQ-32, OQ-33 open
Files touched   : (none)
Last item commit: taxplanning W-088u7: correct the stored rebuild rows' double-counted buy charges — a SUPER_USER dry-run-first endpoint that recomputes through the rebuild and fixes only lots sold more than once (D-277)
Updated         : 2026-10-20, session 25 — W-088u7 done
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
