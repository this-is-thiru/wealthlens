"""W-146: writes the synthetic pack's timeline to backend/src/test/resources/rulepacks-synthetic (D-463, D-464, D-466).

Usage from the repo root: python3 -I docs/taxplanning-build/tools/w146_synthetic_pack.py .
then write the locks: copy zz_synthetic_spike_test.java.txt to
backend/src/test/java/com/thiru/wealthlens/taxengine/rules/timeline/ZzSyntheticSpikeTest.java, delete
backend/target/test-classes/rulepacks-synthetic, run
./mvnw test -pl backend -Dtest=ZzSyntheticSpikeTest -Dcheckstyle.skip -Dspotless.check.skip=true -Djacoco.skip=true
(it fails by design, printing each year's validation and computations, and writes the three locks), and delete the
spike test. Run once per change to this file; the output is committed.

The synthetic year's own law — three regimes, their rate tables, the 1 July standard deduction, the residents-only
eligibility — is written here. Every other rule set the shipped pipeline reads needs exactly one row (W-245, decided
(a)): those rows, the components and the disallowance sets are TY 2026-27's, copied with their regime references
renamed, each saying so in its _note.
"""
import json, os, shutil, sys

REPO = sys.argv[1]
ROOT = os.path.join(REPO, 'backend/src/test/resources/rulepacks-synthetic')
TL = os.path.join(ROOT, 'timeline')
SHIPPED = os.path.join(REPO, 'backend/src/main/resources/rulepacks/timeline')

NOTE = "Synthetic law for W-146 (DR 10 E8, DR 17 T17-11, DR 18 T18-13): not law, never shipped."
COPIED = ("Copied from the shipped TY 2026-27 timeline for W-146's synthetic pack, regime references renamed"
          " (NEW_TAX_REGIME -> SYN_DEFAULT, SYN_FLAT; FIRST_SCHEDULE_RATES -> SYN_CLASSIC); not law, never shipped.")
PROV = {"sourceUrl": "https://example.invalid/w-146-synthetic", "sourceDescription": NOTE,
        "accessedOn": "2026-11-07", "verificationLevel": "UNVERIFIED",
        "whatToCheck": "nothing: synthetic figures chosen for W-146's hand arithmetic"}
START = "2099-04-01"
RENAMED = {"NEW_TAX_REGIME": ["SYN_DEFAULT", "SYN_FLAT"], "FIRST_SCHEDULE_RATES": ["SYN_CLASSIC"]}
OWN_SETS = {"RS-RATE-SLAB", "RS-SAL-STANDARD-DEDUCTION"}


def write(rel, obj):
    p = os.path.join(TL, rel)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, 'w') as f:
        f.write(json.dumps(obj, indent=2, ensure_ascii=False) + '\n')


def provision(ref):
    return {"instrument": "Synthetic Act, 2099", "reference": ref, "presence": "PRESENT"}


ABSENT_1961 = {"instrument": "Income-tax Act, 1961", "reference": "none", "presence": "ABSENT_IN_THIS_ACT"}


def version(frm, to, regimes, cap, ref):
    validity = {"type": "Between", "from": frm}
    if to:
        validity["to"] = to
    return {"validity": validity, "regimes": regimes, "provision1961": ABSENT_1961, "provision2025": provision(ref),
            "cap": cap, "conditions": [],
            "introducedBy": {"instrumentCitation": "Synthetic Act, 2099, " + ref, "gazetteRef": "SYNTHETIC",
                             "notifiedOn": "2099-03-01", "effectiveFrom": frm, "retrospective": False,
                             "sourceUrl": PROV["sourceUrl"], "verificationLevel": "UNVERIFIED"},
            "legalStatus": "IN_FORCE", "publicationStatus": "PUBLISHED", "provenance": PROV}


def rule(rule_id, rule_set, versions, combination="LATEST_VERSION_GOVERNS", basis=None):
    r = {"ruleId": rule_id, "ruleSet": rule_set, "_note": NOTE, "axis": "TAX_YEAR",
         "midYearCombination": combination, "versions": versions, "headProvenance": PROV}
    if basis:
        r["prorationBasis"] = basis
    write("law/rules/" + rule_id + ".json", r)


def ladder(bands):
    return {"type": "SlabLadder", "base": "ROUNDED_TOTAL_INCOME",
            "bands": [{"lowerExclusive": lo, "upperInclusive": hi, "rate": rate} for lo, hi, rate in bands]}


def only(*codes):
    return {"type": "OnlyIn", "regimes": list(codes)}


def renamed(node):
    if isinstance(node, dict):
        if node.get("type") in ("OnlyIn", "ExceptIn") and "regimes" in node:
            node["regimes"] = sorted({c for r in node["regimes"] for c in RENAMED.get(r, [r])})
        for value in node.values():
            renamed(value)
    elif isinstance(node, list):
        for value in node:
            renamed(value)
    return node


def copied(path):
    node = renamed(json.load(open(path)))
    node["_note"] = COPIED + (" Original note: " + node["_note"] if "_note" in node else "")
    return node


shutil.rmtree(ROOT, ignore_errors=True)

# The synthetic year's own law: rate tables (DR 10 E8; a flat rate is a one-band ladder, D-463 C3a)
rule("SYN-RATE-DEFAULT", "RS-RATE-SLAB",
     [version(START, None, only("SYN_DEFAULT"), ladder([("0", "500000", "0"), ("500000", None, "10")]), "s.1")])
rule("SYN-RATE-CLASSIC", "RS-RATE-SLAB",
     [version(START, None, only("SYN_CLASSIC"), ladder([("0", "300000", "0"), ("300000", None, "20")]), "s.2")])
rule("SYN-RATE-FLAT", "RS-RATE-SLAB",
     [version(START, None, only("SYN_FLAT"), ladder([("0", None, "8")]), "s.3")])


# T18-13: a limit that changes on 1 July — the standard deduction, ExceptIn(SYN_FLAT); SYN_FLAT's own row is nil,
# since the set takes exactly one row per regime (W-243)
def std(amount):
    return {"type": "LeastOf", "limbs": [{"name": "statutory amount", "shape": {"type": "Fixed", "amount": amount}},
                                         {"name": "salary", "shape": {"type": "PercentOfBase", "percent": "100",
                                                                      "base": "SALARY"}}]}


EXCEPT_FLAT = {"type": "ExceptIn", "regimes": ["SYN_FLAT"]}
rule("SYN-STANDARD-DEDUCTION", "RS-SAL-STANDARD-DEDUCTION",
     [version(START, "2099-06-30", EXCEPT_FLAT, std("50000"), "s.11"),
      version("2099-07-01", None, EXCEPT_FLAT, std("80000"), "s.11 as amended")],
     combination="PRORATE_PER_SEGMENT", basis="DAYS")
rule("SYN-STANDARD-DEDUCTION-FLAT", "RS-SAL-STANDARD-DEDUCTION",
     [version(START, None, only("SYN_FLAT"), {"type": "Fixed", "amount": "0"}, "s.12")])

# SYN_FLAT's eligibility: residents only (D-466 C3a; revision R2a)
write("law/condition-sets/ELIG-RESIDENT.json", {"id": "ELIG-RESIDENT", "_note": NOTE, "versions": [
    {"validity": {"type": "Between", "from": START}, "displayName": "a resident", "conditions": [{"type": "IsResident"}],
     "basis": "SYN-RATE-FLAT", "provenance": PROV}]})

# Every other rule the shipped pipeline reads, copied (W-245 (a))
for name in sorted(os.listdir(os.path.join(SHIPPED, 'law/rules'))):
    source = json.load(open(os.path.join(SHIPPED, 'law/rules', name)))
    if source.get("ruleSet") in OWN_SETS:
        continue
    write("law/rules/" + name, copied(os.path.join(SHIPPED, 'law/rules', name)))
for name in sorted(os.listdir(os.path.join(SHIPPED, 'law/disallowances'))):
    write("law/disallowances/" + name, copied(os.path.join(SHIPPED, 'law/disallowances', name)))
for name in sorted(os.listdir(os.path.join(SHIPPED, 'exec/components'))):
    write("exec/components/" + name, copied(os.path.join(SHIPPED, 'exec/components', name)))
for name in sorted(os.listdir(os.path.join(SHIPPED, 'exec/pipelines'))):
    write("exec/pipelines/" + name, json.load(open(os.path.join(SHIPPED, 'exec/pipelines', name))))
write("law/rule-sets.json", copied(os.path.join(SHIPPED, 'law/rule-sets.json')))

# Regimes
PIPELINE = json.load(open(os.path.join(SHIPPED, 'law/regimes/NEW_TAX_REGIME.json')))['pipeline']
DISALLOWANCES = sorted(n[:-5] for n in os.listdir(os.path.join(SHIPPED, 'law/disallowances')))


def regime(code, rate, default, kind, vehicle, disallowances, eligibility, in_force_to=None, form=None):
    window = {"from": START}
    if in_force_to:
        window["to"] = in_force_to
    protocol = {"kind": kind, "vehicle": vehicle, "deadline": {"type": "NoDeadline"},
                "binding": {"bindsSubsequentYears": False, "maxWithdrawals": -1,
                            "afterWithdrawal": {"type": "MayReElect"}, "firstReturnOnly": False,
                            "bindingAppliesWhen": []},
                "provenance": PROV}
    if form:
        protocol["form"] = form
    write("law/regimes/" + code + ".json", {
        "code": code, "_note": NOTE, "displayName": code + " (synthetic)", "appliesTo": ["INDIVIDUAL"],
        "rateTable": rate, "disallowances": disallowances, "eligibility": eligibility, "isDefault": default,
        "provision1961": ABSENT_1961, "provision2025": provision("s.1"),
        "elections": [{"validity": {"type": "Between", "from": START}, "protocol": protocol}],
        "lifecycle": {"entryWindow": dict(window), "inForceWindow": dict(window),
                      "onSunset": {"type": "ContinueIndefinitely"}, "onConditionFailure": {"type": "NoInvalidation"},
                      "provenance": PROV},
        "pipeline": PIPELINE, "provenance": PROV})


regime("SYN_DEFAULT", "SYN-RATE-DEFAULT", True, "DEFAULT_NO_ACTION", "NONE", DISALLOWANCES, [])
regime("SYN_CLASSIC", "SYN-RATE-CLASSIC", False, "OPT_OUT", "PRESCRIBED_FORM", [], [],
       form={"number": "SYN-1", "displayName": "Synthetic option form"})
regime("SYN_FLAT", "SYN-RATE-FLAT", False, "OPT_IN", "IN_THE_RETURN", DISALLOWANCES, ["ELIG-RESIDENT"],
       in_force_to="2101-03-31")

with open(os.path.join(ROOT, 'shipped-years.json'), 'w') as f:
    f.write(json.dumps({"_note": NOTE, "taxYears": ["2099-00", "2100-01", "2101-02"]}, indent=2) + '\n')
print('written', sum(len(fs) for _, _, fs in os.walk(ROOT)), 'files')
