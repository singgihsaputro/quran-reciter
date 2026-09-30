// Speech engines write modern spelling; these verses differ from the mushaf's
// and used to score below 100% even when read perfectly (see public/matcher.js).
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { test } from 'node:test'
import { match } from '../public/matcher.js'

const quran = JSON.parse(readFileSync(new URL('../public/quran.json', import.meta.url), 'utf8'))
const verse = (s, v) => quran.find(x => x.number === s).verses.find(x => x.number === v).arabic

test('modern spelling of a verse read perfectly scores 100%', () => {
  for (const [s, v, heard] of [
    [109, 1, 'قل يا أيها الكافرون'],                  // يَـٰٓأَيُّهَا is one word in the mushaf
    [79, 42, 'يسألونك عن الساعة أيان مرساها'],        // hamza seat
    [102, 8, 'ثم لتسألن يومئذ عن النعيم'],
    [104, 7, 'التي تطلع على الأفئدة'],
    [81, 8, 'وإذا الموءودة سئلت'],                    // doubled waw
    [84, 11, 'فسوف يدعو ثبورا'],                      // alif after a final waw
    [89, 23, 'وجيء يومئذ بجهنم يومئذ يتذكر الإنسان وأنى له الذكرى'], // silent letter
    [106, 2, 'إيلافهم رحلة الشتاء والصيف'],           // small ya written out
  ]) assert.equal(match(verse(s, v), heard).accuracy, 1, `${s}:${v}`)
})

test('different words still count as different', () => {
  assert.ok(match(verse(109, 1), 'قل يا أيها الكافر').accuracy < 1)
  assert.ok(match(verse(79, 42), 'يسألونك عن الساعة أين مرساها').accuracy < 1)
  assert.ok(match(verse(1, 4), 'ملك يوم الدين').accuracy === 1)  // both readings of مالك are valid qira'at
})
