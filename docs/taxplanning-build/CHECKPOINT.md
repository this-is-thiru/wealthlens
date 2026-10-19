# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : — (between items)
Stage           : W-088n2 done (D-258); next in order W-088o (rate bases as rule data)
Plan            : —
Approved        : —
Steps done      : —
Next step       : WAIT for the owner's next instruction ("take W-088o" or another order)
Last verify     : unit -Punit (clean, Checkstyle on) → 3062 run, 0 fail; integration 252/0 (DOCKER_HOST=OrbStack socket) (2026-10-19)
Waiting on      : the owner — next item; OQ-32, OQ-33 open
Files touched   : —
Last item commit: taxplanning W-088n2: value owned accommodation by the city's population — Table I's forty- and fifteen-lakh bands as a PercentOfBaseByPopulation cap, PopulationBand removed from the inputs (D-258)
Updated         : 2026-10-19, session 23 — W-088n2 committed
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
