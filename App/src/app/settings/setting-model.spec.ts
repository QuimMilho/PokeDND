import { filterGroups, groupSettings, matches, parseInput, sameValue, showValue, toInputText, tokens } from './setting-model';
import { Setting } from './settings-api';

function make(key: string, group: string, type: Setting['type'], extra: Partial<Setting> = {}): Setting {
  return { key, group, type, source: 'default', overridden: false, chapter: 1, description: `About ${key}.`, ...extra };
}

describe('Setting model', () => {
  const list = [make('a.one', 'First', 'bool'), make('b.two', 'Second', 'int'), make('a.three', 'First', 'string', { overridden: true })];

  it('cuts the list into groups and keeps the order', () => {
    const groups = groupSettings(list);
    expect(groups.map((g) => g.name)).toEqual(['First', 'Second']);
    expect(groups[0].settings.map((s) => s.key)).toEqual(['a.one', 'a.three']);
  });

  it('matches every word of the search against key, description and group, without regard to capitals', () => {
    expect(matches(list[0], { text: '', changedOnly: false })).toBeTrue();
    expect(matches(list[0], { text: 'A.ONE', changedOnly: false })).toBeTrue();
    expect(matches(list[0], { text: 'about first', changedOnly: false })).toBeTrue();
    expect(matches(list[0], { text: 'about second', changedOnly: false })).toBeFalse();
  });

  it('drops groups with no match, and can keep only the changed settings', () => {
    expect(filterGroups(groupSettings(list), { text: 'two', changedOnly: false }).map((g) => g.name)).toEqual(['Second']);
    const changed = filterGroups(groupSettings(list), { text: '', changedOnly: true });
    expect(changed.map((g) => g.settings.map((s) => s.key))).toEqual([['a.three']]);
  });

  it('shows values as text', () => {
    expect(showValue(true)).toBe('true');
    expect(showValue('easy')).toBe('easy');
    expect(showValue([1, 2])).toBe('[1,2]');
    expect(showValue(undefined)).toBe('—');
    expect(showValue(null)).toBe('—');
  });

  it('splits the tokens of a list', () => {
    expect(tokens('a, b,c')).toEqual(['a', 'b', 'c']);
    expect(tokens('')).toEqual([]);
    expect(tokens(undefined)).toEqual([]);
  });

  describe('reading what was typed', () => {
    const int = make('n', 'G', 'int', { min: 1, max: 10 });

    it('reads whole numbers inside the limits', () => {
      expect(parseInput(int, ' 5 ')).toEqual({ ok: true, value: 5 });
      expect(parseInput(int, '0')).toEqual({ ok: false, error: 'The smallest value is 1.' });
      expect(parseInput(int, '11')).toEqual({ ok: false, error: 'The largest value is 10.' });
      expect(parseInput(int, '2.5')).toEqual({ ok: false, error: 'Enter a whole number.' });
      expect(parseInput(int, '')).toEqual({ ok: false, error: 'Enter a whole number.' });
    });

    it('reads decimals', () => {
      const decimal = make('d', 'G', 'decimal', { min: 0, max: 1 });
      expect(parseInput(decimal, '0.25')).toEqual({ ok: true, value: 0.25 });
      expect(parseInput(decimal, 'x')).toEqual({ ok: false, error: 'Enter a number.' });
      expect(parseInput(decimal, '1.5')).toEqual({ ok: false, error: 'The largest value is 1.' });
    });

    it('reads lists of whole numbers', () => {
      const ints = make('l', 'G', 'intList');
      expect(parseInput(ints, '1, 2,3')).toEqual({ ok: true, value: [1, 2, 3] });
      expect(parseInput(ints, '')).toEqual({ ok: true, value: [] });
      expect(parseInput(ints, '1, b').ok).toBeFalse();
    });

    it('reads JSON for json and data settings', () => {
      expect(parseInput(make('j', 'G', 'json'), '{"a": 1}')).toEqual({ ok: true, value: { a: 1 } });
      expect(parseInput(make('j', 'G', 'data'), '"official"')).toEqual({ ok: true, value: 'official' });
      expect(parseInput(make('j', 'G', 'data'), '{').ok).toBeFalse();
    });

    it('sends text as it was typed', () => {
      expect(parseInput(make('s', 'G', 'string'), ' hello ')).toEqual({ ok: true, value: ' hello ' });
    });
  });

  it('writes a value as the text of its input box', () => {
    expect(toInputText(make('n', 'G', 'int', { value: 7 }))).toBe('7');
    expect(toInputText(make('l', 'G', 'intList', { value: [1, 2] }))).toBe('1, 2');
    expect(toInputText(make('j', 'G', 'json', { value: { a: 1 } }))).toBe('{\n  "a": 1\n}');
    expect(toInputText(make('d', 'G', 'data'))).toBe('null');
    expect(toInputText(make('s', 'G', 'string'))).toBe('');
  });

  it('compares values by meaning', () => {
    expect(sameValue([1, 2], [1, 2])).toBeTrue();
    expect(sameValue({ a: 1 }, { a: 1 })).toBeTrue();
    expect(sameValue(undefined, null)).toBeTrue();
    expect(sameValue(1, '1')).toBeFalse();
  });
});
