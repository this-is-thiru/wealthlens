# NodeHarnessTest's doubling stub runs under anyRegimeIn(pack), a salted map's first key, so its slab row varies per
# JVM; reduce those records to their amounts before diffing (D-271).
import re,sys
from decimal import Decimal
pat=re.compile(r'^HARNESS tax\.bybasis .*kind=TAX, in=MoneyValue\[amount=([\d.]+)\], out=MoneyValue\[amount=([\d.]+)\]')
n=0; out=[]
for l in open(sys.argv[1]):
    m=pat.match(l)
    if m and Decimal(m.group(2))==2*Decimal(m.group(1)) and l.count('TraceLine[')==1:
        l='HARNESS tax.bybasis <doubling stub under any regime> in='+m.group(1)+' out='+m.group(2)+'\n'; n+=1
    out.append(l)
open(sys.argv[2],'w').writelines(sorted(out)); print(sys.argv[1],'masked',n)
