"""Build the Income-tax Act, 1961 section list as in force for AY 2026-27 (W-045o2, D-067).

Usage: python3 build_act_1961.py <thc.nic.in "Income Tax Act 1961_.pdf"> <output .json>
Needs: pypdf.

The base is the arrangement of sections in the thc.nic.in copy, which is current to the Finance Act,
2019 (Act 7 of 2019). CHAIN is every change to the section list since then that was found and read
in the amending Act itself (page = the Act's PDF page); nothing here is from a consolidation. Where
an entry could not be sourced it says so in `source`, and the plan's step-2 review lists it.
"""
import json
import re
import sys

import pypdf

EFF = 'with effect from '
# (section, chapter or None to inherit from the preceding section, kind, source)
CHAIN = [
    ('9B', None, 'inserted', 'Finance Act, 2021 (13 of 2021) p.14'),
    ('12AB', None, 'inserted', 'Finance Act, 2020 (12 of 2020) p.20; omitted and re-inserted by TOLA 2020 (38 of 2020) p.12, '
                               + EFF + '1-6-2020'),
    ('12AC', None, 'inserted', 'Finance (No. 2) Act, 2024 (15 of 2024) p.21, ' + EFF + '1-4-2025'),
    ('44BBC', None, 'inserted', 'Finance (No. 2) Act, 2024 (15 of 2024) p.22, ' + EFF + '1-4-2025'),
    ('44BBD', None, 'inserted', 'Finance Act, 2025 (7 of 2025) p.20, ' + EFF + '1-4-2026'),
    ('79A', None, 'inserted', 'Finance Act, 2022 (6 of 2022) p.28'),
    ('80CCH', None, 'inserted', 'Finance Act, 2023 (8 of 2023) p.30'),
    ('80EEA', None, 'inserted', 'Finance (No. 2) Act, 2019 (23 of 2019) p.16, ' + EFF + '1-4-2020'),
    ('80EEB', None, 'inserted', 'Finance (No. 2) Act, 2019 (23 of 2019) p.16, "Insertion of new sections 80EEA and 80EEB"'),
    ('80M', None, 'inserted', 'Finance Act, 2020 (12 of 2020) p.30, ' + EFF + '1-4-2021 (number re-used)'),
    ('89A', None, 'inserted', 'Finance Act, 2021 (13 of 2021) p.33, ' + EFF + '1-4-2022 (number re-used)'),
    ('115BAA', None, 'inserted', 'Taxation Laws (Amendment) Act, 2019 (46 of 2019) p.2, ' + EFF + '1-4-2020'),
    ('115BAB', None, 'inserted', 'Taxation Laws (Amendment) Act, 2019 (46 of 2019) p.2, "Insertion of new sections 115BAA and 115BAB"'),
    ('115BAC', None, 'inserted', 'Finance Act, 2020 (12 of 2020) p.33, ' + EFF + '1-4-2021'),
    ('115BAD', None, 'inserted', 'Finance Act, 2020 (12 of 2020) p.33, "Insertion of new sections 115BAC and 115BAD"'),
    ('115BAE', None, 'inserted', 'Finance Act, 2023 (8 of 2023) p.35, ' + EFF + '1-4-2024'),
    ('115BBH', None, 'inserted', 'Finance Act, 2022 (6 of 2022) p.30, ' + EFF + '1-4-2023'),
    ('115BBI', None, 'inserted', 'Finance Act, 2022 (6 of 2022) p.30, "Insertion of new sections 115BBH and 115BBI"'),
    ('115BBJ', None, 'inserted', 'Finance Act, 2023 (8 of 2023) p.37, ' + EFF + '1-4-2024'),
    ('119A', None, 'inserted', 'Finance Act, 2020 (12 of 2020) p.37'),
    ('130', None, 'inserted', 'TOLA 2020 (38 of 2020) p.20, ' + EFF + '1-11-2020 (number re-used)'),
    ('135A', None, 'inserted', 'TOLA 2020 (38 of 2020) p.21, ' + EFF + '1-11-2020'),
    ('140B', None, 'inserted', 'Finance Act, 2022 (6 of 2022) p.35'),
    ('142B', None, 'inserted', 'TOLA 2020 (38 of 2020) p.22, ' + EFF + '1-11-2020'),
    ('144B', None, 'inserted', 'TOLA 2020 (38 of 2020) p.23, ' + EFF + '1-4-2021 (number re-used)'),
    ('148A', None, 'inserted', 'Finance Act, 2021 (13 of 2021) p.38'),
    ('148B', None, 'inserted', 'Finance Act, 2022 (6 of 2022) p.47'),
    ('151A', None, 'inserted', 'TOLA 2020 (38 of 2020) p.33, ' + EFF + '1-11-2020'),
    ('156A', None, 'inserted', 'Finance Act, 2022 (6 of 2022) p.50'),
    ('157A', None, 'inserted', 'TOLA 2020 (38 of 2020) p.33, ' + EFF + '1-11-2020'),
    ('158AB', None, 'inserted', 'Finance Act, 2022 (6 of 2022) p.50'),
    ('170A', None, 'inserted', 'Finance Act, 2022 (6 of 2022) p.51'),
    ('194BA', None, 'inserted', 'Finance Act, 2023 (8 of 2023) p.45'),
    ('194K', None, 'inserted', 'Finance Act, 2020 (12 of 2020) p.41 (number re-used)'),
    ('194M', None, 'inserted', 'Finance (No. 2) Act, 2019 (23 of 2019) p.25, ' + EFF + '1-9-2019'),
    ('194N', None, 'inserted', 'Finance Act, 2020 (12 of 2020) p.42, substituted ' + EFF + '1-7-2020'),
    ('194-O', None, 'inserted', 'Finance Act, 2020 (12 of 2020) p.43, ' + EFF + '1-10-2020'),
    ('194P', None, 'inserted', 'Finance Act, 2021 (13 of 2021) p.42, ' + EFF + '1-7-2021'),
    ('194Q', None, 'inserted', 'Finance Act, 2021 (13 of 2021) p.42, ' + EFF + '1-7-2021'),
    ('194R', None, 'inserted', 'Finance Act, 2022 (6 of 2022) p.52, ' + EFF + '1-7-2022'),
    ('194S', None, 'inserted', 'Finance Act, 2022 (6 of 2022) p.52, ' + EFF + '1-7-2022'),
    ('194T', None, 'inserted', 'Finance (No. 2) Act, 2024 (15 of 2024) p.43, ' + EFF + '1-4-2025'),
    ('197B', None, 'inserted', 'TOLA 2020 (38 of 2020) p.34, ' + EFF + '14-5-2020'),
    ('206AB', None, 'omitted', 'inserted by Finance Act, 2021 (13 of 2021) p.43; omitted by Finance Act, 2025 (7 of 2025) p.34'),
    ('206CCA', None, 'omitted', 'inserted by Finance Act, 2021 (13 of 2021) p.44; omitted by Finance Act, 2025 (7 of 2025) p.35'),
    ('231', None, 'inserted', 'TOLA 2020 (38 of 2020) p.34, ' + EFF + '1-11-2020 (number re-used)'),
    ('234G', None, 'inserted', 'Finance Act, 2020 (12 of 2020) p.48, ' + EFF + '1-6-2020'),
    ('234H', None, 'inserted', 'Finance Act, 2021 (13 of 2021) p.45'),
    ('234-I', None, 'inserted', 'Finance Act, 2026 (4 of 2026) p.34, deemed ' + EFF + '1-3-2026'),
    ('239A', None, 'inserted', 'Finance Act, 2022 (6 of 2022) p.55'),
    ('245AA', None, 'inserted', 'Finance Act, 2021 (13 of 2021) p.46, ' + EFF + '1-2-2021'),
    ('245M', None, 'inserted', 'Finance Act, 2021 (13 of 2021) p.48, ' + EFF + '1-2-2021 (number re-used)'),
    ('245MA', 'XIX-AA', 'inserted', 'Finance Act, 2021 (13 of 2021) p.49, new Chapter XIX-AA, ' + EFF + '1-4-2021'),
    ('245-OA', None, 'inserted', 'UNSOURCED insertion: not in the 2019 arrangement; Finance Act, 2021 (13 of 2021) p.52 refers to'
                                 ' "section 245-OA of the Income-tax Act" as existing'),
    ('245-OB', None, 'inserted', 'Finance Act, 2021 (13 of 2021) p.52'),
    ('245W', None, 'inserted', 'Finance Act, 2021 (13 of 2021) p.54'),
    ('264A', None, 'inserted', 'TOLA 2020 (38 of 2020) p.36, ' + EFF + '1-11-2020'),
    ('264B', None, 'inserted', 'TOLA 2020 (38 of 2020) p.36, ' + EFF + '1-11-2020'),
    ('269SU', None, 'inserted', 'Finance (No. 2) Act, 2019 (23 of 2019) p.28, ' + EFF + '1-11-2019'),
    ('271AAD', None, 'inserted', 'Finance Act, 2020 (12 of 2020) p.49'),
    ('271AAE', None, 'inserted', 'Finance Act, 2022 (6 of 2022) p.57, ' + EFF + '1-4-2023'),
    ('271DB', None, 'inserted', 'Finance (No. 2) Act, 2019 (23 of 2019) p.28, ' + EFF + '1-11-2019'),
    ('271GC', None, 'inserted', 'Finance (No. 2) Act, 2024 (15 of 2024) p.47, ' + EFF + '1-4-2025'),
    ('271K', None, 'inserted', 'Finance Act, 2020 (12 of 2020) p.49; omitted and re-inserted by TOLA 2020 (38 of 2020) p.37'),
    ('285BAA', None, 'inserted', 'Finance Act, 2025 (7 of 2025) p.38, ' + EFF + '1-4-2026'),
    ('285BB', None, 'inserted', 'Finance Act, 2020 (12 of 2020) p.50, ' + EFF + '1-6-2020'),
    ('292BA', None, 'inserted', 'Finance Act, 2026 (4 of 2026) p.40, deemed ' + EFF + '1-10-2019'),
    ('292BC', None, 'inserted', 'Finance Act, 2026 (4 of 2026) p.40, deemed ' + EFF + '1-4-2021'),
    ('293D', None, 'inserted', 'TOLA 2020 (38 of 2020) p.37, ' + EFF + '1-11-2020'),
    ('88', None, 'omitted', 'Finance Act, 2023 (8 of 2023) p.32, s.45'),
    ('158BI', None, 'omitted', 'Finance Act, 2025 (7 of 2025) p.33, deemed omitted'),
    ('194F', None, 'omitted', 'Finance (No. 2) Act, 2024 (15 of 2024) p.42, ' + EFF + '1-10-2024'),
    ('203AA', None, 'omitted', 'Finance Act, 2020 (12 of 2020) p.45, ' + EFF + '1-6-2020'),
    ('271BB', None, 'omitted', 'Finance Act, 2025 (7 of 2025) p.36'),
    ('269S', None, 'omitted', 'UNSOURCED omission: a consolidation cites the Jammu and Kashmir Reorganisation (Adaptation of'
                              ' Central Laws) Order, 2020, which was not read'),
    ('76', None, 'omitted', 'UNSOURCED omission: absent from the arrangement of the thc.nic.in copy; omitting Act not found'),
    ('77', None, 'omitted', 'UNSOURCED omission: absent from the arrangement of the thc.nic.in copy; omitting Act not found'),
    ('186', None, 'omitted', 'UNSOURCED omission: absent from the arrangement of the thc.nic.in copy; omitting Act not found'),
]
SID = r'(\d{1,3}[A-Z]{0,4}(?:\s*-\s*[A-Z]{1,3})?)'


def key(section):
    m = re.match(r'(\d+)-?([A-Z]*)', section)
    return int(m.group(1)), m.group(2)


def arrangement(pdf):
    pages = [p.extract_text() or '' for p in pypdf.PdfReader(pdf).pages[:29]]
    entries, chapter, part = [], None, None
    for line in (l.strip() for l in '\n'.join(pages).split('\n')):
        if (m := re.match(r'^CHAPTER\s+([IVXLC]+(?:\s*-?\s*[A-Z]{1,3})?)\s*$', line)):
            chapter, part = re.sub(r'\s', '', m.group(1)), None
        elif (m := re.match(r'^([A-Z]{1,2})\.\s*[—–-]+\s*(.+)$', line)):
            part = m.group(1) + '. ' + m.group(2)[:60]
        elif (m := re.match(r'^' + SID + r'\.\s*(.*)$', line)) and chapter:
            entries.append({'section': re.sub(r'\s', '', m.group(1)), 'chapter': chapter, 'part': part,
                            'omitted': bool(re.search(r'\[\s*Omitted', m.group(2))),
                            'source': 'arrangement, thc.nic.in copy (to Act 7 of 2019)'})
    return entries


def main(pdf, out_path):
    by_id = {e['section']: e for e in arrangement(pdf)}
    for section, chapter, kind, source in CHAIN:
        entry = by_id.setdefault(section, {'section': section, 'chapter': chapter})
        entry['omitted'] = kind == 'omitted'
        entry['source'] = source
        if chapter:
            entry['chapter'] = chapter
    ordered = sorted(by_id.values(), key=lambda e: key(e['section']))
    for previous, entry in zip(ordered, ordered[1:]):
        if 'part' not in entry:
            entry['part'] = previous['part'] if not entry.get('chapter') else None
        entry['chapter'] = entry.get('chapter') or previous['chapter']
    numbers = {key(e['section'])[0] for e in ordered if not key(e['section'])[1]}
    missing = [n for n in range(1, max(numbers) + 1) if n not in numbers]
    if missing:
        sys.exit(f'not continuous: {missing}')
    json.dump(ordered, open(out_path, 'w'), ensure_ascii=False, indent=1)
    print(f'{len(ordered)} entries, {sum(e["omitted"] for e in ordered)} omitted, '
          f'{sum(not e["omitted"] for e in ordered)} in force')


if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2])
