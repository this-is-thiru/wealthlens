# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-186 — DONE, committed as 866f3dd2; next is W-179
Stage           : 6 of 6; closed out
Plan            : plans/W-186.md — C1a, C2a, C3a built; C4 recorded as a decision
Approved        : "W-186 approved C1a C2a C3a" (2026-10-31)
Steps done      : all three; D-416 in STATE, the Built note in the WI row, board `D`
Next step       : the owner's word on the commit of these two state lines; then start W-179 at
                  the EXPLAIN stage (the next open item on the board): the resume check,
                  plans/W-179.md, the board `·` → `E`, update this file, STOP
Last verify     : targeted 319/0, unit 3828/0, integration 319/0, doc guards 9/0 (2026-10-31);
                  failing first 4 then 4, bite probe 1 and reverted, locks byte-identical after it
Waiting on      : the owner — the commit of these two state lines; whether the resume check's grep
                  should change (a docs-only state line returns in place of the item commit);
                  OQ-32, OQ-33, OQ-34 open
Files touched   : only these two state lines — STATE.md and CHECKPOINT.md; the item itself is in
                  866f3dd2, so the tree is otherwise clean
Last item commit: taxplanning W-186: price Fyers' clearing charge on its four F&O cards and make CLEARING deductible by its side — Fyers prints and taxes a clearing charge the catalogue had no code for, so those notes understated by Rs 500 per crore of notional and Rs 9,000 per crore of premium, and a computation carrying the charge would have been refused (D-416)
Updated         : 2026-10-31, session 43 — W-186 committed as 866f3dd2; next W-179
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
