# Rule-pack relock (session 26): rewrite both locks from a CKSUM listing printed by zz_print_checksums_test.java.txt.
# Usage from the repo root: python3 -I relock_rulepacks.py <cksum.txt> backend/src/main/resources/rulepacks <BUMP 0|1>
# BUMP=1 bumps a layer's version only where its checksum changed (once per item); BUMP=0 for bite probes.
# It asserts no rule was removed and prints the rules added and changed - check they are exactly the item's rows.
# Full procedure: run_relock.sh beside it.
import json, re, sys, os
out, locks_dir, bump = sys.argv[1], sys.argv[2], sys.argv[3] == '1'
lines = [l for l in open(out).read().split('\n') if l.startswith('CKSUM ')]
assert lines, 'no CKSUM lines'
for y, label in ((2025, '2025-26'), (2026, '2026-27')):
    p = os.path.join(locks_dir, label + '.lock.json')
    lock = json.load(open(p))
    layers = {}; rules = {}
    for l in lines:
        _, yy, kind, name, cs = l.split(' ')
        if int(yy) != y: continue
        (layers if kind == 'LAYER' else rules)[name] = cs
    for name, cs in layers.items():
        key = name.lower(); entry = lock['layers'][key]
        if entry['checksum'] != cs:
            print(label, 'layer changed', key)
            entry['checksum'] = cs
            if bump:
                m = re.match(r'(.*\.)(\d+)$', entry['version']); entry['version'] = m.group(1) + str(int(m.group(2)) + 1)
                print('  ->', entry['version'])
    old = lock['rules']
    removed = set(old) - set(rules); assert not removed, removed
    print(label, 'rules added', sorted(set(rules) - set(old)))
    print(label, 'rules changed', sorted(k for k in rules if k in old and old[k] != rules[k]))
    lock['rules'] = dict(sorted(rules.items()))
    open(p, 'w').write(json.dumps(lock, indent=2, ensure_ascii=False) + '\n')
