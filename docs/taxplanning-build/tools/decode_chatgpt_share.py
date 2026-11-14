# Decode a chatgpt.com/s/t_... share page (session 26; both formats in memory: 'longest string' and 'constants').
# Usage: curl -s -A 'Mozilla/5.0' -o share.html <url>; python3 -I decode_chatgpt_share.py share.html > decoded.txt
# Prints the answer text, then '=== LINKS ===' and every outside URL in the page. Read every cited copy yourself.
import json, re, sys
raw = open(sys.argv[1], encoding='utf-8').read()
chunks = re.findall(r'streamController\.enqueue\("((?:[^"\\]|\\.)*)"\)', raw)
text = ''.join(json.loads('"' + c + '"') for c in chunks)
first = text.split('\n', 1)[0]
arr = json.loads(first)
strings = [s for s in arr if isinstance(s, str)]
longest = max(strings, key=len)
if 'constants' in arr:
    idx = arr.index('constants'); d = arr[idx + 1]
    def res(v, depth=0):
        if depth > 50: return None
        x = arr[v] if isinstance(v, int) and 0 <= v < len(arr) else v
        if isinstance(x, dict): return {arr[int(k[1:])] if k.startswith('_') else k: res(val, depth+1) for k, val in x.items()}
        if isinstance(x, list): return [res(e, depth+1) for e in x]
        return x
    out = res(d)
    vals = out.values() if isinstance(out, dict) else out
    for v in vals:
        if isinstance(v, str) and not re.fullmatch(r'[0-9a-f-]{20,}', v): print(v)
else:
    print(longest)
print('\n=== LINKS ===')
for u in sorted(set(re.findall(r'https?://[^\s"\'<>\\)\]]+', raw))):
    if 'chatgpt.com' not in u and 'openai' not in u and 'oaistatic' not in u: print(u)
