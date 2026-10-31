# Carry-forward return condition — which carried losses need a return filed in time (W-088q1, D-263)

Session 23, 2026-10-19. The question: does the rule that a loss must be determined in a return filed in time reach
the house-property carry-forward, as it reaches the capital-loss carry-forward? This sets `LossCarryForward.requiresReturnFiledInTime`
on `SETOFF-CARRIED-HOUSE-PROPERTY` and `SETOFF-CARRIED-CAPITAL-GAINS`. **READ** = I read the passage myself.

## Sources

| # | Source | Status | What it says |
|---|---|---|---|
| 1 | Income-tax Act, 1961, thc.nic.in copy (https://thc.nic.in/Central%20Governmental%20Acts/Income%20Tax%20Act%201961_.pdf, current to Act 7 of 2019), p.327. Read from the copy an earlier session fetched (955 pages; producer iLovePDF, modified 2025-12-17; sha256 `f008674f055d3002…`), the same copy the rows already cite for s.74 (p.325) | READ | s.80: *"Notwithstanding anything contained in this Chapter, no loss which has not been determined in pursuance of a return filed in accordance with the provisions of sub-section (3) of section 139, shall be carried forward and set off under sub-section (1) of section 72 or sub-section (2) of section 73 or sub-section (2) of section 73A or sub-section (1) or sub-section (3) of section 74 or sub-section (3) of section 74A."* **s.71B is not named.** |
| 2 | Income-tax Act, 2025, Gazette CG-DL-E-22082025-265620, s.121, p.158 | READ (W-081b, W-087b; quoted in the rows' `_note`s) | Not carried "which has not been determined in pursuance of a return filed under section 263(1)"; s.121 does not name s.110 (`SETOFF-CARRIED-HOUSE-PROPERTY` `_note`); it reaches s.111 (`SETOFF-CARRIED-CAPITAL-GAINS` `_note`). |

## Result

| Row | TY 2025-26 (1961) | From TY 2026-27 (2025) | `requiresReturnFiledInTime` |
|---|---|---|---|
| `SETOFF-CARRIED-HOUSE-PROPERTY` | s.71B, not in s.80 | s.110, not in s.121 | `false` |
| `SETOFF-CARRIED-CAPITAL-GAINS` | s.74(1), in s.80 | s.111, in s.121 | `true` |

The copy is current to Act 7 of 2019. `SETOFF-CARRIED-CAPITAL-GAINS`'s `_note` already records s.74 and s.80 as
"unamended 2019–2026", from the search of those Acts' egazette texts (`2026-10-18-capital-gains-basis.md` row 24).
I did not search them again in this session.
