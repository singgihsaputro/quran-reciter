# Recite

An Android Qur'an reader that listens while you recite and shows, word by word,
which words it matched.

## What it does

- Six short surahs — Al-Fatihah, Al-'Asr, Al-Kawthar, Al-Ikhlas, Al-Falaq, An-Nas.
- Tap a verse, tap the microphone, recite. The verse then animates: matched words
  turn green, misread words amber, skipped words grey.
- Verse words fade in right-to-left as the verse is read; the microphone's rings
  pulse to your actual input level; the score bar animates to the result.

## What it does **not** do

**It does not check your tajweed, and it cannot tell you your recitation is
correct.**

Matching works on words. To compare a speech recogniser's output against Uthmani
script, both sides are stripped of harakat, tanwin, shadda, sukun and the Quranic
annotation marks, and the alif, ya and ta-marbuta variants are folded together.
Those marks are exactly where tajweed lives — madd length, ghunnah, qalqalah,
makharij. The comparison deletes them before it starts.

So a full score means *the right words, in the right order*. It says nothing
about whether they were recited correctly. This is a memorisation aid. It is not
a teacher, and it is not a substitute for one.

Recognition quality is the device's, not this app's: it depends on the Arabic
language pack being installed and on a vendor engine that was trained on ordinary
speech, not recitation. A poor result usually means the recogniser did not
understand, not that the recitation was wrong.

## The text

Every verse is fetched from the [Quran.com API](https://api.quran.com/api/v4)
(Uthmani script) by [`tools/fetch_quran.py`](tools/fetch_quran.py) and committed
verbatim to `app/src/main/assets/quran.json`. **No Qur'anic text is typed by
hand.** Rerun the script to regenerate it. Translation is Saheeh International,
as served by the same API.

## Build and test

```bash
./gradlew :core:test        # 14 tests on the matching logic
./gradlew :app:assembleDebug
```

Needs JDK 17 and the Android SDK (`ANDROID_HOME` set), compileSdk 35, minSdk 26.

## Layout

```
core/    pure Kotlin — ArabicNormalizer and RecitationMatcher, and their tests.
         No Android dependency, so the logic that judges a recitation is
         testable without a device.
app/     Compose UI, SpeechRecognizer wrapper, bundled text.
tools/   the fetch script.
```

The matcher aligns by longest common subsequence rather than comparing position
by position — one dropped word would otherwise shift the rest of the verse and
report every later word as wrong. A word outside the alignment is *missed* when
nothing was heard near it and *misread* when something was, because skipping a
word and mispronouncing one are different mistakes to a learner.

## Honest status

- `core` is tested and green.
- `app` compiles to a debug APK.
- **Not yet run on a device or emulator.** Speech recognition needs a real
  microphone and the Arabic pack, so the end-to-end path — permission, listening,
  recognition, scoring — is unverified. Expect rough edges on first run.
