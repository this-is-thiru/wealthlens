# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-155 — retirement paths by rule data (D-313; audit P5; N1)
Stage           : READY — not started (W-154 closed, D-335)
Plan            : written when taken (plans/W-155.md; likely a split, as W-153 / W-154)
Approved        : —
Steps done      : —
Next step       : on "start W-155": read its WI row, the audit § W-088l1 P5 and N1 (RetirementNode :153, :208, :240, :243; NotifiedRetirementLimits :75, :102; RetirementPayment :50–60), the W-154 pattern (plans/W-154*.md: path rows with a definition, nil rows, facts dot-separated), write plans/W-155.md (a split if > §7), STOP
Last verify     : unit -Punit 3619/0 (2026-10-23, W-154g); last full: targeted 301/0, integration 281/0 (W-154f)
Waiting on      : the owner — "start W-155"; OQ-32, OQ-33 open
Files touched   : (none)
Last item commit: taxplanning W-154g: record the specified-security fair market value as not a finding — dispatch by FairMarketValueBasis's sealed shape, each record carrying only its method's prices, so rows would restate it (D-335; W-154 closed)
Updated         : 2026-10-23, session 30 — W-154 closed (a – g); W-155 next
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
