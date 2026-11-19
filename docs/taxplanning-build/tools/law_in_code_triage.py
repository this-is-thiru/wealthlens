# Law-in-code audit triage (W-088l1, session 28). Usage, from a module root (e.g. backend/src/main/java/com/thiru/wealthlens/taxengine):
#   python3 -I <repo>/docs/taxplanning-build/tools/law_in_code_triage.py . <out.txt>
# Runs G1-G7 with comments, strings and text blocks blanked (so a section number in a trace string is not a hit), G8 on the raw
# text, skips rules/generated/, and writes every hit as path:line: text under a "## Gn count" heading.
import sys, os, re, collections
root, out = sys.argv[1], sys.argv[2]
def code_lines(path):
    src = open(path).read()
    # blank out comments and string/char/text-block literals, keep line structure
    res=[]; i=0; n=len(src); buf=[]
    while i<n:
        c=src[i]
        if src.startswith('/*',i):
            j=src.find('*/',i+2); j=n if j<0 else j+2; buf.append(re.sub(r'[^\n]',' ',src[i:j])); i=j
        elif src.startswith('//',i):
            j=src.find('\n',i); j=n if j<0 else j; buf.append(' '*(j-i)); i=j
        elif src.startswith('"""',i):
            j=src.find('"""',i+3); j=n if j<0 else j+3; buf.append(re.sub(r'[^\n]',' ',src[i:j])); i=j
        elif c=='"':
            j=i+1
            while j<n and src[j]!='"':
                j+= 2 if src[j]=='\\' else 1
            buf.append('"'+' '*(j-i-1)+'"'); i=j+1
        else:
            buf.append(c); i+=1
    return ''.join(buf).split('\n'), src.split('\n')
P={
 'G1':r'LocalDate\.of\(|Month\.[A-Z]+|MonthDay\.of|Year\.of',
 'G2':r'(?<![A-Za-z_.0-9])[0-9]{3,}(?![0-9])|(?<![A-Za-z_])[0-9]+\.[0-9]+|BigDecimal\.valueOf\(',
 'G3':r'Money\.of\(|Rate\.of\(|new Rate\(|percent\(',
 'G4':r'switch \(|case [A-Z_]+(,| ->|:)',
 'G5':r'EnumSet\.|EnumMap|Set\.of\(|Map\.of\(|List\.of\([A-Z][A-Za-z]+\.[A-Z_]+',
 'G6':r'== [A-Z][A-Za-z]+\.[A-Z_]{2,}|\.equals\([A-Z][A-Za-z]+\.[A-Z_]{2,}\)|!= [A-Z][A-Za-z]+\.[A-Z_]{2,}',
 'G7':r'static final [A-Za-z<>, ]+ [A-Z_]+ =',
}
hits=collections.defaultdict(list)
for d,_,fs in os.walk(root):
    for f in fs:
        if not f.endswith('.java'): continue
        p=os.path.join(d,f); rel=os.path.relpath(p,root)
        if rel.startswith('rules/generated/'): continue
        code, raw = code_lines(p)
        for k,pat in P.items():
            for i,l in enumerate(code):
                if re.search(pat,l): hits[k].append((rel,i+1,raw[i].strip()))
        # G8 on raw: quoted upper codes
        for i,l in enumerate(raw):
            if re.search(r'"[A-Z][A-Z0-9_]{3,}"',l) and not l.strip().startswith(('*','//')):
                hits['G8'].append((rel,i+1,l.strip()))
with open(out,'w') as o:
    for k in sorted(hits):
        o.write(f'## {k} {len(hits[k])}\n')
        for rel,ln,t in hits[k]: o.write(f'{rel}:{ln}: {t[:200]}\n')
for k in sorted(hits): print(k,len(hits[k]))
