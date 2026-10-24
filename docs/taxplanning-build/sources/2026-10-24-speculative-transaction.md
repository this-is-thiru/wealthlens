# Sourcing for W-160a — speculative transactions and derivatives, outside the capital-gains head (2026-10-24)

**READ** means read in the primary document by this session. Page numbers are the PDF's own. Text was extracted with
pypdf from:

- the Income-tax Act, 2025, `https://egazette.gov.in/WriteReadData/2025/265620.pdf` (sha256 beginning
  `7acfb16fd7f3db8b`);
- the thc.nic.in consolidation of the Income-tax Act, 1961 (8,812,268 bytes, sha256 beginning `f008674f055d3002`,
  current to Act 7 of 2019 — the copy of `2026-10-24-dependant.md`).

## The texts

| # | Provision | Text | Level | Page |
|---|---|---|---|---|
| S1 | 2025 s.66(31) | *"66. For the purposes of Part D of this Chapter,— … (31) "speculative transaction" means a transaction in which a contract for the purchase or sale of any commodity, including stocks and shares, is periodically or ultimately settled otherwise than by the actual delivery or transfer of the commodity or scrips, other than the following transactions:— (a) a specified derivative transaction as defined in clause (33); (b) [hedging of raw materials or merchandise] …; (c) a contract in respect of stocks and shares entered into by a dealer or investor therein to guard against loss in his holdings …; (d) [jobbing or arbitrage by a member]"* | **READ** | 102–103 |
| S2 | 2025 s.66(33) | *""specified derivative transaction" means any transaction in respect of trading in derivatives referred to in section 2 (ac) of the Securities Contracts (Regulation) Act, 1956; or in respect of trading in commodity derivatives … if such transactions are— (a) is carried out— (i) through a stock broker or such other intermediary registered under section 12 of the Securities and Exchange Board of India Act, 1992 … or (ii) by banks or mutual funds, electronically on screen-based systems of a recognised stock exchange; and (b) supported by a time stamped contract note …"* | **READ** | 103 |
| S3 | 1961 s.43(5) | *""speculative transaction" means a transaction in which a contract for the purchase or sale of any commodity, including stocks and shares, is periodically or ultimately settled otherwise than by the actual delivery or transfer of the commodity or scrips: Provided that for the purposes of this clause— (a) … (b) … (c) … (d) an eligible transaction in respect of trading in derivatives referred to in clause (ac) of section 2 of the Securities Contracts (Regulation) Act, 1956 (42 of 1956) carried out in a recognised stock exchange; or (e) an eligible transaction in respect of trading in commodity derivatives carried out in a recognised association … shall not be deemed to be a speculative transaction."* Explanation 1 defines "eligible transaction" (screen-based, through a registered intermediary, with a time-stamped contract note) and "recognised stock exchange" | **READ** | 236–237 |

## Amendments to 1961 s.43(5) after the consolidation

Every amending Act of the chain in `2026-10-10-act-chain-2019-2026.md` (A1 – A12) was fetched from egazette on
2026-10-24. Each was searched by the method of that file: operative words ("In section 43 of the Income-tax Act";
for A10 its s.4 items "(N) in section 43"), and marginal notes ("Amendment of section 43."). "speculative
transaction" was also searched in every Act.

| Act | sha256 (first 16) | s.43 hits | On clause (5)? | Page |
|---|---|---|---|---|
| A1 Finance (No. 2) Act, 2019 | `90a4a84c17a74cc7` | s.12, clause (1), second proviso ("bank account …") | no | 12 |
| A2 TLA 2019 | `1305fd21041b7cfd` | none | — | — |
| A3 Finance Act, 2020 | `2608fdf5eabdb4d1` | **s.21, clause (5):** *"(a) for the words "recognised association" wherever they occur, the words "recognised stock exchange" shall be substituted; (b) in Explanation 2, for clause (iii), the following clause shall be substituted, namely:— '(iii) "recognised stock exchange" means a recognised stock exchange as referred to in clause (f) of section 2 of the Securities Contracts (Regulation) Act, 1956 and which fulfils such conditions as may be prescribed and notified by the Central Government for this purpose;'"* | **yes** — proviso (e) (commodity derivatives) and its Explanation 2 only | 23 |
| A10 TOLA 2020 | `6086b45d77ab53d8` | none | — | — |
| A4 Finance Act, 2021 | `7c3b703d86169881` | s.10, clause (6)(c)(ii) | no | 24 |
| A5 Finance Act, 2022 | `fd62df6728f1806a` | none | — | — |
| A6 Finance Act, 2023 | `47073290530bdbeb` | none | — | — |
| A7 Finance Act, 2024 | `61c6ab8909b8fffc` | none | — | — |
| A8 Finance (No. 2) Act, 2024 | `16bf85c2bd66af15` | none | — | — |
| A9 Finance Act, 2025 | `6eb45d58a1859b5a` | none | — | — |
| A11 TLA 2025 | `efaae5fa7915248a` | none | — | — |
| A12 Finance Act, 2026 | `f01136356e61b253` | none | — | — |

## What it shows

- **The definition and proviso (d) are unamended for TY 2025-26.** A3 s.21 touches proviso (e) (commodity
  derivatives, "recognised association" → "recognised stock exchange") and Explanation 2 only. Neither is a
  condition of `CG-SPECULATIVE` (equity settled otherwise than by delivery) or of `CG-DERIVATIVE` (exchange-traded
  derivatives under SCRA s.2(ac)).
- **In both years:**
  - intraday equity, settled without delivery, is a speculative transaction (S1, S3);
  - an exchange-traded derivative transaction is not speculative (S1(a), S2; S3 proviso (d)).

  Either way, both are profits and gains of business (2025 Chapter IV Part D; 1961 Chapter IV-D). Neither is a
  transfer this capital-gains head takes.
