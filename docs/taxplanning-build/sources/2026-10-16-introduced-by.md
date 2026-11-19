# Sourcing for W-078j5 — group D, the 14 `introducedBy` instruments (2026-10-16)

D-184 sets the bar: the instrument itself is read for its number and year, the cited section, the words and its own
date. When that happens, `gazetteRef` is filled and `notifiedOn` becomes the instrument's date. An instrument that
cannot be reached stays `UNVERIFIED`, and the places searched are recorded.

Fetches use `https://egazette.gov.in/WriteReadData/<year>/<number>.pdf` (curl, UA "Mozilla/5.0"). Numbers were
located by date bracketing and a first-pages scan (helpers in the scratchpad, not committed). Text was extracted with
pypdf. "p." is the Gazette's printed page.

## The instruments

| Id | Instrument | Where | Identity, as read | Used by |
|---|---|---|---|---|
| G1 | Finance Act, 2019 (7 of 2019) | egazette 2019/198304 — Gazette of India, Extraordinary, Part II, Section 1, No. 15, 21-2-2019 | "THE FINANCE ACT, 2019 NO. 7 OF 2019 [21st February, 2019.]"; "received the assent of the President on the 21st February, 2019" (p.1) | `HP-OWN-RESIDENCE-INTEREST-CAP` |
| G2 | Finance Act, 2016 (28 of 2016) | egazette 2016/169739 — Gazette of India, Extraordinary, Part II, Section 1, No. 32, 14-5-2016 | "THE FINANCE ACT, 2016 NO. 28 OF 2016 [14th May, 2016.]"; assent "14th May, 2016" (p.1) | `HP-ARREARS` |
| G3 | Finance Act, 2001 (14 of 2001) | Official Gazette, Government of Goa, Series I No. 8, 26-5-2005, from p.107 (`https://goaprintingpress.gov.in/downloads/0506/0506-8-SI-OG.pdf`) — the Goa reprint, as for F(No.2)A 2004 (`SAL-SALARY-FOR-EMPLOYER-PENSION`) | The notification on p.107 reads: "The Finance Act, 2001 (Central Act 14 of 2001) which has been passed by the Parliament and assented to by the President of India on 11-5-2001 and published in the Gazette of India, Extraordinary, Part II, Section 1 dated 11-5-2001 is hereby published …" (OCR of a scan; the Gazette of India copy itself not read) | `HP-ANNUAL-VALUE`, `HP-UNREALISED-RENT` (Act half), `HP-INTEREST`, `HP-PRE-CONSTRUCTION`, `HP-STANDARD-DEDUCTION` |

## What each says

- **G1 s.5, pp.8–9:** *"5. In section 24 of the Income-tax Act, with effect from the 1st day of April, 2020,— (a) in
  the first proviso, after the words "the amounts of deduction", the words "or, as the case may be, the aggregate of
  the amount of deduction" shall be inserted; (b) in the second proviso, … (c) after the Explanation to the third
  proviso, the following proviso shall be inserted, namely:— "Provided also that the aggregate of the amounts of
  deduction under the first and second provisos shall not exceed two lakh rupees."."* This agrees with the data:
  s.24(b) last proviso, ins. by Act 7 of 2019, s.5, w.e.f. 1-4-2020. The insertion is s.5(c); (a) and (b) are the
  matching "aggregate" words.
- **G2 s.11, p.11:** *"11. For sections 25A, 25AA and 25B of the Income-tax Act, the following section shall be
  substituted with effect from the 1st day of April, 2017, namely:— '25A. (1) The amount of arrears of rent received
  … (2) A sum equal to thirty per cent. of the arrears of rent or the unrealised rent referred to in sub-section (1)
  shall be allowed as deduction.'."* This agrees with the data.
- **Seen in passing, G2 s.10, p.11:** *"In section 24 … in clause (b), in the second proviso, for the words "three
  years", the words "five years" shall be substituted with effect from the 1st day of April, 2017."* This is the
  source of the five-year completion limit in `HP-OWN-RESIDENCE-INTEREST-CAP`'s `OwnResidenceInterestLimit`. That
  version's `introducedBy` names Act 7 of 2019, the last instrument to touch the proviso the cap lives in. It is
  recorded here so the version's history is traceable. No figure changes.
- **G3 s.14, p.117:** *"14. Substitution of new section for section 23.— For section 23 of the Income-tax Act,
  the following section shall be substituted with effect from the 1st day of April, 2002, namely:— "23. Annual value
  how determined.— (1) For the purposes of section 22, the annual value of any property shall be deemed to be— (a) the
  sum for which the property might reasonably be expected to let from year to year; …"* Its Explanation to (1) ("shall
  not include, subject to such rules as may be made in this behalf, the amount of rent which the owner cannot realise")
  is the Act half of `HP-UNREALISED-RENT`. Sub-sections (2)–(4) cover the own residence. This agrees with the data.
- **G3 s.15, pp.117–118:** *"15. Substitution of new section for section 24.— For section 24 of the Income-tax Act,
  the following section shall be substituted with effect from the 1st day of April, 2002, namely:— '24. Deductions from
  income from house property.— … (a) a sum equal to thirty per cent. of the annual value; (b) where the property has
  been acquired, constructed, repaired, renewed or reconstructed with borrowed capital, the amount of any interest
  payable on such capital: …'"*, with the pre-construction Explanation ("in equal instalments for the said previous
  year and for each of the four immediately succeeding previous years"). This agrees with the data.

## Searched and not found (C4a)

Places tried on 2026-10-16:
- **egazette:** text coverage starts about 2010. Pre-2012 files are `E_<no>_<token>_<part>`; probes of
  `2011/E_<n>_{2011,2012}_003.pdf` returned 404.
- **India Code:** `simple-search` and `browse` returned HTTP 504 three times.
- **legislative.gov.in:** a script-rendered page, with no links to the files.
- **Goa Official Gazette:** Series I, every issue from 1987-88 to 2011-12 (1,207 PDFs), searched for each Act's title,
  "Act 4 of 1988", "10AA", "Fourth Schedule" and "ten … twelve". It holds G3 and the 1988 and 1989 Acts that amend
  Act 4 of 1988, but **neither the Finance Act, 1997 nor the Direct Tax Laws (Amendment) Act, 1987 itself**.

| Instrument | Marker(s) |
|---|---|
| Finance Act, 1997 (26 of 1997), s.58 | `PERQ-RPF-CONTRIBUTION` |
| Direct Tax Laws (Amendment) Act, 1987 (4 of 1988), s.6: its assent date and Gazette | `SAL-SALARY-FOR-GRATUITY` |
| Income-tax Act, 1961 as enacted (Gazette of India, 1961), Sch. IV Pt A r.2(h) | `SAL-SALARY-FOR-RPF` |
| Income-tax (Eighth Amendment) Rules, 2001 (r.4) | `HP-UNREALISED-RENT` (Rules half) |
| Income-tax (First Amendment) Rules, 1998 / S.O. 201(E) (r.2B) | `SAL-LEAVE-TRAVEL-CONCESSION`, `-JOURNEYS` |
| Notification No. 10969, 25-6-1999, the Gazette copy | `RET-RETRENCHMENT` |
| S.O. 1046(E), 13-5-2011 (Notification No. 24/2011), the Gazette copy | `PERQ-RPF-INTEREST` |

**Found while searching, which bears on `SAL-SALARY-FOR-GRATUITY`:** Act 4 of 1988 was amended twice before most of it
came into force on 1-4-1989.
- Finance Act, 1988, s.88 (Goa Series I No. 10, 6-6-2002, pp.160–161) touches ss.36–38, 92, 128, 153–155, 158, 162
  and 182 of that Act. It does not touch s.6.
- The Direct Tax Laws (Amendment) Act, 1989 (3 of 1989), s.95(b) (Goa Series I No. 30, 24-10-2002, p.807 of the
  issue) reads: *"(b) in section 6, clauses (a), (k) and (l) shall be omitted"*.

Which clause of s.6 substituted the s.10(10) Explanation's words is therefore material. Only the 1987 Act's own text
settles it. The consolidated text (thc p.72, footnote 3: "Act 4 of 1988 s.6, w.e.f. 1-4-1989") shows the words in
force, so the provenance is not in doubt. What is in doubt is only which instrument's clause this `introducedBy` cites.

## The owner's ChatGPT share (`chatgpt.com/s/t_6ad1f5e24f1481918d865729bcc5c473`), with each lead checked

These are leads, not decisions (D-172). Each was fetched here on 2026-10-16.

| # | Lead | Checked here |
|---|---|---|
| 1 | FA 1997: the thc.nic.in PDF `Central Governmental Acts/Finance Act, 1997.pdf`; LegitQuest and CaseMine transcriptions of s.58 | **READ, but no help.** The thc PDF is image-only (28 pages), a commercial compilation with case notes printed pp.762–789, not a Gazette. P.762 reads "[THE] FINANCE ACT, 1997 [ACT NO. 26 OF 1997] [14th May, 1997]". P.766 reads "41 to 58. Amendment of Act 43 of 1961.— [Note :— These sections amend various sections of Income-tax Act, 1961, which amendment have been incorporated in the Act]". **The words of s.58 are not printed.** LegitQuest and CaseMine are third-party transcriptions, so they are not official copies (D-184 C1a) and were not relied on |
| 2 | DTLA 1987: archive.org `dli.bengal.10689.11855`, *Chaturvedi and Pithisaria's Income Tax Law*, vol. 7, 3rd ed., Eastern Law House, Calcutta, 1988 (metadata read); India Code `repealedfileopen?rfilename=A1989-03.pdf` | **READ (a secondary source).** Preface: "It was only on 24th January, 1988, that that Bill was given assent to by the President and it has become Act No. 4 of 1988". "By section 6 of the DTL(A) Act, 1987, section 10 has been amended, save as otherwise provided, with effect from 1-4-1989, as under: … (d) in clause (10),— … (iv) in the Explanation, for the words "In this clause", the words, brackets, figures and letters "In this clause, and in clause (10AA)" have been substituted" (djvu text, OCR). **The clause is s.6(d)(iv).** DTLA 1989 s.95(b) omitted (a), (k) and (l), so (d) stood. India Code returned 504, but DTLA 1989 s.95(b) was already read in the Goa reprint (above). The Gazette issue of Act 4 of 1988 is still not found |
| 3 | 1961 Act as enacted: India Code `a1961-43.pdf` (the consolidated Act); the Department's Fourth Schedule page | India Code returned 504; the Department returned 403. Neither is the 1961 printing anyway. **Not found** |
| 4 | Eighth Amendment Rules, 2001 = "Notification No. 198/2001, S.O. 631(E), 2-7-2001" (Department page) | Department: 403. **To be read from the owner's download** |
| 5 | **"S.O. 201(E) is a corrigendum to S.O. 34(E), 12-1-1998"**, the Income-tax (First Amendment) Rules, 1998 (Department pages) | Department: 403. **To be read from the owner's download.** If it holds, the LTC `introducedBy` cites the corrigendum rather than the notification, and its `notifiedOn` 1998-03-12 is the corrigendum's date |
| 6 | Notification 10969: Department page, "on or after the 1st January, 1997" | Department: 403. **To be read from the owner's download** |
| 7 | S.O. 1046(E): India Code `upload.indiacode.nic.in/showfile?…notification_no_24_2011…pdf`, headed "To be published in the Gazette" | upload.indiacode.nic.in: no response (curl 000) |

**Read after D-185:** the LegitQuest transcription of the Finance Act, 1997 (`https://www.legitquest.com/act/finance-act-1997/71C4`,
fetched 2026-10-16; CaseMine answered 403): *"Section 58 - Amendment Of Schedule IV. In Schedule IV to the Income-tax
Act, in Part A, in rule 6, in clause (a), for the word "ten", the word "twelve" shall be substituted with effect from the
1st day of April, 1998."* This agrees with the data. The Act's date, 14-5-1997, is from the thc.nic.in compilation, p.762.

## The second share (`chatgpt.com/s/t_6ad1f9cb43088191bebeec3cbf177912`), accepted under D-186

The Department's pages answer 403 here, so these are the share's readings, relayed and **not read by me**. The owner
accepted them as verified (D-186).
- **S.O. 631(E), 2-7-2001:** Notification No. 198/2001, F. No. 142/38/2001-TPL, s.295. *"They shall come into force on
  the 1st day of April, 2002, and shall, accordingly, apply to the assessment year 2002-2003 and subsequent years."*
  The page displays rule 4, but the share did not quote it. Rule 4's words are the provenance's (DoR compendium), and
  are unchanged.
- **S.O. 34(E), 12-1-1998:** the Income-tax (First Amendment) Rules, 1998. The text is "not reproduced here as it is
  already contained in the body of the rules itself".
- **S.O. 201(E), 12-3-1998:** Notification No. 10546, F. No. 142/85/97-TPL. A corrigendum to S.O. 34(E), "published in
  the Gazette of India, Extraordinary, Part II, Section 3, sub-section (ii), dated 12 January 1998, page 39":
  *"for "1989" read "1997"."* The share notes that the Department's text also calls S.O. 34(E) "dated 12 February
  1998", which contradicts that. The data uses 12-1-1998, the date on S.O. 34(E)'s own page and the Gazette date.
  **Result: the LTC `introducedBy` cited the corrigendum. It now cites S.O. 34(E).**
- **Notification No. 10969, 25-6-1999:** F. No. 200/21/97-ITA-I. *"on or after the 1st January, 1997"*. This agrees
  with the DoR consolidation (footnote 66), against the Department's s.10 footnote 28 ("1-1-1977", D-182).
- **S.O. 1046(E):** the India Code PDF timed out for the share as well. Instead, the Manupatra copy D-182 accepted was
  re-fetched here on 2026-10-16 (`https://www.manupatra.com/manufeed/contents/PDF/634411620773263133.pdf`, 20,799
  bytes). It reads *"… in supersession of the notification … S.O. 2091(E), dated the 26th August, 2010 … 9.5 per
  cent., as the rate referred to in the said clause. [F.NO. 142/14…"*.
