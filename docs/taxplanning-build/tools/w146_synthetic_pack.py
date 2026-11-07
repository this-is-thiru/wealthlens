"""W-146's probe (session 49): writes the synthetic timeline to backend/src/test/resources/rulepacks-synthetic.

Usage from the repo root: [BORROW=1] python3 -I docs/taxplanning-build/tools/w146_synthetic_pack.py .
then copy zz_synthetic_spike_test.java.txt to backend/src/test/java/com/thiru/wealthlens/taxengine/rules/timeline/
ZzSyntheticSpikeTest.java, delete backend/target/test-classes/rulepacks-synthetic, and run
./mvnw test -pl backend -Dtest=ZzSyntheticSpikeTest -Dcheckstyle.skip -Dspotless.check.skip=true -Djacoco.skip=true
(it fails by design, printing each year's validation, computations and comparison; it writes the locks itself).
Delete the spike test and the generated resources afterwards. BORROW=1 adds the shipped rows for every other rule set
with their regimes renamed — a probe past finding F1 only, never the deliverable. Findings: plans/W-146.md.
"""
import json, os, shutil, sys

ROOT = os.path.join(sys.argv[1], 'backend/src/test/resources/rulepacks-synthetic')
TL = os.path.join(ROOT, 'timeline')
SHIPPED = os.path.join(sys.argv[1], 'backend/src/main/resources/rulepacks/timeline')

NOTE = "Synthetic law for W-146 (DR 10 E8, DR 17 T17-11, DR 18 T18-13): not law, never shipped."
PROV = {"sourceUrl": "https://example.invalid/w-146-synthetic", "sourceDescription": NOTE,
        "accessedOn": "2026-11-07", "verificationLevel": "UNVERIFIED",
        "whatToCheck": "nothing: synthetic figures chosen for W-146's hand arithmetic"}
START = "2099-04-01"


def write(rel, obj):
    p = os.path.join(TL, rel)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, 'w') as f:
        f.write(json.dumps(obj, indent=2, ensure_ascii=False) + '\n')


def provision(ref):
    return {"instrument": "Synthetic Act, 2099", "reference": ref, "presence": "PRESENT"}


def version(frm, to, regimes, cap, ref, extra=None):
    validity = {"type": "Between", "from": frm}
    if to:
        validity["to"] = to
    v = {"validity": validity, "regimes": regimes,
         "provision1961": {"instrument": "Income-tax Act, 1961", "reference": "none", "presence": "ABSENT_IN_THIS_ACT"},
         "provision2025": provision(ref), "cap": cap, "conditions": [],
         "introducedBy": {"instrumentCitation": "Synthetic Act, 2099, " + ref, "gazetteRef": "SYNTHETIC",
                          "notifiedOn": "2099-03-01", "effectiveFrom": frm, "retrospective": False,
                          "sourceUrl": PROV["sourceUrl"], "verificationLevel": "UNVERIFIED"},
         "legalStatus": "IN_FORCE", "publicationStatus": "PUBLISHED", "provenance": PROV}
    if cap is None:
        del v["cap"]
    if extra:
        v.update(extra)
    return v


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


shutil.rmtree(ROOT, ignore_errors=True)

# Rate tables — RS-RATE-SLAB, one row per regime
rule("SYN-RATE-DEFAULT", "RS-RATE-SLAB",
     [version(START, None, only("SYN_DEFAULT"), ladder([("0", "500000", "0"), ("500000", None, "10")]), "s.1")])
rule("SYN-RATE-CLASSIC", "RS-RATE-SLAB",
     [version(START, None, only("SYN_CLASSIC"), ladder([("0", "300000", "0"), ("300000", None, "20")]), "s.2")])
rule("SYN-RATE-FLAT", "RS-RATE-SLAB",
     [version(START, None, only("SYN_FLAT"), ladder([("0", None, "8")]), "s.3")])

# What salary includes — the row gross salary cites
rule("SYN-SALARY-INCLUDES", "RS-SAL-SALARY-INCLUDES",
     [version(START, None, {"type": "AllRegimes"}, None, "s.10",
              extra={"definition": {"type": "HeadTotal", "head": "SALARIES"}})])

# T18-13: a limit that changes on 1 July — the standard deduction, not in SYN_FLAT
def std(amount):
    return {"type": "LeastOf", "limbs": [{"name": "statutory amount", "shape": {"type": "Fixed", "amount": amount}},
                                         {"name": "salary", "shape": {"type": "PercentOfBase", "percent": "100",
                                                                      "base": "SALARY"}}]}
EXCEPT_FLAT = {"type": "ExceptIn", "regimes": ["SYN_FLAT"]}
rule("SYN-STANDARD-DEDUCTION", "RS-SAL-STANDARD-DEDUCTION",
     [version(START, "2099-06-30", EXCEPT_FLAT, std("50000"), "s.11"),
      version("2099-07-01", None, EXCEPT_FLAT, std("80000"), "s.11 as amended")],
     combination="PRORATE_PER_SEGMENT", basis="DAYS")

# Rule sets: copied verbatim from the shipped declaration for the sets these rows sit in
shipped_sets = json.load(open(os.path.join(SHIPPED, 'law/rule-sets.json')))['ruleSets']
wanted = {"RS-RATE-SLAB", "RS-SAL-SALARY-INCLUDES", "RS-SAL-STANDARD-DEDUCTION"}
write("law/rule-sets.json", {"_note": NOTE, "ruleSets": shipped_sets})

# Regimes
def protocol(kind, vehicle, binds):
    return {"kind": kind, "vehicle": vehicle, "deadline": {"type": "NoDeadline"},
            "binding": {"bindsSubsequentYears": binds, "maxWithdrawals": -1, "afterWithdrawal": {"type": "MayReElect"},
                        "firstReturnOnly": False, "bindingAppliesWhen": []},
            "provenance": PROV}

PIPELINE = json.load(open(os.path.join(SHIPPED, 'law/regimes/NEW_TAX_REGIME.json')))['pipeline']['nodeRefs']

def regime(code, rate, default, kind, vehicle, binds, in_force_to=None, form=None):
    window = {"from": START}
    if in_force_to:
        window["to"] = in_force_to
    r = {"code": code, "displayName": code + " (synthetic)", "appliesTo": ["INDIVIDUAL"], "rateTable": rate,
         "disallowances": [], "eligibility": [], "isDefault": default,
         "provision1961": {"instrument": "Income-tax Act, 1961", "reference": "none", "presence": "ABSENT_IN_THIS_ACT"},
         "provision2025": provision("s.1"),
         "elections": [{"validity": {"type": "Between", "from": START}, "protocol": protocol(kind, vehicle, binds)}],
         "lifecycle": {"entryWindow": dict(window), "inForceWindow": dict(window),
                       "onSunset": {"type": "ContinueIndefinitely"}, "onConditionFailure": {"type": "NoInvalidation"},
                       "provenance": PROV},
         "pipeline": {"nodeRefs": PIPELINE}, "provenance": PROV}
    if form:
        r["elections"][0]["protocol"]["form"] = form
    write("law/regimes/" + code + ".json", r)

regime("SYN_DEFAULT", "SYN-RATE-DEFAULT", True, "DEFAULT_NO_ACTION", "NONE", False)
regime("SYN_CLASSIC", "SYN-RATE-CLASSIC", False, "OPT_OUT", "PRESCRIBED_FORM", False,
       form={"number": "SYN-1", "displayName": "Synthetic option form"})
regime("SYN_FLAT", "SYN-RATE-FLAT", False, "OPT_IN", "IN_THE_RETURN", True, in_force_to="2101-03-31")

# Pipeline nodes and the one component — exec vocabulary, copied
for node in PIPELINE:
    shutil.copy(os.path.join(SHIPPED, 'exec/pipelines', node + '.json'), os.path.join(TL, 'exec/pipelines') if os.path.isdir(os.path.join(TL, 'exec/pipelines')) else (os.makedirs(os.path.join(TL, 'exec/pipelines')) or os.path.join(TL, 'exec/pipelines')))
write("exec/components/BASIC.json", {"code": "BASIC", "_note": NOTE, "displayName": "Basic salary", "head": "SALARIES",
      "kind": "SALARY", "cashNature": "CASH", "treatment": "TAXABLE", "feedsInto": ["SYN-SALARY-INCLUDES"],
      "salaryBaseFor": [], "lifecycle": {"validity": {"from": START}, "onWithdrawal": {"type": "HistoricalOnly"}},
      "provenance": PROV})

# Manifest and years
os.makedirs(ROOT, exist_ok=True)
with open(os.path.join(ROOT, 'shipped-years.json'), 'w') as f:
    f.write(json.dumps({"_note": NOTE, "taxYears": ["2099-00", "2100-01", "2101-02"]}, indent=2) + '\n')
print('written', sum(len(fs) for _, _, fs in os.walk(ROOT)), 'files')

# PROBE ONLY (not the deliverable): borrow the shipped rows for every other set, regimes renamed
if os.environ.get('BORROW') == '1':
    MAP = {"NEW_TAX_REGIME": ["SYN_DEFAULT", "SYN_FLAT"], "FIRST_SCHEDULE_RATES": ["SYN_CLASSIC"]}
    own = {"RS-RATE-SLAB", "RS-SAL-SALARY-INCLUDES", "RS-SAL-STANDARD-DEDUCTION"}
    def remap(o):
        if isinstance(o, dict):
            if o.get("type") in ("OnlyIn", "ExceptIn") and "regimes" in o:
                o["regimes"] = sorted({c for r in o["regimes"] for c in MAP.get(r, [r])})
            for v in o.values(): remap(v)
        elif isinstance(o, list):
            for v in o: remap(v)
    for f in os.listdir(os.path.join(SHIPPED, 'law/rules')):
        r = json.load(open(os.path.join(SHIPPED, 'law/rules', f)))
        if r.get("ruleSet") in own: continue
        remap(r); write("law/rules/" + f, r)
    for f in os.listdir(os.path.join(SHIPPED, 'exec/components')):
        if f == 'BASIC.json': continue
        shutil.copy(os.path.join(SHIPPED, 'exec/components', f), os.path.join(TL, 'exec/components', f))
    for f in os.listdir(os.path.join(SHIPPED, 'law/disallowances')):
        d = json.load(open(os.path.join(SHIPPED, 'law/disallowances', f))); remap(d)
        write("law/disallowances/" + f, d)
    for code in ("SYN_DEFAULT", "SYN_FLAT"):
        p = os.path.join(TL, 'law/regimes', code + '.json'); r = json.load(open(p))
        r["disallowances"] = [f[:-5] for f in os.listdir(os.path.join(SHIPPED, 'law/disallowances'))]
        write('law/regimes/' + code + '.json', r)
    print('borrowed')
if os.environ.get('BORROW') == '1':
    os.remove(os.path.join(TL, 'law/rules/SYN-SALARY-INCLUDES.json'))
    r = json.load(open(os.path.join(SHIPPED, 'law/rules/SAL-SALARY-INCLUDES.json'))); remap(r)
    write('law/rules/SAL-SALARY-INCLUDES.json', r)
    shutil.copy(os.path.join(SHIPPED, 'exec/components/BASIC.json'), os.path.join(TL, 'exec/components/BASIC.json'))
