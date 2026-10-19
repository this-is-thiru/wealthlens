# Lists methods longer than N lines (signature to closing brace) under the given source roots; nested types it
# also catches are to be ignored by eye (W-088u, D-270). Use: python3 method_lengths.py 40 <root>...
import re,sys,os
# Counts each method's lines from its signature to the matching closing brace at the same indent.
sig=re.compile(r'^( {4}| {8})(?:(?:public|private|protected|static|final|synchronized|abstract|default)\s+)*(?:<[^>]+>\s+)?[\w<>\[\], .?]+\s+(\w+)\s*\([^;]*$')
out=[]
for root in sys.argv[2:]:
    for d,_,fs in os.walk(root):
        for f in fs:
            if not f.endswith('.java'): continue
            p=os.path.join(d,f); L=open(p).read().split('\n')
            i=0
            while i<len(L):
                m=sig.match(L[i])
                if m and not L[i].strip().startswith(('return','new ','if','for','while','else','case','throw')):
                    ind=m.group(1); j=i
                    while j<len(L) and not L[j].rstrip().endswith('{'):
                        if L[j].rstrip().endswith(';'): break
                        j+=1
                    if j<len(L) and L[j].rstrip().endswith('{'):
                        k=j+1
                        while k<len(L) and not (L[k].startswith(ind+'}') and not L[k].startswith(ind+' ')): k+=1
                        n=k-i+1
                        if n>int(sys.argv[1]): out.append((n,p.split('wealthlens/')[-1],m.group(2),i+1))
                        i=k
                i+=1
for n,p,name,line in sorted(out,reverse=True): print(n,p.replace('backend/src/main/java/com/thiru/wealthlens/',''),name,line)
