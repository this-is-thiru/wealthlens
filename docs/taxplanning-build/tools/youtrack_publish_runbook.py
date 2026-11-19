# Publish the tax-planning runbook to the owner's YouTrack KB (D-278, D-279): replace article WL-A-2's summary
# (the file's H1) and content (a source-of-truth header + the rest of the file). The token is read from
# ~/.config/youtrack/token (mode 600, outside the repo) and sent only as a Bearer header; never printed.
# Use: python3 -I youtrack_publish_runbook.py WL-A-2 ../post-deployment-runbook.md — then read the article back.
# update.py <article idReadable> <markdown file>: replace the article's summary (the file's H1) and content
import json, os, sys, urllib.request
token = open(os.path.expanduser('~/.config/youtrack/token')).read().strip()
article, path = sys.argv[1], sys.argv[2]
lines = open(path).read().split('\n')
assert lines[0].startswith('# ')
summary = lines[0][2:].strip()
content = ("> Source of truth: the repository file `docs/taxplanning-build/post-deployment-runbook.md` (WealthLens). "
           "This article is a copy for reading; edit the repository file and republish.\n\n" + '\n'.join(lines[1:]).lstrip('\n'))
req = urllib.request.Request(
    'https://pthirumalayadav.youtrack.cloud/api/articles/' + article + '?fields=idReadable,summary',
    data=json.dumps({'summary': summary, 'content': content}).encode(),
    headers={'Authorization': 'Bearer ' + token, 'Content-Type': 'application/json', 'Accept': 'application/json',
             'User-Agent': 'Mozilla/5.0'}, method='POST')
print(urllib.request.urlopen(req, timeout=30).read().decode())
