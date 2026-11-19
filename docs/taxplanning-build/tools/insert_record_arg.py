# Session 27 (W-088k2): add a record component at every construction site. Inserts ARG before the last top-level
# argument of every CALL( ... ) in FILE (paren-, bracket- and string-aware), printing how many sites it changed.
# Usage from the repo root: python3 -I docs/taxplanning-build/tools/insert_record_arg.py <File.java> 'new Record' ARG
# Then clean-compile: an incremental build can report success on stale classes.
import sys
path, call, arg = sys.argv[1:4]
s = open(path).read(); out = []; i = 0; n = 0
while True:
    j = s.find(call + '(', i)
    if j < 0:
        out.append(s[i:]); break
    k = j + len(call) + 1; depth = 1; last_comma = -1; q = None
    while depth:
        c = s[k]
        if q:
            if c == '\\': k += 1
            elif c == q: q = None
        elif c in '"\'': q = c
        elif c in '([{': depth += 1
        elif c in ')]}': depth -= 1
        elif c == ',' and depth == 1: last_comma = k
        k += 1
    assert last_comma > 0
    out.append(s[i:last_comma] + ', ' + arg); i = last_comma; n += 1
open(path, 'w').write(''.join(out)); print(path, n)
