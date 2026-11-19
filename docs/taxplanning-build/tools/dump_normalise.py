# Sorts every name={...} map (salted Map.copyOf order) and then the records, so two dumps can be diffed (D-265).
import re,sys
def fix(m): return m.group(1)+'={'+', '.join(sorted(m.group(2).split(', ')))+'}'
open(sys.argv[2],'w').writelines(sorted(re.sub(r'(\w+)=\{([^}]*)\}',fix,l) for l in open(sys.argv[1])))
