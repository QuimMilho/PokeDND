import { CardEntry, SpeciesSummary, cardsOf } from './species';

/** What the person chose on the card browser or the export screen. A part left empty does not restrict. */
export interface SpeciesFilter {
  generations: ReadonlySet<number>;
  types: ReadonlySet<string>;
  /** With types chosen: match only the first type (default is either type). */
  primaryTypeOnly: boolean;
  /** Part of a name. */
  query: string;
}

export const EMPTY_FILTER: SpeciesFilter = { generations: new Set(), types: new Set(), primaryTypeOnly: false, query: '' };

/** Adds the value to the set, or removes it when it is there. The set itself is not changed (signals compare by identity). */
export function toggled<T>(set: ReadonlySet<T>, value: T): Set<T> {
  const copy = new Set(set);
  if (!copy.delete(value)) copy.add(value);
  return copy;
}

/** A copy of the set with the value in it (`present`) or not in it. */
export function withValue<T>(set: ReadonlySet<T>, value: T, present: boolean): Set<T> {
  const copy = new Set(set);
  if (present) copy.add(value);
  else copy.delete(value);
  return copy;
}

/** The search text as it is compared: no surrounding spaces, no leading #, lower case, accents ignored. */
export function normaliseQuery(text: string): string {
  return text
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .toLowerCase()
    .trim()
    .replace(/^#/, '')
    .trim()
    .replace(/\s+/g, ' ');
}

export function isFiltered(filter: SpeciesFilter): boolean {
  return filter.generations.size > 0 || filter.types.size > 0 || normaliseQuery(filter.query) !== '';
}

/** The query string of `GET /data/species` for this filter (page size and cursor included). */
export function toParams(filter: SpeciesFilter, options: { limit?: number; cursor?: string | null } = {}): Record<string, string | string[]> {
  const params: Record<string, string | string[]> = { playable: 'true' };
  if (filter.generations.size) params['generation'] = [...filter.generations].sort((a, b) => a - b).map(String);
  if (filter.types.size) {
    params['type'] = [...filter.types].sort();
    if (filter.primaryTypeOnly) params['primaryTypeOnly'] = 'true';
  }
  const q = filter.query.trim().replace(/^#/, '').trim();
  if (q) params['q'] = q;
  if (options.limit) params['limit'] = String(options.limit);
  if (options.cursor) params['cursor'] = options.cursor;
  return params;
}

/**
 * Does the species pass the filter? The same rule as the API (chapter 9.6): a part left empty does not restrict; the search matches
 * part of the name, or the Dex number. Used where the whole list is in memory (the export screen's card picker).
 */
export function matches(species: SpeciesSummary, filter: SpeciesFilter): boolean {
  if (filter.generations.size && !filter.generations.has(species.generation)) return false;
  if (filter.types.size) {
    const own = filter.primaryTypeOnly ? species.types.slice(0, 1) : species.types;
    if (!own.some((t) => filter.types.has(t.toLowerCase()))) return false;
  }
  const q = normaliseQuery(filter.query);
  if (q) {
    const name = normaliseQuery(species.name);
    const dex = String(species.dex);
    if (!name.includes(q) && !species.id.includes(q) && !(/^\d+$/.test(q) && dex === q.replace(/^0+/, ''))) return false;
  }
  return true;
}

/** The cards a generation/type selection gives, plus the ones named by hand, each only once. */
export function selectCards(
  all: readonly SpeciesSummary[],
  filter: Pick<SpeciesFilter, 'generations' | 'types' | 'primaryTypeOnly'>,
  includeForms: boolean,
  byHand: ReadonlySet<string>,
): CardEntry[] {
  const filtered = filter.generations.size > 0 || filter.types.size > 0;
  const seen = new Set<string>();
  const out: CardEntry[] = [];
  for (const species of all) {
    // every card the species has: a card named by hand counts even when forms are switched off for the filters
    for (const card of cardsOf(species, true)) {
      const byFilter = filtered && (includeForms || card.base) && matches(species, { ...filter, query: '' });
      const wanted = byFilter || byHand.has(card.key);
      if (wanted && !seen.has(card.key)) {
        seen.add(card.key);
        out.push(card);
      }
    }
  }
  return out;
}
