# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-186 — clearing charges — BUILT (D-416), awaiting the owner's commit word
Stage           : 5 of 6; verified, reported, awaiting REVIEW and the commit
Plan            : plans/W-186.md — C1a, C2a, C3a built; C4 recorded as a decision
Approved        : "W-186 approved C1a C2a C3a" (2026-10-31)
Steps done      : all three; the plan ticks, D-416 in STATE, the Built note in the WI row, board `D`
Next step       : the owner's word on the commit; then W-179 (the next open item on the board)
Last verify     : targeted 319/0, unit 3828/0, integration 319/0, doc guards 9/0 (2026-10-31);
                  failing first 4 then 4, bite probe 1 and reverted, locks byte-identical after it
Waiting on      : the owner — the commit of this work; whether the resume check's grep should
                  change (a docs-only state line returns in place of the item commit);
                  OQ-32, OQ-33, OQ-34 open
Files touched   : 9 charge data files, 2 law rule files, 2 locks, 3 Java tests, 4 golden fixtures,
                  3 docs (staging runbook, deploy runbook, 03-WORK-ITEMS), STATE.md, plans/W-186.md,
                  CHECKPOINT.md — no main-source Java
Last item commit: taxplanning W-185: put the IPFT line on the thirteen 2026-03 cards at 0.0000001 % — NSE rolled the contribution back to Rs 0.01 per crore per side from 1-3-2026 as a line of its own, so seven cash cards charged a thousand times too much and six F&O cards charged nothing (D-415)
Updated         : 2026-10-31, session 43 — W-186 built; STOP for the commit
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.

*Known false mismatch:* after a docs-only post-commit state line, the grep returns that state-line commit
rather than the item commit, because the state lines carry the item prefix too. Compare the **item** commit
named above; the tree, not the grep, is what decides. (Raised for a row in `plans/W-185.md`, Risks; it fired
again this session — `5c1b9f89` returned against the item commit `93fb1569`, both with a clean tree.)
