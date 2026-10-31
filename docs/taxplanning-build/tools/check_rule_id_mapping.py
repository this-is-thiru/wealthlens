"""Check the W-045d2 rule-ID mapping in 01-rulebook.md (D-069).

Usage: python3 check_rule_id_mapping.py [path to 01-rulebook.md]   — exits 1 on any problem.

The old IDs are found independently of the mapping: every backticked ID in the rulebook that
RuleIdNamingTest would refuse, outside the R3 mapping of W-045d, plus DED-NEWREGIME-001, which the
test's OLD/NEW pattern misses (a fused token, W-045d3). The patterns below are the test's own.
"""
import re
import sys

RULE_ID = re.compile(r'^[A-Z]{2,8}(-[A-Z0-9]{1,12}){1,5}$')
YEAR = re.compile(r'-(19|20)\d\d\b')
OLD_OR_NEW = re.compile(r'(^|-)(OLD|NEW)(-|$)')
SECTION = re.compile(r'S\d{3}')
STATUTORY_NEW = 'NEW-TAX-REGIME'
ALSO_IN_SCOPE = {'DED-NEWREGIME-001'}
MARKER = '**Erratum (W-045d2,'


def violation(identity):
    words = identity.replace('_', '-')
    if YEAR.search(words):
        return 'carries a year'
    if OLD_OR_NEW.search(words.replace(STATUTORY_NEW, '')):
        return 'says OLD or NEW'
    if SECTION.search(words):
        return 'carries a section number'
    return None


def main(path):
    text = open(path, encoding='utf-8').read()
    start = text.index(MARKER)
    mapping_block = text[start:text.index('Verified by:', start)]
    mapped = re.findall(r'^> \| `([A-Z0-9-]+)` \| `([A-Z0-9-]+)` \|', mapping_block, re.M)
    r3_block = text[text.index('**Erratum (W-045d,'):start]
    r3_old = set(re.findall(r'^> \| `([A-Z0-9-]+)` \|', r3_block, re.M))
    outside = text[:text.index('**Erratum (W-045d,')] + text[text.index('Verified by:', start):]
    found = {i for i in re.findall(r'`([A-Z]{2,8}(?:-[A-Z0-9]{1,12}){1,6})`', outside) if violation(i)}
    expected = (found - r3_old - {'RATE-SLAB-NEW-TAX-REGIME'}) | ALSO_IN_SCOPE
    problems = []
    olds = [old for old, _ in mapped]
    problems += [f'{i} is not in the mapping' for i in sorted(expected - set(olds))]
    problems += [f'{i} is mapped {olds.count(i)} times' for i in sorted(set(olds)) if olds.count(i) != 1]
    problems += [f'{i} is mapped but is not an ID the rulebook needs renamed' for i in sorted(set(olds) - expected)]
    for old, new in mapped:
        if not RULE_ID.match(new):
            problems.append(f'{old} -> {new}: not a RuleId shape')
        elif violation(new):
            problems.append(f'{old} -> {new}: {violation(new)}')
    for p in problems:
        print('PROBLEM', p)
    print(f'{len(expected)} old IDs expected, {len(mapped)} mapped, {len({n for _, n in mapped})} new IDs, '
          f'{len(problems)} problems')
    return 1 if problems else 0


if __name__ == '__main__':
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1 else 'docs/taxplanning-design/01-rulebook.md'))
