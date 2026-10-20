# Bite probe on rule data (session 26): bite_probe.py cannot mutate a rule JSON (the lock check fails everything).
# This backs up both locks and the row, mutates OLD -> NEW (asserting N occurrences), relocks with BUMP=0, runs
# 'clean test' of the class(es), then restores every file, touches it and checks the restore byte for byte.
# Usage from the repo root: python3 -I bite_probe_rule_data.py . <ROW.json> '<old>' '<new>' <N> <TestClass> <scratch dir>
import sys, os, shutil, subprocess, re, glob
repo, rule, old, new, n, tests, sp = sys.argv[1:8]
L = os.path.join(repo, 'backend/src/main/resources/rulepacks'); R = os.path.join(L, 'timeline/law/rules', rule)
files = [os.path.join(L, '2025-26.lock.json'), os.path.join(L, '2026-27.lock.json'), R]
bak = os.path.join(sp, 'probebak'); os.makedirs(bak, exist_ok=True)
for f in files: shutil.copy2(f, os.path.join(bak, os.path.basename(f)))
try:
    s = open(R).read(); assert s.count(old) == int(n), s.count(old); open(R, 'w').write(s.replace(old, new))
    T = os.path.join(repo, 'backend/src/test/java/com/thiru/wealthlens/taxengine/rules/timeline/ZzPrintChecksumsTest.java')
    shutil.copy(os.path.join(repo, 'docs/taxplanning-build/tools/zz_print_checksums_test.java.txt'), T)
    r = subprocess.run(['./mvnw', 'test', '-pl', 'backend', '-Dtest=ZzPrintChecksumsTest', '-Dcheckstyle.skip', '-Dspotless.check.skip=true', '-Dsurefire.failIfNoSpecifiedTests=false'], cwd=repo, capture_output=True, text=True)
    os.remove(T)
    ck = sorted(set(re.sub(r'^.*CKSUM', 'CKSUM', l) for l in r.stdout.splitlines() if 'CKSUM' in l))
    open(os.path.join(sp, 'ckp.txt'), 'w').write('\n'.join(ck) + '\n')
    print(subprocess.run(['python3', '-I', os.path.join(repo, 'docs/taxplanning-build/tools/relock_rulepacks.py'), os.path.join(sp, 'ckp.txt'), L, '0'], capture_output=True, text=True).stdout)
    r = subprocess.run(['./mvnw', 'clean', 'test', '-pl', 'backend', '-Dtest=' + tests, '-Dcheckstyle.skip', '-Dspotless.check.skip=true'], cwd=repo, capture_output=True, text=True)
    for l in r.stdout.splitlines():
        if re.match(r'\[ERROR\]   \S', l) or re.match(r'\[(INFO|ERROR)\] Tests run: \d+, Fail[^-]*$', l): print(l[:260])
finally:
    for f in files: shutil.copy2(os.path.join(bak, os.path.basename(f)), f); os.utime(f, None)
    for f in files: assert open(f).read() == open(os.path.join(bak, os.path.basename(f))).read()
    assert not os.path.exists(os.path.join(repo, 'backend/src/test/java/com/thiru/wealthlens/taxengine/rules/timeline/ZzPrintChecksumsTest.java'))
    print('restored')
