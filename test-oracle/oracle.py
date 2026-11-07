"""A deliberately naive, separate reckoner of income-tax payable on a total income (DR 19 E4.2; W-141).

It shares nothing with the engine: no Java, no rule pack, no file of the backend's except the hand-worked T1/T2 golden
cases it must reproduce before it writes anything. Every figure is hard-coded here, each with the Gazette passage it was
read from, as recorded in docs/taxplanning-build/sources/2026-11-07-boundary-figures.md ("row n" below). The law is the
same in TY 2025-26 and TY 2026-27 for everything here, so one set of figures serves both years.

Run from the repository root:  python3 test-oracle/oracle.py
It checks itself against the hand cases, then writes test-oracle/expected.csv from test-oracle/cases.csv.
"""
import csv
import glob
import hashlib
import json
import os
import sys
from fractions import Fraction

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
GOLDEN = os.path.join(ROOT, "backend", "src", "test", "resources", "taxengine", "golden")

NEW = "NEW_TAX_REGIME"
OLD = "FIRST_SCHEDULE_RATES"
YEARS = ("2025-26", "2026-27")

# Row 1 — new regime: FA 2025 s.25 (1961 s.115BAC(1A)(iii)) Table, p.25; Income-tax Act, 2025 s.202(1) Table, p.227.
NEW_SLABS = [(400000, 0), (800000, 5), (1200000, 10), (1600000, 15), (2000000, 20), (2400000, 25), (None, 30)]

# Rows 2, 3 — old regime: FA 2026 First Schedule Part I-A / I-B, Paragraph A (I), (II), (III), pp.80, 84-85.
OLD_SLABS = {
    "under 60": [(250000, 0), (500000, 5), (1000000, 20), (None, 30)],
    "60 to 79": [(300000, 0), (500000, 5), (1000000, 20), (None, 30)],
    "80 and over": [(500000, 0), (1000000, 20), (None, 30)],
}

# Row 4 — new-regime surcharge, FA 2026 s.2(4)(b) / s.3(4)(b) Table Sl. 10 (no dividends or special-rate gains here).
NEW_SURCHARGE = [(5000000, 10), (10000000, 15), (20000000, 25)]
# Row 6 — old-regime surcharge, First Schedule Part I-A / I-B Paragraph F Table 1 Sl. 1.
OLD_SURCHARGE = [(5000000, 10), (10000000, 15), (20000000, 25), (50000000, 37)]
# Rows 5, 7 — marginal relief: s.2(5) / s.3(5) Table Sl. 6; Paragraph F Table 2 Sl. 1.
NEW_RELIEF_AT = [5000000, 10000000, 20000000]
OLD_RELIEF_AT = [5000000, 10000000, 20000000, 50000000]

# Row 8 — Health and Education Cess, FA 2026 s.2(6), s.3(15): 4 per cent of tax and surcharge.
CESS = Fraction(4, 100)

# Row 9 — rebate. Old regime: resident individual, total income not over 5,00,000: the lesser of the tax and 12,500
# (1961 s.87A; 2025 Act s.156(1), p.186). New regime: 2025 Act s.156(2), p.186 — not over 12,00,000: the lesser of the
# tax and 60,000; above it, the tax in excess of the income over 12,00,000; never more than the slab tax (s.156(3)).
OLD_REBATE_CEILING, OLD_REBATE_MAXIMUM = 500000, 12500
NEW_REBATE_CEILING, NEW_REBATE_MAXIMUM = 1200000, 60000


def age_class(age):
    if age is None or age < 60:
        return "under 60"
    return "60 to 79" if age < 80 else "80 and over"


def slab_tax(slabs, income):
    tax, lower = Fraction(0), 0
    for upper, rate in slabs:
        top = income if upper is None else min(income, upper)
        if top > lower:
            tax += Fraction(rate, 100) * (top - lower)
        if upper is None or income <= upper:
            break
        lower = upper
    return tax


def after_rebate(regime, income, tax):
    if regime == OLD:
        return tax - min(tax, OLD_REBATE_MAXIMUM) if income <= OLD_REBATE_CEILING else tax
    if income <= NEW_REBATE_CEILING:
        return tax - min(tax, NEW_REBATE_MAXIMUM)
    excess = income - NEW_REBATE_CEILING
    return min(tax, excess)  # the rebate takes away whatever tax exceeds the income above 12,00,000


def tax_and_surcharge(regime, age, income):
    slabs = NEW_SLABS if regime == NEW else OLD_SLABS[age_class(age)]
    tax = after_rebate(regime, income, slab_tax(slabs, income))
    ladder = NEW_SURCHARGE if regime == NEW else OLD_SURCHARGE
    rate = max([r for above, r in ladder if income > above] or [0])
    return tax * (1 + Fraction(rate, 100))


def payable(regime, age, income):
    total = tax_and_surcharge(regime, age, income)
    relief_at = [c for c in (NEW_RELIEF_AT if regime == NEW else OLD_RELIEF_AT) if income > c]
    if relief_at:
        c = max(relief_at)
        total = min(total, tax_and_surcharge(regime, age, c) + (income - c))
    return rounded(total * (1 + CESS))


def rounded(amount):
    """Row 10 — 1961 s.288B / 2025 Act s.516: paise ignored, then to the nearest ten, five or more upward."""
    whole = int(amount)
    return whole - whole % 10 + (10 if whole % 10 >= 5 else 0)


def check_hand_cases():
    misses = []
    files = sorted(glob.glob(os.path.join(GOLDEN, "T1", "*.json")) + glob.glob(os.path.join(GOLDEN, "T2", "*.json")))
    for path in files:
        case = json.load(open(path, encoding="utf-8"))
        got = payable(case["regime"], case.get("ageAtYearEnd"), int(case["totalIncome"]))
        if got != int(case["expected"]["payable"]):
            misses.append(f'{case["id"]}: oracle {got}, hand {case["expected"]["payable"]}')
    if misses or not files:
        sys.exit("The oracle disagrees with the hand-worked cases, so it writes nothing:\n  " + "\n  ".join(misses or ["no cases found"]))
    return len(files)


def sha256(path):
    return hashlib.sha256(open(path, "rb").read()).hexdigest()


def main():
    checked = check_hand_cases()
    cases_path = os.path.join(HERE, "cases.csv")
    rows = list(csv.DictReader(open(cases_path, encoding="utf-8")))
    with open(os.path.join(HERE, "expected.csv"), "w", encoding="utf-8", newline="\n") as out:
        out.write(f"# oracle.py sha256 {sha256(os.path.abspath(__file__))} cases.csv sha256 {sha256(cases_path)}\n")
        out.write("regime,ageAtYearEnd,taxYear,totalIncome,payable\n")
        for row in rows:
            age = int(row["ageAtYearEnd"]) if row["ageAtYearEnd"] else None
            out.write(f'{row["regime"]},{row["ageAtYearEnd"]},{row["taxYear"]},{row["totalIncome"]},'
                      f'{payable(row["regime"], age, int(row["totalIncome"]))}\n')
    print(f"{checked} hand cases reproduced; {len(rows)} rows written to expected.csv")


if __name__ == "__main__":
    main()
