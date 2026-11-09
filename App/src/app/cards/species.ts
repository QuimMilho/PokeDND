/** The 18 types with the card colours of chapter 9.7 (the same table the PDF cards use). */
export const TYPE_COLOURS: Readonly<Record<string, string>> = {
  normal: '#A8A77A',
  fire: '#EE8130',
  water: '#6390F0',
  electric: '#F7D02C',
  grass: '#7AC74C',
  ice: '#96D9D6',
  fighting: '#C22E28',
  poison: '#A33EA1',
  ground: '#E2BF65',
  flying: '#A98FF3',
  psychic: '#F95587',
  bug: '#A6B91A',
  rock: '#B6A136',
  ghost: '#735797',
  dragon: '#6F35FC',
  dark: '#705746',
  steel: '#B7B7CE',
  fairy: '#D685AD',
};

/** All types, in the order of the table above. */
export const TYPES: readonly string[] = Object.keys(TYPE_COLOURS);

/** The generations of chapter 9.6 and where they start and end in the National Dex. */
export const GENERATIONS: readonly { number: number; from: number; to: number }[] = [
  { number: 1, from: 1, to: 151 },
  { number: 2, from: 152, to: 251 },
  { number: 3, from: 252, to: 386 },
  { number: 4, from: 387, to: 493 },
  { number: 5, from: 494, to: 649 },
  { number: 6, from: 650, to: 721 },
  { number: 7, from: 722, to: 809 },
  { number: 8, from: 810, to: 905 },
  { number: 9, from: 906, to: 1025 },
];

export function typeColour(type: string | undefined): string {
  return (type && TYPE_COLOURS[type.toLowerCase()]) || '#888888';
}

/** The type's name in the language of this build. */
export function typeName(type: string): string {
  switch (type.toLowerCase()) {
    case 'normal':
      return $localize`:@@typeNormal:Normal`;
    case 'fire':
      return $localize`:@@typeFire:Fire`;
    case 'water':
      return $localize`:@@typeWater:Water`;
    case 'electric':
      return $localize`:@@typeElectric:Electric`;
    case 'grass':
      return $localize`:@@typeGrass:Grass`;
    case 'ice':
      return $localize`:@@typeIce:Ice`;
    case 'fighting':
      return $localize`:@@typeFighting:Fighting`;
    case 'poison':
      return $localize`:@@typePoison:Poison`;
    case 'ground':
      return $localize`:@@typeGround:Ground`;
    case 'flying':
      return $localize`:@@typeFlying:Flying`;
    case 'psychic':
      return $localize`:@@typePsychic:Psychic`;
    case 'bug':
      return $localize`:@@typeBug:Bug`;
    case 'rock':
      return $localize`:@@typeRock:Rock`;
    case 'ghost':
      return $localize`:@@typeGhost:Ghost`;
    case 'dragon':
      return $localize`:@@typeDragon:Dragon`;
    case 'dark':
      return $localize`:@@typeDark:Dark`;
    case 'steel':
      return $localize`:@@typeSteel:Steel`;
    case 'fairy':
      return $localize`:@@typeFairy:Fairy`;
    default:
      return type;
  }
}

export interface FormSummary {
  id: string;
  slug: string;
  kind: string;
  playable: boolean;
}

/** One line of `GET /data/species` (the API's short version of a species). */
export interface SpeciesSummary {
  dex: number;
  id: string;
  name: string;
  generation: number;
  types: string[];
  baseStats: Record<string, number>;
  playable: boolean;
  stage?: string;
  forms: FormSummary[];
}

export interface SpeciesPage {
  items: SpeciesSummary[];
  next: string | null;
}

/** The parts of a species that decide its cards (a summary and the full species both have them). */
export type SpeciesLike = Pick<SpeciesSummary, 'dex' | 'id' | 'forms'>;

/** A card that can be printed: a species and one of its forms. */
export interface CardEntry {
  /** The card key, for example `0006_charizard` or `0105_marowak_alolan` (the same as in the PDF's manifest). */
  key: string;
  species: SpeciesLike;
  form: FormSummary;
  base: boolean;
}

function isBase(form: FormSummary): boolean {
  return form.kind === 'BASE' || form.id.endsWith('_base');
}

/** The key of a card: the Dex number on four digits, then the species id (base form) or the form id (any other form). */
export function cardKey(species: SpeciesLike, form: FormSummary): string {
  return `${String(species.dex).padStart(4, '0')}_${isBase(form) ? species.id : form.id}`;
}

/**
 * Which cards a species has: every playable form except the purely cosmetic female variants (same rule as the API, chapter 9.6).
 * With `includeForms` off only the base form is a card.
 */
export function cardsOf(species: SpeciesLike, includeForms = true): CardEntry[] {
  const out: CardEntry[] = [];
  for (const form of species.forms) {
    if (!form.playable) continue;
    const base = isBase(form);
    const cosmetic = (form.slug === 'female' && form.kind === 'NAMED') || form.slug === 'hisuian-female';
    if (!base && (!includeForms || cosmetic)) continue;
    out.push({ key: cardKey(species, form), species, form, base });
  }
  return out;
}

/** The address of the card art of a card (or the small icon for lists). */
export function artUrl(species: SpeciesLike, form: FormSummary | undefined, icon = false): string {
  const folder = `${String(species.dex).padStart(4, '0')}_${species.id}`;
  const file = form && !isBase(form) ? form.id : species.id;
  return `/api/v1/art/${folder}/${file}.png${icon ? '?size=icon' : ''}`;
}
