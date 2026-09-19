# 12 — Claim integrity: how much can be claimed, and what actually draws attention

**Extends `03-advisory.md` and adds features to `04-product-spec.md`.**

## C0 — What this designs, and what it does not

The question was: *what is the minimum, maximum or average we can claim and still not get a notice —
some flexibility.*

There are two readings. One is **"what does the law allow on my facts, and can I prove it"** — that is
a real, valuable, buildable feature, and it is what this document specifies. The other is **"what can
I claim beyond what I can prove, and get away with"** — I am not designing that, and I would not be
able to design it honestly even if I wanted to, because the selection criteria are not published and
I have opened no source that states them. Any number I offered there would be invented, which the
brief's anti-hallucination rule forbids and which would be worse than useless to a user who relied
on it.

The useful news is that the first reading turns out to answer the question better than the second
would, because of what the Rules actually say about what the department already sees.

---

## C1 — The reframe: for a D3 user, there is no threshold to stay under

`FACT (S4 — Income-tax Rules 2026, rule 237(6), read this session)`: for the purposes of **pre-filling
the return**, a statement of financial transaction must be furnished covering:

| Nature of transaction | Value of transaction | Reporting person |
|---|---|---|
| **Capital gains on transfer of listed securities or units of Mutual Funds** | **All transactions** | a recognised stock exchange; a depository; a recognised clearing corporation; a registrar and share-transfer agent |
| **Dividend distributed** | **All transactions** | a company |
| **Interest paid or credited** | **All transactions** | a banking company or co-operative bank; the Post Master General; an NBFC holding a s.45-IA certificate |

**"All transactions" — there is no threshold.** `INFERENCE`: the three income items D3 adds to salary
— capital gains, dividends and interest — are reported to the department in full, by the counterparty,
for the express purpose of pre-filling the user's return. Salary is already reported in full by the
employer under `TDS-CERT-001` (Form No. 130) and the quarterly statements under s.397(3)(b).

`FACT (S2, s.133(6))`: an **80G donation** claim *"shall be allowed ... only on the basis of the
information relating to such donation furnished by such institution or fund to the prescribed
authority"* — the deduction is **only** allowable if the donee independently reported it.

So for the primary persona, the position is:

> Almost every **income** figure and at least one **deduction** figure are already known to the
> department from a third party before the return is filed. The variable is not whether a figure is
> visible. It is whether the figure the user reports **matches** the one already held.

`INFERENCE`: this makes "stay under a threshold" the wrong mental model, and it makes **mismatch
detection** the right product. A ₹40,000 claim that contradicts third-party data is more exposed than
a ₹4,00,000 claim that reconciles. That is a more useful thing to tell a user than any threshold would
be, and unlike a threshold it is derivable from rules that were actually read.

---

## C2 — The three amounts, which is where "minimum, maximum, average" becomes real

For **every** claim, the product computes three numbers and the two gaps between them.

```
  0 ─────────── D ─────────── E ─────────── S ──────────▶
                │             │             │
             declared     evidenced     statutory
              amount       maximum        maximum
                └─────────────┘             │
                   headroom             └────┘
              (claim more, safely)     EXPOSURE GAP
                                  (claimed or claimable
                                   beyond what you can prove)
```

| Symbol | Name | Definition | Source |
|---|---|---|---|
| **S** | **Statutory maximum** | the most the law permits **on this user's own declared facts** | the rule's `CapShape`, evaluated — `G2`, `M1.3` |
| **E** | **Evidenced maximum** | the most the user's **recorded evidence** supports | `ProofOfClaim` amounts (`M3.3`), parsed document fields (`M4`), third-party data where available |
| **D** | **Declared amount** | what the draft currently claims | `DraftInputs` (`11-filing-drafts.md` F4) |

Two derived quantities, and they mean opposite things:

| Quantity | Formula | Meaning | Product behaviour |
|---|---|---|---|
| **Headroom** | `min(E, S) − D` | money the user is entitled to and can prove, and is not claiming | **Surface it.** This is `A3`'s gap analysis, now evidence-aware |
| **Exposure gap** | `D − E` when positive | claimed beyond what the user can currently prove | **Surface it, with what evidence would close it** |
| **Statutory overshoot** | `D − S` when positive | claimed beyond what the law allows on these facts | **Block or hard-warn.** This is not a risk, it is an error |

### Worked example — HRA, on `T3-1`'s facts

Basic ₹12,00,000; DA ₹1,20,000; HRA received ₹6,00,000; rent claimed ₹30,000/month; rent receipts on
file for 8 of the 12 months; no landlord PAN recorded.

```
S  statutory maximum  = LeastOf( received 6,00,000 ,
                                 rent − 10% salary = 3,60,000 − 1,32,000 = 2,28,000 ,
                                 50% of salary     = 6,60,000 )              =  2,28,000
                        limitedBy = "rent less 10% of salary"      SAL-HRA-001

E  evidenced maximum  = recompute limb (b) on EVIDENCED rent only:
                        8 months × 30,000 = 2,40,000
                        2,40,000 − 1,32,000                                   =  1,08,000
                        (limb (a) and (c) unchanged; least-of still binds on (b))

D  declared           =                                                          2,28,000

headroom      = min(E,S) − D = 1,08,000 − 2,28,000                            =  −1,20,000
exposure gap  = D − E        = 2,28,000 − 1,08,000                            =   1,20,000
```

Output, in the language `A4` requires:

> Your HRA exemption of **₹2,28,000** is within the statutory limit. **₹1,20,000** of it rests on four
> months of rent for which no receipt is recorded. Adding those receipts closes the gap. Separately,
> because your annual rent of ₹3,60,000 exceeds ₹1,00,000, your landlord's **PAN is required**
> (`SAL-HRA-006`); it is not on file.

`INFERENCE`: that is the honest form of "how much can I safely claim". The answer is not a number
pulled from the air — it is **₹1,08,000 fully supported today, ₹2,28,000 supportable with four more
receipts, and ₹2,28,000 is the ceiling regardless of what the HRA component says.**

### On "average"

`RECOMMENDATION`: **do not ship a peer-comparison average** — *"people at your income claim ₹X of
80C"*. Two reasons, one practical and one principled. Practically, I have opened **no** source
containing such a distribution, so any figure would be invented (`UNKNOWN`, below). Principally, an
average is an argument for claiming what others claim rather than what one's own facts support, which
inverts the whole design of this document.

There is a defensible average, and it is the user's own: **"you claimed ₹X last year and ₹Y the year
before"**, drawn from their own `ACTED_ON` drafts (`11-filing-drafts.md` F3). A year-on-year swing in
a user's own claim is a genuinely useful prompt — both as a reminder of something forgotten and as a
check on something over-stated — and it needs no external data at all.

---

## C3 — The integrity check-set

Four families of check, run over a draft, each producing findings with a severity. They are **not** a
score out of 100; `RECOMMENDATION`: a single score invites optimisation against the score, which is
precisely the behaviour to avoid.

### C3.1 Statutory validity — is this claim even lawful on these facts?

| Check | Rule |
|---|---|
| Claim exceeds the rule's `CapShape` output | every capped rule |
| HRA claimed while the accommodation is owned by the assessee | `SAL-HRA-002` |
| 80GG claimed while HRA income exists | `DED-80GG-002` |
| Nil annual value claimed on a property let at any time in the year | `HP-SELFOCC-002(b)` |
| Nil annual value claimed on more than two houses | `HP-SELFOCC-002(a)` |
| Self-occupied interest claimed in the default regime | `HP-NEW-001` |
| Professional tax claimed in the default regime | `SAL-PTAX-002` |
| Senior-citizen medical expenditure claimed alongside premium for the same person | `DED-80D-008` |
| Same interest claimed under both s.22(1)(b) and s.130/131/132 | `DED-80EX-001` |
| Employee NPS claimed under both s.123 and s.124(3) | `DED-NPS-SELF-001` (s.124(5), s.124(10)) |
| VRS deduction claimed in a second tax year | `RET-VRS-002` |
| Standard deduction exceeding salary | `SAL-STDDED-003` |
| Aggregate Chapter VIII exceeding gross total income | `DED-GTI-CAP-001` |

`RECOMMENDATION`: these are **errors, not warnings.** The computation must refuse to produce a total
that includes a statutorily impossible claim (`D-NOSILENT` applied to inputs, not just to rules).

### C3.2 Payment-mode and documentation rules — verified, and frequently missed

> **Extended by `22-claim-catalogue.md` C5.** This table is the *requirement*; the catalogue adds the
> **procedure** — ordered steps, the counterparty to ask, the exact artefact to ask for, and whether
> the claim survives being absent from Form 16. One source: the catalogue's `evidence` list is
> generated from this table (invariant I25).

`FACT`, each read this session:

| Requirement | Threshold / condition | Rule |
|---|---|---|
| Landlord name, address and **PAN**, plus relationship if any | aggregate rent in the tax year **exceeds ₹1,00,000** | `SAL-HRA-006`; S4 r.205(2) Table Sl. 1 |
| Evidence of **expenditure** for LTC / LTA | always | `SAL-LTA-002`; S4 r.205(2) Table Sl. 2 |
| Lender name, address and **PAN** for house-property interest | always | S4 r.205(2) Table Sl. 3 |
| Evidence of investment or expenditure for Chapter VIII | always | S4 r.205(2) Table Sl. 4 |
| 80D: **cash is permitted only for preventive health check-up** | all other 80D payments must be non-cash | `DED-80D-010`; S2 s.126(9) |
| 80D preventive check-up sub-limit | **₹5,000** in aggregate | `DED-80D-005`; S2 s.126(3) |
| 80G: the donation must be **a sum of money** | not in kind | S2 **s.133(4)** |
| 80G: donation **over ₹2,000** must be non-cash | | S2 **s.133(5)** |
| 80G: the claim is allowed **only on the basis of information furnished by the institution** | | S2 **s.133(6)** |
| 80GGA-equivalent: cash contribution **exceeding ₹2,000** not allowed | | S2 s.135(2)(b) |
| Disability certificate furnished with the return; re-certified on expiry | | `DED-80DD-001`, `DED-80U-001`; S2 ss.127(6)–(7), 154(2) |
| Lender's certificate specifying interest, and interest on any refinancing loan | for the self-occupied ₹2,00,000 limb | `HP-DED-CERT-001`, `HP-CAP-SELFOCC-001(ii)`; S2 s.22(2), 22(4) |
| Part C deductions denied if the return is late | Part C of Chapter VIII only | `DED-RETURN-001`; S2 s.122(5) |

`INFERENCE`: `s.133(6)` is the sharpest of these and the least known. An 80G claim is **not** allowed
merely because the user holds a receipt — it is allowed on the basis of what the **institution
reported**. So the product's 80G check is not "do you have a receipt", it is "does this appear in your
pre-filled data, and if not, have you asked the institution why".

### C3.3 Third-party match — the check that actually matters

`INFERENCE (from C1)`: because income is reported in full, the highest-value checks compare the draft
against data the department already holds.

| Comparison | Source now | Source later |
|---|---|---|
| Salary, exemptions, deductions, TDS | **Form No. 130 / Form 16** (`G5.4`, `M4.2`) | — |
| Perquisite values | **Form No. 123** | — |
| Previous-employer salary | **Form No. 122** | — |
| Capital gains on listed securities and mutual-fund units | `portfolio` (D3) | SFT pre-fill (r.237(6)) |
| Dividends | user entry | SFT pre-fill (r.237(6)) |
| Interest paid or credited | user entry | SFT pre-fill (r.237(6)) |
| 80G donations | user entry | institution's report (s.133(6)) |

`RECOMMENDATION`: `04-product-spec.md`'s out-of-scope item 9 said an AIS / Form 26AS import is out of
scope because it needs authenticated department access, and that stands. But `M4`'s reconciliation
model is **source-agnostic**, and r.237(6) says exactly what such a feed would contain. So the
reconciliation reason-set and the `DocumentParser` SPI should be designed to accept it as another
parser, and `12`'s checks should degrade gracefully to "not yet comparable" rather than "matched"
when no third-party source exists for a line. **`CANNOT_COMPARE` is already a first-class verdict
(`M4.2`) — reuse it here rather than reporting a false clean bill.**

### C3.4 Internal consistency and self-comparison

| Check | Why |
|---|---|
| Rent claimed exceeds a plausible fraction of gross salary | not a rule, a prompt — high rent with thin evidence is the exposure gap in C2 |
| Rent paid to a spouse, parent or other relative | lawful, but `SAL-HRA-006` relationship disclosure is required, and evidence expectations are higher |
| Rent paid in cash with no receipts | closes no evidence |
| Claim swings more than a set proportion from the user's own prior `ACTED_ON` draft | the only defensible "average" (C2) |
| Employment periods overlap or leave gaps | `G5.3`; a gap may be genuine unemployment or a missing Form 16 |
| Declared but unproven claims at the employer's cut-off | `TDS-SAL-12BB-001`, `M3.3` — the March-shock alert |
| Two employers each granting a full standard deduction | `T8-1` — ₹2,14,500 on the worked figures |

---

## C4 — Thresholds that are real, and what they actually mean

`RECOMMENDATION`: surface these as **"this is visible / this triggers an obligation"**, never as
*"stay below this"*. They are all `VERIFIED` from sources read this session.

### C4.1 Thresholds that make filing a return **mandatory** regardless of income

`FACT (S4, rule 163(1), for the purposes of s.263(1)(a)(x))`:

| Trigger | Threshold |
|---|---|
| Deposits in one or more **current accounts** | exceeding **₹1 crore** |
| Expenditure on **foreign travel**, for self or any other person | exceeding **₹2,00,000** |
| Expenditure on **electricity** consumption | exceeding **₹1,00,000** |
| Business sales / turnover / gross receipts | exceeding **₹60 lakh** |
| Professional gross receipts | exceeding **₹10 lakh** |
| Aggregate **TDS + TCS** | **₹25,000 or more**; **₹50,000 or more** if resident and aged 60+ |
| Aggregate deposits in one or more **savings** bank accounts | **₹50 lakh or more** |

### C4.2 Transactions reported to the department by a third party

`FACT (S4, rule 237(2), Form No. 165)` — abridged to the rows a salaried individual actually hits:

| Transaction | Value | Reported by |
|---|---|---|
| Cash payment for bank drafts, pay orders, banker's cheques | **₹10 lakh+** (₹5 lakh if no PAN) | bank / co-op bank |
| Cash deposits **or withdrawals** in current accounts | **₹50 lakh+** | bank / co-op bank |
| Cash deposits in accounts other than current or time deposits | **₹10 lakh+** (₹5 lakh if no PAN) | bank / co-op bank / Post Master General |
| Time deposits (excluding renewals) | **₹10 lakh+** | bank / Post Office / Nidhi / deposit-taking NBFC |
| Credit-card bill payments | **₹1 lakh+ in cash**, or **₹10 lakh+** by any other mode | card issuer |
| Acquiring bonds or debentures | **₹10 lakh+** | issuing company |
| Acquiring shares, including share application money | **₹10 lakh+** | issuing company |
| Buy-back of shares other than in the open market | **₹10 lakh+** | listed company |
| Sale of foreign currency, forex cards, traveller's cheques | **₹10 lakh+** (₹5 lakh if no PAN) | authorised person under FEMA |
| Purchase, sale, gift or joint development of **immovable property** | **₹45 lakh+**, or stamp-duty value ₹45 lakh+ | registrar / sub-registrar |
| **Capital gains on listed securities and MF units; dividends; interest** | **all transactions** | exchange / depository / company / bank |

`RECOMMENDATION`: render this as a **"what the department already sees"** panel for the user's own
year, populated from the figures in their own draft. *"Your ₹12.4 lakh of interest and your ₹3.1 lakh
of dividends are reported in full by your bank and the companies — make sure your return matches."*
That is genuinely actionable and it is the opposite of a threshold-avoidance tool.

### C4.3 The obligation these thresholds create

`INFERENCE`: none of the values in C4.1 or C4.2 is a *safety* threshold. C4.1 thresholds make a return
**compulsory**; C4.2 thresholds make a transaction **visible**. Crossing one does not create risk —
**failing to account for it does**. That distinction is the single most useful thing this feature can
teach a user, and it is fully sourced.

---

## C5 — What is `UNKNOWN`, stated plainly

| Item | Status | Why |
|---|---|---|
| CBDT's **scrutiny selection criteria** — the parameters under which a return is picked for examination | **UNKNOWN** | Not published as a rule or notification, and **no source on it was opened this session**. Any figure would be invented |
| Any **statistical distribution** of claim amounts by income band | **UNKNOWN** | No such source opened. This is why C2 declines to ship a peer average |
| Whether a particular claim level "draws attention" | **UNKNOWN, and unanswerable from the statute** | The statute states entitlement and documentation; it does not state selection |
| Risk-management strategy referenced in the Act for verifying claims | **partially known** | `FACT (S2, s.135(4), s.133(6))`: claims are verified *"in accordance with the risk management strategy formulated by the Board from time to time"* — the Act **says a strategy exists and is applied**, and does not state its contents |

`RECOMMENDATION`: the product should say this in as many words when a user asks. *"We don't model
which returns are examined, because the criteria aren't published. We model what you're entitled to
and whether you can prove it."* An honest limitation stated once is worth more than a fabricated
comfort, and it is also the only position consistent with `A4`'s language rules.

---

## C6 — Feature spec

Adding to `04-product-spec.md`:

| ID | Feature | Acceptance criteria | Rule IDs | R | I | C | E | RICE | Tier |
|---|---|---|---|---|---|---|---|---|---|
| **F23** | **Claim integrity panel** — S / E / D and the two gaps, per claim | Every capped claim shows statutory maximum, evidenced maximum, declared; headroom and exposure gap computed; `limitedBy` named | C2, all capped rules | 0.95 | 3 | 0.9 | 3 | **0.86** | **MVP** — replaces part of F13 |
| **F24** | **Statutory validity checks** | Every C3.1 check implemented; a statutory overshoot **blocks** the total | C3.1 | 1.0 | 3 | 0.95 | 2 | **1.43** | **MVP** |
| **F25** | **Documentation checklist** | Every C3.2 requirement evaluated; each missing item names the evidence that closes it **and the counterparty to get it from** (`22-claim-catalogue.md` C7) | C3.2, `22` | 0.9 | 3 | 0.95 | 2 | **1.28** | **MVP** |
| **F26** | **"What the department already sees"** | C4.1 and C4.2 evaluated against the user's own figures; framed as visibility and obligation, never as avoidance | `rule 163`, `rule 237` | 0.7 | 2 | 0.9 | 2 | **0.63** | **Next** |
| **F27** | **Year-on-year self-comparison** | Swings against the user's own prior `ACTED_ON` drafts flagged both ways | C3.4, `11-filing-drafts.md` F3 | 0.6 | 2 | 0.95 | 1 | **1.14** | **Next** |

`INFERENCE`: F24 and F25 score higher than most of the original MVP set, and they should — they are
cheap, they rest entirely on rules already in the pack, and they prevent the class of error the user
cannot detect unaided.

---

## C7 — Language

Extending `A4.1`:

| Do not say | Say instead |
|---|---|
| "Safe limit: ₹X" | "Statutory maximum ₹S. Your evidence currently supports ₹E." |
| "You can claim up to ₹X without a notice" | "We don't model which returns are examined — the criteria aren't published. Here is what you're entitled to and what you can prove." |
| "Low risk" | "Fully evidenced" / "₹X not yet evidenced" |
| "Most people claim ₹X" | "You claimed ₹X last year" |
| "This is unlikely to be questioned" | nothing — delete the sentence |

`RECOMMENDATION`: the product must never present the exposure gap as an **opportunity**. The correct
framing of `D − E > 0` is *"here is what would close it"*, never *"here is what you could get away
with"*. That is a UI copy rule with a correctness purpose: the same number, framed the second way,
turns a compliance tool into an evasion tool, and the engineering is identical either way.

---

## C8 — Tests

| ID | Case | Expected |
|---|---|---|
| **T12-1** | HRA with 8 of 12 months evidenced | `S = 2,28,000`, `E = 1,08,000`, exposure gap `1,20,000`, `limitedBy` = limb (b) |
| **T12-2** | HRA declared at ₹3,00,000 against a statutory maximum of ₹2,28,000 | **blocked** as a statutory overshoot, not warned |
| **T12-3** | Annual rent ₹3,60,000, no landlord PAN | `ACTION_REQUIRED` citing `SAL-HRA-006` |
| **T12-4** | Annual rent ₹96,000, no landlord PAN | **no finding** — below the r.205(2) threshold |
| **T12-5** | 80D premium paid in cash | claim rejected, citing `DED-80D-010` / s.126(9) |
| **T12-6** | 80D preventive check-up in cash | **allowed** — the one permitted cash payment |
| **T12-7** | 80G donation of ₹5,000 in cash | rejected, citing s.133(5) |
| **T12-8** | 80G donation of ₹1,500 in cash | allowed on mode, **but** flagged against s.133(6) — allowable only on the institution's report |
| **T12-9** | Same housing interest claimed under s.22(1)(b) and s.131 | blocked, citing `DED-80EX-001` |
| **T12-10** | Nil annual value claimed on a property let for one day | blocked, citing `HP-SELFOCC-002(b)` |
| **T12-11** | TDS + TCS aggregate ₹26,000, total income below the exemption limit | return-filing obligation flagged, citing r.163(1)(f) |
| **T12-12** | No third-party source for dividend income | reconciliation reports `CANNOT_COMPARE`, **not** `MATCHED` |
| **T12-13** | 80C declared ₹1,50,000, evidenced ₹40,000, statutory maximum ₹1,50,000 | headroom 0, exposure gap ₹1,10,000, and the evidence list that closes it |
| **T12-14** | Any output containing the words "safe", "unlikely to be questioned" or a peer average | **fails a copy test** — asserted against the rendered strings |
