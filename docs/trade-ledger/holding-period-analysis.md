# Holding-period classification — analysis

**Purpose:** everything TL-4 needs to encode, and why the rule cannot be a single number.
**Status:** analysis complete; TL-4 implemented it, and §5's mutual-fund source question is settled as D7.
**Confidence:** stated per rule below. Where it is lower, it says so and says what to check.

---

## 1. The one-line summary

`holdingPeriodDays > 365 ? LONG_TERM : SHORT_TERM` is wrong for **every asset type this application
supports except listed equity**, and it is wrong in four places.

It is not wrong by a little. For several asset classes the correct answer is *"short-term no matter
how long it was held"* — a rule a day-count comparison cannot express at all.

---

## 2. Two dates govern, and confusing them misclassifies

This is the subtlety that decides whether an implementation is right.

| Rule | Keyed on |
|---|---|
| The 12/24-month framework (Finance (No. 2) Act 2024) | **date of transfer** — the sale |
| Deemed-short-term for specified mutual funds (s.50AA) | **date of acquisition** — the buy |
| Deemed-short-term for unlisted bonds/debentures | **date of transfer** |

So a fund bought in 2022 and sold in 2025 is *not* caught by the acquisition-dated rule, while a
sale in 2025 *is* governed by the transfer-dated framework. A policy model that stores one
"effective from" date per rule and applies it to one date will get a class of trades wrong.

**Design consequence:** each policy row must declare *which date it is keyed on*, not just when it
took effect.

---

## 3. The framework, by period

### Before 23 July 2024 *(confidence: high)*

| Asset | Long-term after |
|---|---|
| Listed equity shares, equity-oriented MF units | 12 months |
| Listed bonds, debentures, zero-coupon bonds | 12 months |
| Unlisted shares | 24 months |
| Immovable property | 24 months |
| Gold, physical assets, non-equity MF units, everything else | **36 months** |

### On or after 23 July 2024 *(confidence: high)*

The 36-month category was **abolished**. Two periods remain:

| Asset | Long-term after |
|---|---|
| **Listed** securities of any kind — equity, equity-oriented MF units, listed bonds and debentures | **12 months** |
| **Everything else** — unlisted shares, non-equity MF units, gold, immovable property | **24 months** |

*Keyed on date of transfer.* A sale on 22 July 2024 uses the old table; 23 July uses the new one.

---

## 4. The deemed-short-term rules — where a day count cannot help

### 4.1 Specified mutual funds, s.50AA *(confidence: high on the rule, medium on the 2024 redefinition's start date)*

Units of a **Specified Mutual Fund acquired on or after 1 April 2023** are **always short-term**,
however long they are held. Taxed at slab rates. No indexation.

**The definition changed**, and the change matters because it moves funds *out* of the rule:

- **Original (Finance Act 2023):** a fund investing **not more than 35%** of proceeds in equity
  shares of domestic companies. This swept in gold ETFs, international funds and gold/silver
  fund-of-funds along with debt funds.
- **Amended (Finance (No. 2) Act 2024):** a fund investing **more than 65% in debt and money-market
  instruments**, or a fund-of-funds investing 65%+ in such funds.

Under the amended definition, gold ETFs and international funds leave s.50AA and become ordinary
24-month assets. **Check the exact commencement before relying on it** — the amendment is expressed
as effective from AY 2026-27, which corresponds to transfers in FY 2025-26 (from 1 April 2025), but
"1 April 2026" also appears in the Act's own wording, and the two readings differ by a full
financial year for a large set of funds.

### 4.2 Unlisted bonds and debentures *(confidence: high)*

From **23 July 2024**, gains on transfer of an unlisted bond or unlisted debenture are **always
short-term**, regardless of holding period. Keyed on date of transfer.

### 4.3 Why this shape matters to the model

Both rules return "short-term" without consulting a duration. A policy whose only output is a
number of months **cannot express them**. The policy's result type must be:

```
LONG_TERM_AFTER(months)  |  ALWAYS_SHORT_TERM  |  NOT_CAPITAL_GAINS
```

---

## 5. Mapping onto this codebase's `AssetType`

`AssetType` is `EQUITY, MUTUAL_FUND, BOND, GOLD_BOND, FD, INSURANCE`.

### EQUITY *(confidence: high)*

12 months if listed. Unlisted equity is 24 months — but this application records `exchangeName` on
every trade and is built around exchange-traded holdings, so **treat equity as listed** and revisit
only if unlisted holdings are ever supported.

**Intraday equity is not capital gains at all.** It is speculative business income, taxed at slab
rate. That is decision D1 in the checklist and is why `segment` has to reach the trade outcome.

### MUTUAL_FUND — **`AssetType` alone is not enough** *(confidence: high on the problem)*

This is the finding that shapes TL-4. One `MUTUAL_FUND` value covers three different tax treatments:

| Fund | Test | Treatment |
|---|---|---|
| Equity-oriented | >65% in domestic equity | 12 months |
| Specified (s.50AA) | see §4.1, and acquired on/after 01-Apr-2023 | **always short-term** |
| Everything else — hybrid, and specified funds acquired before 01-Apr-2023 | — | 24 months (36 before 23-Jul-2024) |

So the policy needs a **second dimension**. `ChargeInstrumentEntity` carries `equityOriented` and
`fundCategory` and looks like the source — **it is not.** Settled 2026-09-12 as decision D7, for
three reasons, the last of which is decisive:

1. **It is keyed on a code real holdings do not match.** That is ADR-29's entire premise: profiles
   key on short codes, holdings on full scheme names, so they meet only by coincidence. A lookup
   from the classifier would miss for essentially every real fund — by construction, not by luck.
2. **Absence is already spoken for.** A scheme with no exit load has no reason to carry a row, so a
   miss means "no exit load", not "category unknown". Reading classification off it would
   conflate two different absences into one silence.
3. **Even fully populated, it cannot answer the question.** `equityOriented=true` gives
   `EQUITY_ORIENTED`. But `false` must split into `SPECIFIED` and `OTHER` on the ">65% in debt and
   money-market" test, and `FundCategory` settles that only for `DEBT` and `LIQUID`. `INDEX`, `ETF`,
   `FUND_OF_FUNDS` and `OTHER` are each ambiguous — and per §4.1 those are precisely the categories
   the 2024 amendment moved *out* of s.50AA. The enum is silent exactly where the answer is
   contested, and a plausible-looking mapping would be wrong for the hardest cases while looking
   right for the easy ones.

**The source is the instrument registry** in the priced-portfolio epic, which owns instrument
identity under ADR-29 and is where a fund's category belongs — a property of the fund, not of a
rate period. Until it exists, a fund is recorded **unclassified** and surfaced, the same way
`NO_INSTRUMENT_PROFILE` surfaces a missing exit load. A visible gap beats a confident wrong answer.

Defaulting to the 24-month non-equity rule was considered and rejected: it silently converts an
unknown into a filed number, which is the one outcome this design exists to prevent.

**This makes the priced-portfolio epic a dependency of correct MF classification**, not merely of
complete charge reporting.

### BOND *(confidence: high)*

Listed: 12 months. **Unlisted: always short-term from 23 July 2024** (§4.2). `AssetType.BOND` does
not distinguish listed from unlisted — a second dimension is needed here too, or bonds must be
treated as listed with the assumption recorded.

### GOLD_BOND — the special case worth getting right *(confidence: high on exemption, medium on edge cases)*

Sovereign Gold Bonds are not ordinary gold:

- **Redemption at maturity (8 years): exempt for individuals.** Redemption by an individual is not
  treated as a transfer, so there is no capital gain at all.
- **Premature redemption through the RBI window (from year 5): also exempt** on the same basis.
- **Sale on an exchange before maturity: taxable.** Listed, so 12 months.

So a gold-bond disposal needs to know *how* it was disposed of. If the application cannot
distinguish redemption from a market sale, it will tax an exempt redemption. **This deserves its own
decision** — at minimum, record gold-bond disposals as unclassified rather than defaulting them to
taxable.

*(Note: SGB issuance was discontinued for new tranches, but existing bonds run to 2032 and beyond,
so this is live for years yet.)*

### FD *(confidence: high)*

Interest income, not capital gains. **A fixed deposit should not produce a trade outcome at all** —
classifying it as STCG puts it under the wrong head of income entirely. `NOT_CAPITAL_GAINS`.

### INSURANCE *(confidence: medium)*

ULIP proceeds are ordinarily exempt under s.10(10D); high-premium policies (aggregate annual premium
above ₹2.5 lakh) are taxed as capital gains, with equity-oriented ULIPs treated as 12-month assets.
Traditional policies follow different rules again. **Too conditional to encode from `AssetType`** —
treat as unclassified and decide separately.

---

## 6. What I would encode

```
policy: { assetType, subClass?, keyedOn: ACQUISITION|TRANSFER,
          effectiveFrom, effectiveTo,
          result: LONG_TERM_AFTER_MONTHS(n) | ALWAYS_SHORT_TERM | NOT_CAPITAL_GAINS }
```

Validity-windowed exactly like a rate card (decision D2), so a 2023 trade keeps the 2023 rule and a
future change ships as a new row rather than an edit.

**Resolution order matters:** the deemed-short-term rules must be evaluated *before* the duration
framework, because they override it. Same shape as the charges engine's rule ordering, and worth
reusing the reasoning rather than the code.

**Unknown must be representable.** Between the two seeded scheme profiles, listed-vs-unlisted bonds,
and gold-bond redemption, there are more trades this application cannot classify today than ones it
can. The single most valuable property of this design is that it says so per row instead of guessing
— which is the same argument `ChargeResolution` already makes for charges, and the reason a stored
zero is never read as "this was free".

---

## 7. Confidence, stated plainly

**High:** the 12/24-month framework and its 23-July-2024 commencement; the abolition of 36 months;
s.50AA deeming specified mutual funds short-term from 1 April 2023; unlisted bonds and debentures
deemed short-term from 23 July 2024; SGB redemption exemption; FD interest not being capital gains.

**Medium:** the exact commencement of the amended Specified Mutual Fund definition (§4.1) — the AY
2026-27 versus 1 April 2026 readings differ by a financial year, and a large set of funds changes
treatment between them. ULIP treatment (§5). Gold-bond edge cases beyond plain maturity redemption.

**One structural caveat.** The Income-tax Act, 2025 replaced the 1961 Act with effect from 1 April
2026. The *substance* of these rules was carried forward, but **section numbers changed** — so
"s.50AA" is the right rule under its former name, and any code comment or user-facing text citing a
section number should cite the current one. The rules encoded here are the rules; the citations need
refreshing.

**Where this should be checked:** the medium-confidence items above, before they drive a filed
number. Not the framework — that part is settled and unambiguous.
