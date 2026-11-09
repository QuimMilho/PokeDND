import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { flushUi, press, sleep } from '../../testing/ui';
import { CardBrowser } from './card-browser';
import { SpeciesPage, SpeciesSummary } from './species';

function summary(dex: number, id: string, types: string[], generation = 1): SpeciesSummary {
  return {
    dex,
    id,
    name: id.charAt(0).toUpperCase() + id.slice(1),
    generation,
    types,
    baseStats: { hp: 45 },
    playable: true,
    forms: [{ id: `${id}_base`, slug: 'base', kind: 'BASE', playable: true }],
  };
}

const page = (items: SpeciesSummary[], next: string | null = null): SpeciesPage => ({ items, next });

describe('CardBrowser', () => {
  let fixture: ComponentFixture<CardBrowser>;
  let http: HttpTestingController;
  let root: HTMLElement;

  const species = (): TestRequest => http.expectOne((r) => r.url === '/api/v1/data/species');
  const tiles = () => Array.from(root.querySelectorAll('a[data-testid^="species-"]')).map((a) => a.getAttribute('data-testid'));
  const settle = () => flushUi(fixture);

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [CardBrowser], providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(CardBrowser);
    root = fixture.nativeElement;
  });

  afterEach(() => http.verify());

  it('loads the first page of playable species and shows art, name, Dex number and types', async () => {
    await settle();
    const first = species();
    expect(first.request.params.get('playable')).toBe('true');
    expect(first.request.params.get('limit')).toBe('60');
    expect(first.request.params.has('generation')).toBeFalse();
    first.flush(page([summary(1, 'bulbasaur', ['grass', 'poison']), summary(4, 'charmander', ['fire'])]));
    await settle();
    expect(tiles()).toEqual(['species-bulbasaur', 'species-charmander']);
    const tile = root.querySelector('[data-testid="species-bulbasaur"]')!;
    expect(tile.textContent).toContain('#1');
    expect(tile.textContent).toContain('Bulbasaur');
    expect(tile.textContent).toContain('Grass');
    expect(tile.textContent).toContain('Poison');
    expect(tile.querySelector('img')?.getAttribute('src')).toBe('/api/v1/art/0001_bulbasaur/bulbasaur.png?size=icon');
    expect(tile.getAttribute('href')).toBe('/cards/bulbasaur');
  });

  it('filters by generation and by type: each change loads the list again', async () => {
    await settle();
    species().flush(page([summary(1, 'bulbasaur', ['grass'])]));
    await settle();

    press(root, 'generation-2');
    await settle();
    const byGeneration = species();
    expect(byGeneration.request.params.getAll('generation')).toEqual(['2']);
    byGeneration.flush(page([summary(172, 'pichu', ['electric'], 2)]));
    await settle();
    expect(tiles()).toEqual(['species-pichu']);

    press(root, 'type-electric');
    await settle();
    const byType = species();
    expect(byType.request.params.getAll('generation')).toEqual(['2']);
    expect(byType.request.params.getAll('type')).toEqual(['electric']);
    byType.flush(page([summary(172, 'pichu', ['electric'], 2)]));
    await settle();
    expect(tiles()).toEqual(['species-pichu']);
  });

  it('searches by name a moment after the person stops typing', async () => {
    await settle();
    species().flush(page([summary(1, 'bulbasaur', ['grass'])]));
    await settle();

    const input = root.querySelector<HTMLInputElement>('[data-testid="search"]')!;
    input.value = 'Char';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    await sleep(100);
    http.expectNone((r) => r.url === '/api/v1/data/species'); // still typing
    await sleep(250);
    await settle();
    const search = species();
    expect(search.request.params.get('q')).toBe('char');
    search.flush(page([summary(4, 'charmander', ['fire'])]));
    await settle();
    expect(tiles()).toEqual(['species-charmander']);
  });

  it('shows a message when nothing matches', async () => {
    await settle();
    species().flush(page([]));
    await settle();
    expect(root.querySelector('[data-testid="empty"]')?.textContent).toContain('No Pokémon match these filters');
    expect(tiles()).toEqual([]);
  });

  it('shows more with the cursor of the next page, and the clear button starts again', async () => {
    await settle();
    species().flush(page([summary(1, 'bulbasaur', ['grass'])], '1:bulbasaur'));
    await settle();
    press(root, 'more');
    await settle();
    const more = species();
    expect(more.request.params.get('cursor')).toBe('1:bulbasaur');
    more.flush(page([summary(2, 'ivysaur', ['grass'])]));
    await settle();
    expect(tiles()).toEqual(['species-bulbasaur', 'species-ivysaur']);
    expect(root.querySelector('[data-testid="more"]')).toBeNull();

    press(root, 'generation-1');
    await settle();
    species().flush(page([summary(1, 'bulbasaur', ['grass'])]));
    await settle();
    press(root, 'clear');
    await settle();
    const cleared = species();
    expect(cleared.request.params.has('generation')).toBeFalse();
    cleared.flush(page([summary(1, 'bulbasaur', ['grass'])]));
  });

  it('says what went wrong when the list cannot be loaded', async () => {
    await settle();
    species().flush({ code: 'REFERENCE_DATA_MISSING', message: 'x' }, { status: 503, statusText: 'Service Unavailable' });
    await settle();
    expect(root.querySelector('[data-testid="failure"]')?.textContent).toContain('card data is not loaded');
    expect(root.querySelector('[data-testid="empty"]')).toBeNull();
  });
});
