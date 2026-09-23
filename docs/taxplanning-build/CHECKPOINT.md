# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : — (between items) — D-046 B1: rewrite 01-PRD.md and 02-TECH-SPEC.md as current truth
Stage           : NOT_STARTED
Plan            : reviews/2026-09-23-prd-techspec-review.md — A1–A7 + B2–B6 folded into the rewrite
Approved        : D-046 — "agreeing with you"; C1–C5 done (D-047)
Steps done      : —
Next step       : rewrite 02-TECH-SPEC.md first (T1–T12 current truth; errata → Changes section), then 01-PRD.md
Last verify     : ./mvnw test -pl backend -Punit → 1414 pass, 0 fail, 0 Checkstyle violations (W-045a)
Waiting on      : nothing. O-1..O-4 open (00-BRIEF.md §12)
Files touched   : —
Last item commit: taxplanning W-045a: enforce the engineering standards in the build — Checkstyle gate, naming rules, commit hook
Updated         : 2026-09-23, session 4
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
