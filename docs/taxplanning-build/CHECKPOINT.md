# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-045d — rule-ID/regime rename (needs OQ-4 concept names from the owner)
Stage           : NOT_STARTED
Plan            : — (plans/W-045d.md once written)
Approved        : —
Steps done      : —
Next step       : READ the W-045d row in 03-WORK-ITEMS and the sections it names; check OQ-4 (00-BRIEF.md §12) — if unanswered, ask the owner for the names; write plans/W-045d.md; STOP for approval
Last verify     : ./mvnw test -pl backend -Punit → 1407 pass, 0 fail, BUILD SUCCESS (W-045c1)
Waiting on      : OQ-4 names for W-045d. OQ-1..OQ-3 also open; OQ-3 gates the L2 deletion
Files touched   : —
Last item commit: taxplanning W-045c1: delete legacy dead code — nothing imports it (Appendix E, L1)
Updated         : 2026-09-24, session 5
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
