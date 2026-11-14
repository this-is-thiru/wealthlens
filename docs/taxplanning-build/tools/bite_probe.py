# Bite probe (W-088u4 – W-088v): mutate ONE exact text in a file (asserting it occurs once), run 'clean test' of the
# named test class(es), print the failures, then restore the file and touch it (mv keeps an old mtime, and a stale
# class would hide the restore). Use from the repo root: python3 bite_probe.py . <file> '<old>' '<new>' <TestClass[,Other]>
# Integration classes need DOCKER_HOST / TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE exported first (OrbStack).
# probe.py <repo> <file> <old> <new> <mvn -Dtest value> : mutate once, run clean test, list failures, restore + touch
import os,re,shutil,subprocess,sys,glob
repo,f,old,new,tests=sys.argv[1:6]
p=os.path.join(repo,f); bak=p+'.zzbak'
s=open(p).read(); assert s.count(old)==1,('matches',s.count(old)); shutil.copy2(p,bak)
open(p,'w').write(s.replace(old,new))
try:
    r=subprocess.run(['./mvnw','clean','test','-pl','backend','-Dtest='+tests,'-Dcheckstyle.skip','-Dspotless.check.skip=true','-Dsurefire.failIfNoSpecifiedTests=false'],cwd=repo,capture_output=True,text=True)
    for line in r.stdout.splitlines():
        if re.search(r'Tests run: \d+, Fail.*(in |$)',line) and 'Time elapsed' in line or re.match(r'\[ERROR\]   \S',line): print(line.replace('[ERROR] ','').strip())
    print('totals:',[l for l in r.stdout.splitlines() if re.match(r'\[(INFO|ERROR)\] Tests run: \d+, Fail[^-]*$',l)][-1:])
finally:
    shutil.move(bak,p); os.utime(p,None)
    assert old in open(p).read()
