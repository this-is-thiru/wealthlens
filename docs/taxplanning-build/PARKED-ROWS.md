# Parked rows — the switch to Phase 11 (D-435)

Phase 9 stopped converging (9 planned items, 237 rows), and no stored data or endpoint has yet reached the engine. So the
build moves to Phase 11 after four rows, and **every other open Phase 9 row is parked here, not dropped.**
`ParkedRowsTest` fails the build if an unfinished Phase 9 row, or any row the board marks `K`, is missing from this table.

**The deploy gate.** Before the one deploy (D-287), every row still parked is built or waived **by the owner, by name, with a
D-row**. Runbook §0.4 stops the deploy otherwise. No row leaves this table by being forgotten; a finished row stays, and the
board shows its status.

**"Meanwhile"** says what protects a user until the row is built, and only as far as a row or D-row states it. **"Not checked"
means nobody has established that the figure or behaviour is safe**: check it before waiving it. W-208 – W-212 (rate-card
accuracy) and W-089a (arrears relief) most need that check.

**Queue:** `W-229`, then W-089b, W-223, W-213, W-216, W-230, then Phase 11 from W-096, planned by family. A new finding gets a row only
if it gives a wrong figure with no refusal or notice; anything else is a line under "Noted" below.

| Row | State | What | Comes back | Meanwhile |
|---|---|---|---|---|
| W-229 | NOW 0 | This register, its guard and the switch | This switch | — |
| W-088g6 | PARKED | The stamp-duty value as a land sale's consideration | The deploy gate | `ACTION_REQUIRED` notice on the sale (D-432) |
| W-088g7 | PARKED | A land sale's cost of improvement | The deploy gate | Notice on the sale (D-432) |
| W-088g8 | PARKED | A land sale's 1-4-2001 fair market value | The deploy gate | Notice on the sale (D-432) |
| W-088h | PARKED | A non-resident's unlisted securities | When unlisted securities are mapped, else waived at the deploy gate | Row says unreachable |
| W-088i2 | PARKED | The house-reinvestment exemption — the deposit route | The deploy gate | Not checked |
| W-088i3 | PARKED | The house-reinvestment exemption — the charge-backs | After W-107 (needs a stored computation), then the deploy gate | Not checked |
| W-088i6 | PARKED | The exemption on the indexed measure — an amount or a proportion | Before W-088i4 | Row says unreachable until W-088i4 |
| W-088i4 | PARKED | The house-reinvestment exemption — a land or building as the original asset | The deploy gate | Under-grants, never over-grants (row) |
| W-088i7 | PARKED | The construction window's start as rule data | With W-088i4 | Not checked |
| W-089a | PARKED | When salary is taxed — due, paid in advance, or arrears — and relief for arrears | The deploy gate | Not checked |
| W-089b | NOW 1 | `CreditsNode` | This switch | Done first |
| W-089c | PARKED | Advance tax | Before W-112, then the deploy gate | Not checked |
| W-089d | PARKED | Salary TDS | The deploy gate | Not checked |
| W-089e | PARKED | The un-limited rebate with special-rate income | When a source settles it, else waived at the deploy gate | Refused (row) |
| W-078j7c | PARKED | The r.280 watch, next run | The deploy gate | Not checked |
| W-206 | PARKED | Request DTOs at the five entity-bound endpoints | The deploy gate | Not checked |
| W-207 | PARKED | A non-premium's multi-year sum and the spreading row | The deploy gate | Refused (row) |
| W-208 | PARKED | BSE rate cards | The deploy gate | Not checked |
| W-209 | PARKED | The Zerodha AMC tiers and the first-year waiver | The deploy gate | Not checked |
| W-210 | PARKED | The account's plan in the trade context | The deploy gate | Not checked |
| W-211 | PARKED | Brokerage by holder type — HUF and NRI | The deploy gate | Not checked |
| W-212 | PARKED | A buyback order's fee | The deploy gate | Not checked |
| W-213 | NOW 3 | A stock split's arithmetic on the two perform flows | This switch | Done first |
| W-214 | PARKED | The untracked TODOs | The deploy gate | Not checked |
| W-215 | PARKED | The dead members no build check covers | The deploy gate | Not checked |
| W-230 | NOW 5 | A V2 sale's entered charge lost where no card prices it | This switch | Done first |
| W-233 | PARKED | One filed draft per ledger entry (I8) | When ledgers feed from stored data, then the deploy gate | Unreachable until then: no draft consumes a ledger entry yet |
| W-232 | PARKED | Encryption at rest and third-party retention | The deploy gate; **must be built, not waived, before a real PAN is stored** | PAN and TAN plain in the database; masked in responses, messages and logs (D-440) |
| W-231 | PARKED | The trade-outcome rebuild across a stock split | Before the rebuild runs at the deploy (runbook §2) | **Silent wrong cost for a post-split sale; only reachable by running the rebuild** |
| W-216 | NOW 4 | A stock split with a malformed ratio recorded as performed | This switch | Done first |
| W-217 | PARKED | The api-collection pointer on a pushed commit | Owner, before the next push | Not checked |
| W-218 | PARKED | W-089's and W-088i's records completed | The deploy gate | Not checked |
| W-219 | PARKED | Two guards made to check what they claim | The deploy gate | Not checked |
| W-220 | PARKED | No seventh request-bound entity without the no-arg binding | The deploy gate | Not checked |
| W-221 | PARKED | A corrected rate card into a seeded database | The deploy gate | Unreachable until §1B first runs (row) |
| W-222 | PARKED | The commit protocol's post-commit commits and co-author line | The deploy gate | Not checked |
| W-223 | NOW 2 | A V2 buy's entered charges zeroed where no rate card applies | This switch | Done first |
| W-224 | PARKED | The upload tests that read an empty sheet | The deploy gate | Not checked |
| W-225 | PARKED | Correct, remove or list an entered property sale | The deploy gate | Not checked |
| W-226 | PARKED | A property acquired by gift or inheritance | The deploy gate | Not checked |
| W-227 | PARKED | Whether a property sale is a trade in the performance metrics | The deploy gate | Not checked |
| W-228 | PARKED | The trade-outcome rebuild beside property sales | The deploy gate | Not checked |

## Noted

Findings that did not earn a row (D-435).

- 2025 s.266(2)(c) – (g) (s.157 relief, foreign relief, s.206 credit, s.391(2)) are not entered, so the balance can only be too high, never too low (W-089b, D-436); W-089a owns (c).
- A sold lot no card priced refuses the summary until charges can be entered by code for such assets (W-223 C2a, D-437). A refusal, not a wrong figure, so no row; it needs a decision before the deploy gate.
- Not built from `DR 11` (D-441 C3a): snapshots every 50 revisions (replay stays short until a draft has hundreds), fork, draft-to-draft diff, quota eviction, year rollover, per-field provenance (no reader before document import).
- Where the balance is served: `TaxComputation` holds the bill only; W-104 / W-109 read `round.netpayable` and decide on `FilingPosition` (D-436).

## Also open, not Phase 9

Unchanged by the switch, and on the board: Phases 10 – 14 (62 rows, none started) and Phase 15's 10 blocked removal rows, which
wait on the deploy's logged runs (D-286). Three `TODO(W-…)` markers stay in `src/main`, each naming its row.
