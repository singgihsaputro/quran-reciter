#!/usr/bin/env python3
"""Fetch Uthmani verse text from the Quran.com API into app assets.

The text is never typed by hand — it is fetched from a reputable published
source and committed verbatim. Rerun this to regenerate; the output is stable.

Source: https://api.quran.com/api/v4 (Uthmani script, KFGQPC-derived)
"""
import html
import json
import pathlib
import re
import urllib.request

OUT = pathlib.Path(__file__).resolve().parent.parent / 'app/src/main/assets/quran.json'

# Short surahs, the ones a learner practises first.
SURAHS = [
    (1, 'Al-Fatihah', 'The Opening'),
    (103, 'Al-Asr', 'The Declining Day'),
    (108, 'Al-Kawthar', 'Abundance'),
    (112, 'Al-Ikhlas', 'Sincerity'),
    (113, 'Al-Falaq', 'The Daybreak'),
    (114, 'An-Nas', 'Mankind'),
]


def plain(text):
    """Translations arrive with footnote markup — <sup foot_note=...> and friends."""
    text = re.sub(r'<sup[^>]*>.*?</sup>', '', text, flags=re.S)
    text = re.sub(r'<[^>]+>', '', text)
    return html.unescape(text).strip()


def get(url):
    req = urllib.request.Request(url, headers={'User-Agent': 'quran-reciter'})
    with urllib.request.urlopen(req, timeout=30) as r:
        return json.load(r)


def main():
    out = []
    for number, name, meaning in SURAHS:
        arabic = get(
            f'https://api.quran.com/api/v4/quran/verses/uthmani?chapter_number={number}'
        )['verses']
        english = get(
            'https://api.quran.com/api/v4/quran/translations/20'
            f'?chapter_number={number}'
        )['translations']

        if len(arabic) != len(english):
            raise SystemExit(f'surah {number}: {len(arabic)} verses vs {len(english)} translations')

        verses = [
            {
                'number': i + 1,
                'arabic': a['text_uthmani'].strip(),
                'translation': plain(e['text']),
            }
            for i, (a, e) in enumerate(zip(arabic, english))
        ]
        if not verses or not all(v['arabic'] for v in verses):
            raise SystemExit(f'surah {number}: empty verse text, refusing to write')

        out.append({
            'number': number,
            'name': name,
            'meaning': meaning,
            'verses': verses,
        })
        print(f'  {number:>3} {name:<12} {len(verses)} verses')

    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(out, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(f'wrote {OUT.relative_to(OUT.parent.parent.parent.parent)}')


if __name__ == '__main__':
    main()
