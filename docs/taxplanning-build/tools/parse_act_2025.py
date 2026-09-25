"""Parse the Income-tax Act, 2025 gazette into its section list (W-045o, D-066).

Usage: python3 parse_act_2025.py <path to egazette 2025/265620.pdf> <output .json>
Needs: pypdf. Output: rulepacks/timeline/content/acts/INCOME_TAX_ACT_2025.json.

Why it looks like this: the gazette has no arrangement of sections, and plain text extraction mixes
table serial numbers with section numbers. So sections are found in layout-mode text and walked in
order 1, 2, 3 ... preferring "strong" candidates (followed by "(1)", or preceded on the line by a
marginal note). Two sections the pattern cannot see are pinned with their quoted text. The walk
must be continuous or the script fails.
"""
import json
import re
import sys

import pypdf

PINNED = {
    1: (1, '1.( 1) This Act may be called the Income-tax Act, 2025.'),
    173: (201, '173 For the purposes of this section and sections 161, 162, 163, 165, 171 and 172,'
               ' unless the context otherwise requires,—'),
}
CANDIDATE = re.compile(r'(?<![\d.(A-Za-z])(\d{1,3})\s*\.\s*(\(\s*1\s*\)|[A-Z“"])')
CHAPTER = re.compile(r'\s*CHAPTER\s+([IVXLC]+)\s*$')
PART = re.compile(r'\s*([A-Z])\s*\.\s*[—–-]{1,2}\s*(\S.*?)\s*$')


def layout_lines(reader):
    for pi, page in enumerate(reader.pages):
        text = page.extract_text(extraction_mode="layout") if pi > 0 else page.extract_text()
        for li, line in enumerate(text.split('\n')):
            yield pi + 1, li, line


def main(pdf, out_path):
    reader = pypdf.PdfReader(pdf)
    candidates, chapters, parts = [], [], []
    for page, li, line in layout_lines(reader):
        for m in CANDIDATE.finditer(line):
            after, before = line[m.end(1):], line[:m.start(1)]
            strong = bool(re.match(r'\s*\.\s*\(\s*1\s*\)', after)) or bool(re.search(r'[A-Za-z]', before))
            candidates.append((page, li, int(m.group(1)), strong, re.sub(r'\s+', ' ', line.strip())))
        if (m := CHAPTER.match(line)):
            chapters.append((page, li, m.group(1)))
        elif (m := PART.match(line)) and len(m.group(2)) < 90:
            parts.append((page, li, m.group(1) + '. ' + re.sub(r'\s+', ' ', m.group(2))))

    found, pos, expected = {}, (0, -1), 1
    while True:
        if expected in PINNED:
            page, quote = PINNED[expected]
            found[expected] = (page, 10 ** 6, quote)
            pos = (page, 10 ** 6) if expected != 1 else (1, -1)
            expected += 1
            continue
        ahead = [c for c in candidates if (c[0], c[1]) > pos]
        nxt = [c for c in ahead if c[2] == expected + 1 and c[3]]
        limit = (nxt[0][0], nxt[0][1]) if nxt else (10 ** 9, 0)
        pick = next((c for c in ahead if c[2] == expected and c[3] and (c[0], c[1]) < limit), None) \
            or next((c for c in ahead if c[2] == expected and (c[0], c[1]) < limit), None)
        if not pick:
            break
        found[expected] = (pick[0], pick[1], pick[4])
        pos = (pick[0], pick[1])
        expected += 1

    last = max(found)
    missing = [n for n in range(1, last + 1) if n not in found]
    if missing:
        sys.exit(f'not continuous: missing {missing}')
    sections = []
    for n in range(1, last + 1):
        page, li, quote = found[n]
        chapter = [c for c in chapters if (c[0], c[1]) <= (page, li)][-1]
        part = [p for p in parts if (chapter[0], chapter[1]) <= (p[0], p[1]) <= (page, li)]
        sections.append({'section': str(n), 'page': page, 'chapter': chapter[2],
                         'part': part[-1][2] if part else None, 'firstWords': quote[:80]})
    json.dump(sections, open(out_path, 'w'), ensure_ascii=False, indent=1)
    print(f'{len(sections)} sections, continuous 1–{last}')


if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2])
