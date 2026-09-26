#!/usr/bin/env python3
"""Fetch Uthmani verse text from the Quran.com API into app assets.

The text is never typed by hand — it is fetched from a reputable published
source and committed verbatim. Rerun this to regenerate; the output is stable.

Source: https://api.quran.com/api/v4 (Uthmani script, KFGQPC-derived)
Word audio paths come from the same API; they are relative to
https://audio.qurancdn.com/ and cannot be computed from the word's position,
because the numbering skips a slot at every standalone pause mark.
"""
import html
import json
import pathlib
import re
import urllib.request

OUT = pathlib.Path(__file__).resolve().parent.parent / 'app/src/main/assets/quran.json'
# Bedtime stories are retellings written by hand — prose, not scripture. The one
# verse each story ends on is fetched here like every other verse.
STORIES = OUT.parent / 'stories.json'
STORY_VERSES = OUT.parent / 'story_verses.json'

# Al-Fatihah plus the whole of Juz 'Amma (78-114) — the short surahs, and the
# ones learners memorise first. Names come from the API too, never typed here.
SURAH_NUMBERS = [1] + list(range(78, 115))


def plain(text):
    """Translations arrive with footnote markup — <sup foot_note=...> and friends."""
    text = re.sub(r'<sup[^>]*>.*?</sup>', '', text, flags=re.S)
    text = re.sub(r'<[^>]+>', '', text)
    return html.unescape(text).strip()


def get(url):
    req = urllib.request.Request(url, headers={'User-Agent': 'quran-reciter'})
    with urllib.request.urlopen(req, timeout=30) as r:
        return json.load(r)


# A token with at least one letter is a word; a bare pause mark (ۖ ۚ ۩ ...) is not.
LETTER = re.compile('[\u0621-\u064A\u0671]')


def word_audio(number):
    """Per-verse lists of word audio paths, in reading order."""
    out, page = {}, 1
    while page:
        data = get(
            f'https://api.quran.com/api/v4/verses/by_chapter/{number}'
            f'?words=true&word_fields=audio_url&per_page=50&page={page}'
        )
        for v in data['verses']:
            out[v['verse_number']] = [
                w['audio_url'] for w in v['words'] if w['char_type_name'] == 'word'
            ]
        page = data['pagination']['next_page']
    return out


# Indonesian: the Ministry of Religious Affairs (Kemenag) translation, the one
# Indonesian children learn from.
INDONESIAN = 33


def chapter_names():
    """Surah names and their English and Indonesian meanings, from the API rather than by hand."""
    en = get('https://api.quran.com/api/v4/chapters?language=en')['chapters']
    id_ = {c['id']: c['translated_name']['name'] for c in get('https://api.quran.com/api/v4/chapters?language=id')['chapters']}
    return {
        c['id']: (c['name_simple'], c['translated_name']['name'], id_[c['id']])
        for c in en
    }


def story_verses():
    keys = [s['verse'] for s in json.loads(STORIES.read_text(encoding='utf-8'))['stories']]
    out = {}
    for key in keys:
        arabic = get(f'https://api.quran.com/api/v4/quran/verses/uthmani?verse_key={key}')['verses']
        english = get(f'https://api.quran.com/api/v4/quran/translations/20?verse_key={key}')['translations']
        indonesian = get(f'https://api.quran.com/api/v4/quran/translations/{INDONESIAN}?verse_key={key}')['translations']
        if not len(arabic) == len(english) == len(indonesian) == 1 or not arabic[0]['text_uthmani'].strip():
            raise SystemExit(f'story verse {key}: expected exactly one verse')
        out[key] = {
            'arabic': arabic[0]['text_uthmani'].strip(),
            'translation': plain(english[0]['text']),
            'indonesian': plain(indonesian[0]['text']),
        }
    STORY_VERSES.write_text(json.dumps(out, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(f'wrote {len(out)} story verses')


def main():
    names = chapter_names()
    out = []
    for number in SURAH_NUMBERS:
        name, meaning, meaning_id = names[number]
        arabic = get(
            f'https://api.quran.com/api/v4/quran/verses/uthmani?chapter_number={number}'
        )['verses']
        english = get(
            'https://api.quran.com/api/v4/quran/translations/20'
            f'?chapter_number={number}'
        )['translations']

        indonesian = get(
            f'https://api.quran.com/api/v4/quran/translations/{INDONESIAN}'
            f'?chapter_number={number}'
        )['translations']
        audio = word_audio(number)

        if not len(arabic) == len(english) == len(indonesian):
            raise SystemExit(f'surah {number}: {len(arabic)} verses vs {len(english)}/{len(indonesian)} translations')

        verses = [
            {
                'number': i + 1,
                'arabic': a['text_uthmani'].strip(),
                'translation': plain(e['text']),
                'indonesian': plain(t['text']),
                'audio': audio[i + 1],
            }
            for i, (a, e, t) in enumerate(zip(arabic, english, indonesian))
        ]
        if not verses or not all(v['arabic'] for v in verses):
            raise SystemExit(f'surah {number}: empty verse text, refusing to write')
        for v in verses:
            # The app pairs audio[i] with the i-th word it draws; a drift would
            # play the wrong word to a child learning it.
            words = [t for t in v['arabic'].split() if LETTER.search(t)]
            if len(words) != len(v['audio']):
                raise SystemExit(
                    f"{number}:{v['number']}: {len(words)} words vs {len(v['audio'])} audio"
                )

        out.append({
            'number': number,
            'name': name,
            'meaning': meaning,
            'meaning_indonesian': meaning_id,
            'verses': verses,
        })
        print(f'  {number:>3} {name:<12} {len(verses)} verses')

    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(out, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(f'wrote {OUT.relative_to(OUT.parent.parent.parent.parent)}')
    story_verses()


if __name__ == '__main__':
    main()
