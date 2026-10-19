# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-088t — one tax-year derivation (review E7)
Stage           : READY — not started
Plan            : none yet; WI row W-088t
Approved        : —
Steps done      : —
Next step       : on "start W-088t": read, write plans/W-088t.md, STOP
Last verify     : unit clean -Punit 3078 run, 0 fail; integration 252/0 (2026-10-19, W-088s)
Waiting on      : the owner — "start W-088t"; OQ-32, OQ-33 open
Files touched   : (none)
Last item commit: taxplanning W-088s: declare the shipped tax years in a manifest beside the locks — rulepacks/shipped-years.json read by ClasspathRulePackProvider.shippedYears, TaxEngineConfig.SHIPPED_YEARS removed (D-268)
Updated         : 2026-10-19, session 24 — W-088s approved and committed
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
