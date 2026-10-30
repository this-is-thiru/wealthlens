# Bookkeeping audit — board, work items, plans, D-rows, YouTrack (session 38 pause, 2026-10-28)

**Asked by the owner** after D-394: "find all gaps". This was a mechanical cross-check:
- every entry on STATE's board (all phases, 461 entries);
- every row and heading of `03-WORK-ITEMS.md` (360 rows plus W-001 – W-058 as headings);
- every plan file (306);
- every D-row;
- YouTrack WL-28's children.

Nothing below has been changed yet; each needs the owner's word.

## Gaps

| # | Gap | Whose | What would close it |
|---|---|---|---|
| G1 | **Order:** W-185, W-186 and W-179 are `·` on the board before W-164, W-180, W-165, W-166, W-167, which are `D`. W-164 – W-167 and W-180 were done before W-177 – W-179, against the board (R4; D-394) | session 38 | Do W-185 → W-186 → W-179 next, or the owner re-orders the board to the order of work, recorded |
| G2 | **W-187 is not on the board.** The Gazette re-sourcing of W-177 / W-178 is written but unplaced | session 38 (the owner's to place) | The owner places it; proposed before W-185 |
| G3 | **Research on non-Gazette sources:** GCA s.5 (a commercial scan), the SEBI fee (sebi.gov.in), NSE transaction charges and the IPFT fold-in (broker pages), F&O IPFT before 1-3-2026 (inferred), broker prices (broker pages) | session 38 | **Done**, W-187 (D-396) |
| G4 | **W-180's plan steps are unticked.** It was closed without a change, and the plan says the steps were not taken, but the boxes are not marked that way | session 38 | Mark the four steps "not taken (closed, D-387)" |
| G5 | **Plans of DONE items with unticked steps,** each with its item commit in git: W-075a (step 8), W-075d0 (5), W-078f (4), W-088p1 (1), W-156 (2), W-160a (2 – 6), W-160d1 (1 – 4), W-163 (3) | earlier sessions | **Done**, W-188 (D-398): all 15 steps were done in their item commits, now ticked with the hash; W-200 placed for a check |
| G6 | **W-071 (a split parent) is missing from the board.** Its children W-071a – g are on it | earlier sessions | Add `W-071 —` before W-071a |
| G7 | **Findings left unplaced** in CHECKPOINT's "open" list, against the never-unplaced rule: CLAUDE.md's "backfill" 503 wording; the catalogue's SEBI_FEE reference (it names the exchange-fee Regulations, W-163c); the api-collection term requests; a per-bucket term audit record; keeping old stored labels; the nil-line proposal; `PaidOtherThanInCash`; the REG basis-set finding | earlier sessions and session 37 | The owner places each as a row, or dismisses it |

## Second pass — every other surface (owner: "check evry aspect")

| # | Gap | Whose | State |
|---|---|---|---|
| G8 | `docs/charges-engine/README.md` point 13 said the maintenance card is Zerodha's only unscoped fallback; W-177 gave Upstox and Fyers the same | session 38 | **Fixed** at this pass |
| G9 | `docs/charges-engine/staging-runbook.md` lines 11 – 14 named "the four statutory cards" and sent the reader to `ac2-rate-verification.md` alone; W-177 / W-178 cards and the eight statutory cards had no pointer to their sources | session 38 | **Fixed** (it now points to both 2026-10-28 records and to W-187) |
| G10 | `README.md` data model still listed `LocalDate date` on `CORPORATE_ACTION`, removed by W-166 | session 38 | **Fixed** |
| G11 | `README.md` data model lacks `allotment_date` on `CORPORATE_ACTION` (added by W-088k4a, D-306) | earlier session | Open, for the owner (CLAUDE.md: the README predates the tax build; trust the code) |
| G12 | `plans/W-180.md` steps unmarked (G4 above) | session 38 | **Fixed**: steps 1 – 3 "not taken: closed, D-387", step 4 ticked |
| G13 | `ac2-rate-verification.md` has no entry for W-177 / W-178's cards; their evidence is the 2026-10-28 sources records | session 38 | **Done**, W-187 (D-396): AC-2 points to the two 2026-10-28 records |

**Also checked, no gap:**
- **Commit hygiene:** each session-38 commit contains only its item's named paths.
- **CI** on the pushed branch: `c515c558` success (run 40736555883), `23048209` success. `e53f1e38` was cancelled
  when the next push superseded it (the workflow's concurrency). `3dd61ee0` was in progress at this pass.
- **No other document** carries a stale card or golden count.
- **No document** names the Gold Bond 8-year default, `safeReadAsList` or `TJsonMapper.readValue(String)`.

## Checked and consistent (no gap)

- D-rows D-1 – D-394: none missing.
- No board entry appears twice.
- W-001 – W-058 exist as headings.
- W-045b3 records its approval ("sure").
- W-088u9, W-153, W-153c and W-154e are closed split parents, marked `—` as every split parent is.
- Phase 15's ten `B` rows (W-147 – W-150, W-168, W-170, W-171, W-173 – W-175) match YouTrack WL-28's ten open
  children (WL-29, -30, -32, -34 – -40) one for one.
- WL-A-2 matches the runbook (republished at D-394).
- `CHECKPOINT`'s last item commit equals git.
- Both repos are pushed.

## Not checked

- Whether every runbook entry since W-176 has its run-log line for every database (the owner's runs).
- Whether each D-row's quoted figures match the files they name, beyond this session's items.

## Placed (D-395)

Every open gap above is now a row: W-187 (G3, G13), W-188 (G5), W-189 (G6), W-190 (G11), W-191 – W-198 (G7's eight findings). G1 closes as W-185 → W-186 → W-179 are done in board order.
