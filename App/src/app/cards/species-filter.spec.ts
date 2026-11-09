import { EMPTY_FILTER, SpeciesFilter, isFiltered, matches, normaliseQuery, selectCards, toParams, toggled, withValue } from './species-filter';
import { SpeciesSummary, artUrl, cardKey, cardsOf, typeColour } from './species';

function species(dex: number, id: string, types: string[], generation: number, forms: Partial<SpeciesSummary['forms'][number]>[] = []): SpeciesSummary {
  return {
    dex,
    id,
    name: id.charAt(0).toUpperCase() + id.slice(1),
    generation,
    types,
    baseStats: { hp: 50 },
    playable: true,
    forms: [{ id: `${id}_base`, slug: 'base', kind: 'BASE', playable: true }, ...forms].map((f) => ({ playable: true, ...f }) as SpeciesSummary['forms'][number]),
  };
}

const BULBASAUR = species(1, 'bulbasaur', ['grass', 'poison'], 1, [{ id: 'bulbasaur_female', slug: 'female', kind: 'NAMED' }]);
const CHARMANDER = species(4, 'charmander', ['fire'], 1);
const MAROWAK = species(105, 'marowak', ['ground'], 1, [{ id: 'marowak_alolan', slug: 'alolan', kind: 'REGIONAL' }]);
const PICHU = species(172, 'pichu', ['electric'], 2);
const ALL = [BULBASAUR, CHARMANDER, MAROWAK, PICHU];

const filter = (partial: Partial<SpeciesFilter>): SpeciesFilter => ({ ...EMPTY_FILTER, ...partial });

describe('search text', () => {
  it('ignores case, accents, a leading # and extra spaces', () => {
    expect(normaliseQuery('  #Pikachu ')).toBe('pikachu');
    expect(normaliseQuery('FLABÉBÉ')).toBe('flabebe');
    expect(normaliseQuery('mr.   mime')).toBe('mr. mime');
  });
});

describe('toggled', () => {
  it('adds a missing value and removes a present one, without changing the original', () => {
    const one = new Set([1]);
    expect([...toggled(one, 2)]).toEqual([1, 2]);
    expect([...toggled(one, 1)]).toEqual([]);
    expect([...one]).toEqual([1]);
  });
});

describe('withValue', () => {
  it('sets the value on or off, whatever the set held before', () => {
    expect([...withValue(new Set([1]), 2, true)]).toEqual([1, 2]);
    expect([...withValue(new Set([1, 2]), 2, true)]).toEqual([1, 2]);
    expect([...withValue(new Set([1, 2]), 2, false)]).toEqual([1]);
    expect([...withValue(new Set([1]), 2, false)]).toEqual([1]);
  });
});

describe('query parameters for the species list', () => {
  it('always asks for playable species only', () => {
    expect(toParams(EMPTY_FILTER)).toEqual({ playable: 'true' });
  });

  it('sends the generations in order, the types sorted, and the trimmed search', () => {
    const params = toParams(filter({ generations: new Set([3, 1]), types: new Set(['fire', 'bug']), query: ' #char ' }), { limit: 60 });
    expect(params).toEqual({ playable: 'true', generation: ['1', '3'], type: ['bug', 'fire'], q: 'char', limit: '60' });
  });

  it('sends primaryTypeOnly only when a type is chosen', () => {
    expect(toParams(filter({ primaryTypeOnly: true }))['primaryTypeOnly']).toBeUndefined();
    expect(toParams(filter({ types: new Set(['fire']), primaryTypeOnly: true }))['primaryTypeOnly']).toBe('true');
  });

  it('adds the cursor of the next page', () => {
    expect(toParams(EMPTY_FILTER, { cursor: '60:growlithe' })['cursor']).toBe('60:growlithe');
  });

  it('knows whether anything is filtered', () => {
    expect(isFiltered(EMPTY_FILTER)).toBeFalse();
    expect(isFiltered(filter({ query: '   ' }))).toBeFalse();
    expect(isFiltered(filter({ generations: new Set([1]) }))).toBeTrue();
    expect(isFiltered(filter({ query: 'a' }))).toBeTrue();
  });
});

describe('matching a species (same rule as the API)', () => {
  it('lets everything pass when nothing is chosen', () => {
    expect(ALL.every((s) => matches(s, EMPTY_FILTER))).toBeTrue();
  });

  it('filters by generation', () => {
    expect(ALL.filter((s) => matches(s, filter({ generations: new Set([2]) }))).map((s) => s.id)).toEqual(['pichu']);
    expect(ALL.filter((s) => matches(s, filter({ generations: new Set([1, 2]) }))).length).toBe(4);
  });

  it('matches either type by default, or only the first one', () => {
    const poison = filter({ types: new Set(['poison']) });
    expect(matches(BULBASAUR, poison)).toBeTrue();
    expect(matches(BULBASAUR, { ...poison, primaryTypeOnly: true })).toBeFalse();
    expect(matches(BULBASAUR, filter({ types: new Set(['grass']), primaryTypeOnly: true }))).toBeTrue();
  });

  it('needs both the generation and the type', () => {
    const f = filter({ generations: new Set([2]), types: new Set(['fire']) });
    expect(ALL.some((s) => matches(s, f))).toBeFalse();
  });

  it('searches part of the name, the id, or the Dex number', () => {
    expect(ALL.filter((s) => matches(s, filter({ query: 'mar' }))).map((s) => s.id)).toEqual(['marowak']);
    expect(ALL.filter((s) => matches(s, filter({ query: 'CHAR' }))).map((s) => s.id)).toEqual(['charmander']);
    expect(ALL.filter((s) => matches(s, filter({ query: '#172' }))).map((s) => s.id)).toEqual(['pichu']);
    expect(ALL.filter((s) => matches(s, filter({ query: 'zzz' })))).toEqual([]);
  });
});

describe('cards of a species', () => {
  it('uses the base form and leaves out the cosmetic female variant', () => {
    expect(cardsOf(BULBASAUR).map((c) => c.key)).toEqual(['0001_bulbasaur']);
  });

  it('adds alternate forms, with the form id in the key, unless forms are switched off', () => {
    expect(cardsOf(MAROWAK).map((c) => c.key)).toEqual(['0105_marowak', '0105_marowak_alolan']);
    expect(cardsOf(MAROWAK, false).map((c) => c.key)).toEqual(['0105_marowak']);
  });

  it('leaves out forms that are not playable', () => {
    const odd = species(9, 'odd', ['normal'], 1, [{ id: 'odd_x', slug: 'x', kind: 'NAMED', playable: false }]);
    expect(cardsOf(odd).map((c) => c.key)).toEqual(['0009_odd']);
  });

  it('writes the key and the art address the way the API does', () => {
    expect(cardKey(MAROWAK, MAROWAK.forms[1])).toBe('0105_marowak_alolan');
    expect(artUrl(MAROWAK, MAROWAK.forms[1])).toBe('/api/v1/art/0105_marowak/marowak_alolan.png');
    expect(artUrl(MAROWAK, MAROWAK.forms[0])).toBe('/api/v1/art/0105_marowak/marowak.png');
    expect(artUrl(CHARMANDER, undefined, true)).toBe('/api/v1/art/0004_charmander/charmander.png?size=icon');
  });
});

describe('the cards an export gives', () => {
  const keys = (cards: ReturnType<typeof selectCards>) => cards.map((c) => c.key);
  const none = new Set<string>();

  it('is empty when nothing is chosen', () => {
    expect(selectCards(ALL, EMPTY_FILTER, true, none)).toEqual([]);
  });

  it('takes a whole generation, with forms', () => {
    expect(keys(selectCards(ALL, filter({ generations: new Set([1]) }), true, none))).toEqual(['0001_bulbasaur', '0004_charmander', '0105_marowak', '0105_marowak_alolan']);
  });

  it('takes only base forms when forms are switched off', () => {
    expect(keys(selectCards(ALL, filter({ generations: new Set([1]) }), false, none))).toEqual(['0001_bulbasaur', '0004_charmander', '0105_marowak']);
  });

  it('needs the generation and the type together', () => {
    expect(keys(selectCards(ALL, filter({ generations: new Set([1]), types: new Set(['fire']) }), true, none))).toEqual(['0004_charmander']);
  });

  it('adds cards named by hand on top, once, even a form while forms are off', () => {
    const byHand = new Set(['0105_marowak_alolan', '0004_charmander', '0172_pichu']);
    expect(keys(selectCards(ALL, filter({ generations: new Set([1]), types: new Set(['fire']) }), false, byHand))).toEqual([
      '0004_charmander',
      '0105_marowak_alolan',
      '0172_pichu',
    ]);
  });

  it('works with the hand-picked cards alone', () => {
    expect(keys(selectCards(ALL, EMPTY_FILTER, true, new Set(['0172_pichu'])))).toEqual(['0172_pichu']);
  });
});

describe('type colours', () => {
  it('has the colour of chapter 9.7 for each type and a grey for anything else', () => {
    expect(typeColour('fire')).toBe('#EE8130');
    expect(typeColour('Water')).toBe('#6390F0');
    expect(typeColour('nonsense')).toBe('#888888');
    expect(typeColour(undefined)).toBe('#888888');
  });
});
