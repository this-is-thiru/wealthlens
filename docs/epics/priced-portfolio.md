# Epic — A fully priced portfolio

**Status:** defined, not started. **Not part of the charges engine's delivery** (Phases A–C); it
begins after that work merges.
**Raised:** 2026-09-09, from the Phase B backfill against real data.
**Shape:** one comprehensive release, at the repository owner's direction — not a trickle of
follow-on tickets against the charges checklist.

---

## 1. What this delivers

**Every trade in a user's portfolio is identified and priced.**

The charges engine can price a trade correctly — that was established in Phase B, to the paisa,
against a real contract note. What it cannot yet do is price *a real portfolio*, for two reasons
that have nothing to do with the engine's arithmetic and everything to do with the data around it.

Measured on `it-staging`, one real user, 319 transactions over two and a half years:

| | |
|---|---|
| Trades priced against a real rate card | **92 of 319 (29%)** |
| Trades with no rate card for their date | **227 (71%)** |
| Mutual fund trades that could not resolve their scheme | **43, across 8 schemes** |

Neither number is a defect. Both are the system correctly reporting that it lacks data, and both
are recorded as gaps rather than silently zeroed — which is exactly what the design was built to do.
But a user opening a charges report today sees three trades in ten priced, and that is not a feature
anyone would ship.

**This release closes both gaps together**, because closing either alone leaves the report visibly
incomplete and a user cannot tell which of the two reasons applies to any given row.

> **Not a cutover blocker (ADR-32).** Existing transactions keep their charges and are never
> re-driven through the engine, so none of this gates Phase C. What it gates is a charges report a
> user would call complete.

## 2. Why it is one release rather than two

The two workstreams are independent in implementation and inseparable in value.

Ship the instrument master alone and mutual funds resolve their schemes — but 71% of trades still
say "no rate card". Ship the historical cards alone and equity prices back to 2023 — but funds still
gap. In both cases the user-facing answer is still "your portfolio is partly priced", and the
follow-up question is still "which part, and why".

There is also a shared piece of work neither can avoid: **mapping existing stored data onto the new
identities**, since every stored transaction predates both changes. Doing that once is materially
cheaper and safer than doing it twice.

Note what that is *not*: ADR-32 settles that transactions are never re-driven through the engine to
recompute their charges. What this release maps is **identity** — which instrument a stored row
refers to — and repricing history is a separate decision nobody has taken.

## 3. Workstream A — instrument identity

**The problem, precisely.** `ChargeInstrumentEntity` is keyed on `stockCode`. Real mutual fund
holdings carry values like

```
EDELWEISS NIFTY SMALLCAP 250 INDEX FUND - DIRECT PLAN
```

— the scheme name as the broker's statement spells it — while shipped profiles key on short codes
such as `HDFCLIQUID`. The two meet only if whoever writes a profile reproduces the stored string
exactly, punctuation and spacing included. That is a convention holding two subsystems together, and
it fails silently: the redemption is priced, the exit load is simply absent, the charge comes out
smaller, and nothing errors.

**The decision is already taken — ADR-29.** One registry of every instrument the application
recognises, equities and schemes alike. Upload validates against it and rejects a transaction naming
an instrument it does not carry. Charge profiles key off the canonical code.

**In scope**

- The registry: canonical code, display name, asset type, ISIN / scheme code, status.
- **The tax sub-class** — `EQUITY_ORIENTED` / `SPECIFIED` / `OTHER` for a scheme, `LISTED` /
  `UNLISTED` for a bond. Added by decision **D7** in the
  [trade-ledger checklist](../trade-ledger/implementation-checklist.md): holding-period
  classification needs it, `ChargeInstrumentEntity` cannot supply it, and it is instrument
  identity — a property of the fund, not of a rate period. Until this ships, every mutual fund and
  bond disposal is recorded `UNCLASSIFIED` and cannot be filed from. **This makes the epic a
  dependency of correct capital-gains classification, not only of complete charge reporting.**
  Note the vocabulary is *not* `FundCategory`: the s.50AA test is ">65% debt and money-market",
  which `INDEX`, `ETF` and `FUND_OF_FUNDS` do not answer.
- Upload validation, with the rejection surfacing usefully — which row, which instrument, what to do.
- **A route back for a rejected upload.** Admin endpoint, refreshed external catalogue, or
  self-service. Without one the validation is a wall, and this is the part most likely to be
  under-designed.
- `ChargeInstrumentEntity` keyed on the canonical code.
- `ChargeInstrumentEntity.isin` joined to the registry — stored and unused today.
- Exit-load profiles for schemes users actually hold. Two ship today and neither is one anybody
  holds, so exit load is currently unreachable in practice.

## 4. Workstream B — historical rate coverage

Every shipped card starts 2025-04-01. Real histories start years earlier.

**In scope:** 2023 and 2024 generations of each shipped card, for each broker in use.

This is rate archaeology rather than engineering — finding what each broker charged in each period
and what changed when. **ADR-26 already fixes how it ships:** a new generation with a new
`scheduleCode` and its own validity window, never an edit to a card that has already priced
anything. The mechanism exists and is tested; what is missing is the evidence.

**The honest risk:** published rate pages are not reliably archived. Some periods may only be
establishable from contract notes, and some may not be establishable at all. A card that cannot be
sourced should stay absent — a wrong rate is worse than a recorded gap, because the gap is visible
and the wrong rate is not.

## 5. Shared workstream — migrating what already exists

Every stored transaction and holding predates both changes.

- Map stored `stockCode` values onto canonical codes. **Known worklist:** the 8 mutual fund schemes
  on `it-staging` that resolved `NO_INSTRUMENT_PROFILE`.
- Decide what happens to a stored instrument that cannot be mapped. It cannot be rejected — it is
  already there — so it needs a state, and that state has to be visible.
- **Whether to reprice history at all is an open decision, not an assumption.** ADR-32 says the
  cutover does not re-drive transactions. If this release wants historical trades priced so the
  report reads completely, that is a deliberate choice to make here.
  The tool that did this was deleted with ADR-32, so pricing history means building it again —
  deliberately, as part of this release, rather than finding a dormant endpoint and running it.

## 6. Release acceptance criteria

- [ ] A real portfolio prices **substantially all** of its trades; the residue is explained per row.
- [ ] A mutual fund redemption inside its exit-load window is charged, on a scheme a user holds.
- [ ] A mutual fund and a bond disposal each classify to a real holding period rather than
      `UNCLASSIFIED`, and a specified fund acquired on or after 01-Apr-2023 comes out short-term
      however long it was held.
- [ ] An upload naming an unknown instrument is rejected with a message naming the row and the
      instrument, **and** the user has a route to get it added.
- [ ] Existing data is migrated, and anything unmappable is visible rather than silent.
- [ ] `GET /user-charges/user/{email}/gaps` is empty for a covered period, or every row in it states
      a reason a human accepts.

## 7. Explicitly out of scope

Carried forward from the charges engine's own recorded limits, none of which this release addresses:
MTF interest, aggregate caps across trades, volume-tiered pricing on cumulative turnover, and
`POST /charges/recompute` for amending a card that has already priced charges.

## 8. Open questions

1. **How does an instrument get into the registry?** Seeded from an external source, curated by an
   admin, or self-service on rejection. Shapes the whole of Workstream A's user experience.
2. **How far back does Workstream B go?** Bounded by the oldest transaction anyone holds, or by the
   oldest rate anyone can evidence.
3. **Does the registry supersede `ChargeCatalogueEntity`?** They are different registries — charge
   *codes* versus *instruments* — but both are "reference data validated against at write time", and
   somebody will ask.

## 9. Evidence

- `../charges-engine/phase-b-reconciliation-findings.md` §5 — the gaps, measured.
- `../charges-engine/decisions.md` **ADR-29** — the instrument master decision and its reasoning,
  including why a registry beats normalising the string, and why rejecting an upload is the right
  severity when ADR-24 says a missing charge profile never is.
- `../charges-engine/decisions.md` **ADR-26** — how a rate card generation ships.
