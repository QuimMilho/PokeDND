import enXlf from '../locale/messages.xlf';
import ptXlf from '../locale/messages.pt.xlf';
import { compareCatalogs } from '../testing/catalog';

/** Takes one `<unit>` out of a catalogue, the way a forgotten translation looks. */
function without(xlf: string, id: string): string {
  const unit = new RegExp(`\\s*<unit id="${id}"[^>]*>[\\s\\S]*?</unit>`);
  expect(unit.test(xlf)).withContext(`the catalogue has a unit "${id}"`).toBeTrue();
  return xlf.replace(unit, '');
}

describe('Translations (English and Portuguese)', () => {
  it('every text has a Portuguese translation, and no Portuguese text is left over', () => {
    const d = compareCatalogs(enXlf, ptXlf);
    expect(d.missingInPortuguese).withContext('in English but not in Portuguese').toEqual([]);
    expect(d.missingInEnglish).withContext('in Portuguese but not in English').toEqual([]);
    expect(d.untranslated).withContext('with an empty Portuguese text').toEqual([]);
    expect(d.placeholderMismatch).withContext('with different placeholders').toEqual([]);
  });

  it('the catalogues are not empty (the test would pass for nothing otherwise)', () => {
    expect(new DOMParser().parseFromString(enXlf, 'application/xml').getElementsByTagName('unit').length).toBeGreaterThan(100);
  });

  it('fails when a key is removed from the Portuguese copy', () => {
    const d = compareCatalogs(enXlf, without(ptXlf, 'navGames'));
    expect(d.missingInPortuguese).toEqual(['navGames']);
    expect(d.missingInEnglish).toEqual([]);
  });

  it('fails when a key is removed from the English copy', () => {
    const d = compareCatalogs(without(enXlf, 'navGames'), ptXlf);
    expect(d.missingInEnglish).toEqual(['navGames']);
    expect(d.missingInPortuguese).toEqual([]);
  });

  it('fails when a Portuguese text is empty or loses a placeholder', () => {
    const empty = ptXlf.replace(/(<unit id="navGames">[\s\S]*?<target>)[^<]*(<\/target>)/, '$1$2');
    expect(compareCatalogs(enXlf, empty).untranslated).toEqual(['navGames']);

    // the first message of the catalogue that has a placeholder: take the placeholder out of its Portuguese text
    const withPlaceholder = /<unit id="(\w+)">\s*<segment>\s*<source>[^<]*<ph [^>]*equiv="(\w+)"/.exec(ptXlf);
    expect(withPlaceholder).withContext('some message has a placeholder').not.toBeNull();
    const [, id, name] = withPlaceholder!;
    const broken = ptXlf.replace(new RegExp(`(<unit id="${id}">[\\s\\S]*?<target>[^]*?)<ph [^>]*equiv="${name}"[^>]*/>`), '$1');
    expect(broken).not.toBe(ptXlf);
    expect(compareCatalogs(enXlf, broken).placeholderMismatch).toContain(id);
  });
});
