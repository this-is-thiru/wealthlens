# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-185 — DONE, committed as 93fb1569; next is W-186 (clearing charges)
Stage           : 6 of 6; closed out
Plan            : plans/W-185.md — C1a, C2a, C3a, C4b built
Approved        : "W-185 approved C1a C2a C3a C4b" (2026-10-31)
Steps done      : both; D-415 in STATE, Built note in the WI row, board `D`
Next step       : the owner's word on the commit of these two state lines; then start W-186
                  (clearing charges — the Fyers F&O `CLEARING` code, a `RS-CG-CHARGE` row with a
                  sourced s.48 / s.72 reading, relock): the resume check, plans/W-186.md, the board
                  `·` → `E`, update this file, STOP
Last verify     : targeted 170/0, unit 3822/0, integration 319/0 (2026-10-31); failing first 8, bite
                  probe bit and was reverted; no Java, no relock, no dump
Waiting on      : the owner — the commit of these two state lines; whether the resume check's grep
                  should change (a docs-only state line returns in place of the item commit);
                  OQ-32, OQ-33, OQ-34 open
Files touched   : only these two state lines — STATE.md and CHECKPOINT.md; the item itself is in
                  93fb1569, so the tree is otherwise clean
Last item commit: taxplanning W-185: put the IPFT line on the thirteen 2026-03 cards at 0.0000001 % — NSE rolled the contribution back to Rs 0.01 per crore per side from 1-3-2026 as a line of its own, so seven cash cards charged a thousand times too much and six F&O cards charged nothing (D-415)
Updated         : 2026-10-31, session 43 — W-185 committed as 93fb1569; next W-186
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.

*Known false mismatch:* after a docs-only post-commit state line, the grep returns that state-line commit
rather than the item commit, because the state lines carry the item prefix too. Compare the **item** commit
named above; the tree, not the grep, is what decides. (Raised for a row in `plans/W-185.md`, Risks.)
