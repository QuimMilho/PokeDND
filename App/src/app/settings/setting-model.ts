import { Setting } from './settings-api';

/** The settings of one group of Appendix A, in the order of the registry. */
export interface Group {
  name: string;
  settings: Setting[];
}

/** Cuts the list into the groups of Appendix A. Groups and settings keep the order in which the API sent them. */
export function groupSettings(settings: readonly Setting[]): Group[] {
  const groups = new Map<string, Group>();
  for (const s of settings) {
    let group = groups.get(s.group);
    if (!group) {
      group = { name: s.group, settings: [] };
      groups.set(s.group, group);
    }
    group.settings.push(s);
  }
  return Array.from(groups.values());
}

/** What the search box and the "changed only" switch ask for. */
export interface Filter {
  text: string;
  changedOnly: boolean;
}

/** Does the setting match? Every word of the search must be in the key, the description or the name of the group (capitals do not matter). */
export function matches(setting: Setting, filter: Filter): boolean {
  if (filter.changedOnly && !setting.overridden) {
    return false;
  }
  const words = filter.text.toLowerCase().split(/\s+/).filter(Boolean);
  if (!words.length) {
    return true;
  }
  const haystack = `${setting.key} ${setting.description} ${setting.group}`.toLowerCase();
  return words.every((w) => haystack.includes(w));
}

export function filterGroups(groups: readonly Group[], filter: Filter): Group[] {
  return groups.map((g) => ({ name: g.name, settings: g.settings.filter((s) => matches(s, filter)) })).filter((g) => g.settings.length > 0);
}

/** The value as text, for the read-only places (the preview of a rule set, the "default" marker). */
export function showValue(value: unknown): string {
  if (value === undefined || value === null) {
    return '—';
  }
  return typeof value === 'string' ? value : JSON.stringify(value);
}

/** The tokens of a `csv` value: `"a,b"` is `["a", "b"]`. */
export function tokens(value: unknown): string[] {
  return typeof value === 'string' ? value.split(',').map((t) => t.trim()).filter(Boolean) : [];
}

/** The result of reading what the person typed: a value for the API, or the reason it cannot be one. */
export type Parsed = { ok: true; value: unknown } | { ok: false; error: string };

/**
 * Turns what the person typed into the value the API wants for this kind of setting. The API checks everything again; this only
 * catches what cannot even be sent (text where a number is wanted, broken JSON) and the limits that the registry states.
 */
export function parseInput(setting: Setting, raw: string): Parsed {
  const text = raw.trim();
  switch (setting.type) {
    case 'int':
    case 'decimal': {
      const n = Number(text);
      if (text === '' || !Number.isFinite(n) || (setting.type === 'int' && !Number.isInteger(n))) {
        return fail(setting.type === 'int' ? $localize`:@@errNotWhole:Enter a whole number.` : $localize`:@@errNotNumber:Enter a number.`);
      }
      if (setting.min !== undefined && n < setting.min) {
        return fail($localize`:@@errTooSmall:The smallest value is ${setting.min}:min:.`);
      }
      if (setting.max !== undefined && n > setting.max) {
        return fail($localize`:@@errTooBig:The largest value is ${setting.max}:max:.`);
      }
      return { ok: true, value: n };
    }
    case 'intList': {
      const parts = text === '' ? [] : text.split(',').map((p) => Number(p.trim()));
      if (parts.some((n) => !Number.isInteger(n))) {
        return fail($localize`:@@errNotWholeList:Enter whole numbers separated by commas.`);
      }
      return { ok: true, value: parts };
    }
    case 'json':
    case 'data':
      try {
        return { ok: true, value: JSON.parse(text) as unknown };
      } catch {
        return fail($localize`:@@errNotJson:This is not valid JSON.`);
      }
    default:
      return { ok: true, value: raw };
  }
}

/** A value as the text of its input box. */
export function toInputText(setting: Setting): string {
  const v = setting.value;
  if (v === undefined || v === null) {
    return setting.type === 'json' || setting.type === 'data' ? 'null' : '';
  }
  if (setting.type === 'intList' && Array.isArray(v)) {
    return v.join(', ');
  }
  if (setting.type === 'json' || setting.type === 'data') {
    return JSON.stringify(v, null, 2);
  }
  return String(v);
}

/** Do the two values mean the same? Used to avoid saving a setting that did not change. */
export function sameValue(a: unknown, b: unknown): boolean {
  return JSON.stringify(a ?? null) === JSON.stringify(b ?? null);
}

function fail(error: string): Parsed {
  return { ok: false, error };
}
