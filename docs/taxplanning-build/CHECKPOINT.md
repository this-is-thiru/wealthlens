# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-154g — specified-security fair market value (D-325; Q11)
Stage           : READ — W-154f committed; plan for W-154g being written
Plan            : plans/W-154g.md (to write)
Approved        : —
Steps done      : —
Next step       : read W-154g WI row, plans/W-154.md C3a, SpecifiedSecurityPerquisite, FairMarketValueBasis, PERQ-SPECIFIED-SECURITY / -DATE rows and their sources (r.15(8); r.3(8)); write plans/W-154g.md; STOP
Last verify     : targeted 301/0; unit -Punit 3619/0; integration 281/0 (2026-10-23, W-154f)
Waiting on      : — (writing W-154g plan); OQ-32, OQ-33 open
Files touched   : (none)
Last item commit: taxplanning W-154f: state each transferred asset's wear-and-tear method as rule data — a WearAndTear definition (rate and method) on the RS-PERQ-ASSET-TRANSFER rows, so AssetPerquisite holds no kind test and reads no cap as a rate (D-334)
Updated         : 2026-10-23, session 30 — W-154f done and committed
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
