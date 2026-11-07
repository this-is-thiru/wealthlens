"""Writes test-oracle/cases.csv: the incomes the oracle and the engine are compared on (W-141, C3a).

A fixed grid, so the file can be reviewed: every 25,000 up to 60 lakh, every 5 lakh up to 6 crore, and 10 and 100
either side of each point where the law changes behaviour — the slab and rebate edges, the surcharge thresholds, and the
relief crossovers worked by hand in docs/taxplanning-build/sources/2026-11-07-boundary-figures.md and DR 08 T1-N4, T2-3.
Each income for the new regime, and for the old regime at each age class (none given, 65, 82), in both tax years.

Run from the repository root:  python3 test-oracle/cases.py
"""
import os

HERE = os.path.dirname(os.path.abspath(__file__))

EDGES = [250000, 300000, 400000, 500000, 800000, 1000000, 1200000, 1600000, 2000000, 2400000,
         5000000, 10000000, 20000000, 50000000]
CROSSOVERS = [1270580, 1270590, 5161190, 5161200, 10196940, 10196950, 20892790, 20892800,
              5195890, 5195900, 10214690, 10214700, 20929990, 20930000, 53017820, 53017830]


def incomes():
    points = set(range(25000, 6000001, 25000)) | set(range(6500000, 60000001, 500000))
    for edge in EDGES + CROSSOVERS:
        points |= {edge - 100, edge - 10, edge, edge + 10, edge + 100}
    return sorted(points)


def main():
    rows = []
    for year in ("2025-26", "2026-27"):
        for income in incomes():
            rows.append(f"NEW_TAX_REGIME,,{year},{income}")
            for age in ("", "65", "82"):
                rows.append(f"FIRST_SCHEDULE_RATES,{age},{year},{income}")
    with open(os.path.join(HERE, "cases.csv"), "w", encoding="utf-8", newline="\n") as out:
        out.write("regime,ageAtYearEnd,taxYear,totalIncome\n")
        out.write("\n".join(rows) + "\n")
    print(f"{len(rows)} rows written to cases.csv")


if __name__ == "__main__":
    main()
