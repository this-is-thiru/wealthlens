# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-045c1 — delete legacy dead code (FbpOptimizer, ItrFormAdvisor, HrLetterContentBuilder, ParsedCtcDto, MarginalReliefCalculator+test, empty _review dir)
Stage           : NOT_STARTED
Plan            : — (plans/W-045c1.md once written)
Approved        : —
Steps done      : —
Next step       : READ the W-045c1 row + 03-WORK-ITEMS Appendix E; write plans/W-045c1.md; STOP for approval
Last verify     : ./mvnw test -pl backend -Punit → 1414 pass, 0 fail, 0 Checkstyle violations (W-045c)
Waiting on      : nothing for W-045c1. Owner questions OQ-1..OQ-4 open (00-BRIEF.md §12); OQ-3 gates the L2 deletion; OQ-4 is needed at W-045d
Files touched   : —
Last item commit: taxplanning W-045c: audit Phases 5–6, map slices to phases, schedule legacy deletions in compiling order
Updated         : 2026-09-24, end of session 4
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
