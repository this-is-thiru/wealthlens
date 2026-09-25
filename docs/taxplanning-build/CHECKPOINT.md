# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-045d3 — the code half of the rule-ID and regime-value naming (D-069)
Stage           : NOT_STARTED
Plan            : — (plans/W-045d3.md once written)
Approved        : —
Steps done      : —
Next step       : READ the W-045d3 row and only the sections it names; write plans/W-045d3.md; STOP for approval. Known: `git grep -lE 'S[0-9]{3}_' -- backend` = 16 files; the shipped FIRST_SCHEDULE_RATES.json change needs its lock; keep RuleIdNamingTest's deliberate S202_DEFAULT; the replacement for ElectionRoute S202_4_B needs a name from s.202(4)(b)'s own words (owner chooses)
Last verify     : ./mvnw clean test -pl backend -Punit → 1488 pass, 0 fail (W-045d2); check_rule_id_mapping.py 52/52
Waiting on      : nothing for W-045d3; owner to itemise the backlog gaps (STATE); D-054 startup wiring UNOWNED. OQ-1, OQ-2 open
Files touched   : —
Last item commit: taxplanning W-045d2: map the remaining 52 rulebook IDs to concept names — each from its own provision, checked by script (D-069)
Updated         : 2026-09-25, session 6
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
