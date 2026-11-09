import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { flushUi, press, sleep } from '../../testing/ui';
import { CardExport } from './export';
import { EXPORT_POLL_MS, ExportJob } from './export-api';
import { SpeciesSummary } from './species';

function summary(dex: number, id: string, types: string[], generation: number, extra: SpeciesSummary['forms'] = []): SpeciesSummary {
  return {
    dex,
    id,
    name: id.charAt(0).toUpperCase() + id.slice(1),
    generation,
    types,
    baseStats: { hp: 45 },
    playable: true,
    forms: [{ id: `${id}_base`, slug: 'base', kind: 'BASE', playable: true }, ...extra],
  };
}

const SPECIES = [
  summary(1, 'bulbasaur', ['grass', 'poison'], 1),
  summary(4, 'charmander', ['fire'], 1),
  summary(105, 'marowak', ['ground'], 1, [{ id: 'marowak_alolan', slug: 'alolan', kind: 'REGIONAL', playable: true }]),
  summary(172, 'pichu', ['electric'], 2),
];

const job = (state: ExportJob['state'], progress: number, extra: Partial<ExportJob> = {}): ExportJob => ({
  id: '11111111-2222-3333-4444-555555555555',
  state,
  progress,
  total: 4,
  message: null,
  warnings: [],
  pages: null,
  createdAt: '2026-11-08T12:00:00Z',
  expiresAt: null,
  downloadUrl: null,
  manifestUrl: null,
  request: null,
  options: null,
  ...extra,
});

describe('CardExport', () => {
  let fixture: ComponentFixture<CardExport>;
  let http: HttpTestingController;
  let root: HTMLElement;

  const settle = () => flushUi(fixture);
  const click = async (testId: string) => {
    press(root, testId);
    await settle();
  };
  const startButton = () => root.querySelector<HTMLButtonElement>('[data-testid="start"]')!;

  async function create(query: Record<string, string[]> = {}) {
    TestBed.configureTestingModule({
      imports: [CardExport],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: EXPORT_POLL_MS, useValue: 30 },
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap(query) } } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(CardExport);
    root = fixture.nativeElement;
    await settle();
    http.expectOne((r) => r.url === '/api/v1/data/species').flush({ items: SPECIES, next: null });
    await settle();
  }

  /** The newest look at the job that is still waiting for an answer (an older one is dropped when the next poll starts). */
  const pendingPoll = () => {
    const waiting = http.match('/api/v1/cards/exports/11111111-2222-3333-4444-555555555555').filter((r) => !r.cancelled);
    expect(waiting.length).toBeGreaterThan(0);
    return waiting[waiting.length - 1];
  };

  afterEach(() => http.verify());

  it('cannot start before something is chosen', async () => {
    await create();
    expect(startButton().disabled).toBeTrue();
    expect(root.querySelector('[data-testid="summary"]')?.textContent).toContain('No cards chosen yet.');
  });

  it('counts the cards of a generation (forms included) and the printed cards with copies', async () => {
    await create();
    await click('generation-1');
    expect(root.querySelector('[data-testid="summary"]')?.textContent).toMatch(/4 cards/);
    expect(root.querySelector('[data-testid="summary"]')?.textContent).toContain('4 printed');
    await click('include-forms');
    expect(root.querySelector('[data-testid="summary"]')?.textContent).toMatch(/3 cards/);
    const copies = root.querySelector<HTMLInputElement>('[data-testid="copies"]')!;
    copies.value = '3';
    copies.dispatchEvent(new Event('input'));
    await settle();
    expect(root.querySelector('[data-testid="summary"]')?.textContent).toContain('9 printed');
    expect(startButton().disabled).toBeFalse();
  });

  it('refuses a selection above the limit of one export', async () => {
    await create();
    await click('generation-1');
    const copies = root.querySelector<HTMLInputElement>('[data-testid="copies"]')!;
    copies.value = '400'; // 4 cards x 400 copies = 1,600 printed cards, the limit is 1,500
    copies.dispatchEvent(new Event('input'));
    await settle();
    expect(startButton().disabled).toBeTrue();
    expect(root.textContent).toContain('more than the 1500 cards one export may have');
    copies.value = '300'; // 1,200: fine
    copies.dispatchEvent(new Event('input'));
    await settle();
    expect(startButton().disabled).toBeFalse();
  });

  it('adds a card by hand from the search and removes it again', async () => {
    await create();
    const search = root.querySelector<HTMLInputElement>('[data-testid="hand-search"]')!;
    search.value = 'maro';
    search.dispatchEvent(new Event('input'));
    await settle();
    await click('add-0105_marowak_alolan');
    expect(root.querySelector('[data-testid="hand-chosen"]')?.textContent).toContain('Marowak (alolan)');
    expect(root.querySelector('[data-testid="summary"]')?.textContent).toMatch(/1 card/);
    (root.querySelector('[data-testid="hand-chosen"] button[matChipRemove]') as HTMLElement).click();
    await settle();
    expect(root.querySelector('[data-testid="hand-chosen"]')).toBeNull();
  });

  it('starts with the card from "Export this card"', async () => {
    await create({ card: ['0004_charmander'] });
    expect(root.querySelector('[data-testid="summary"]')?.textContent).toMatch(/1 card/);
  });

  it('sends the chosen options, shows the progress and offers the PDF when it is done', async () => {
    await create();
    await click('generation-1');
    await click('type-fire');
    await click('backs');
    startButton().click();
    await settle();

    const post = http.expectOne('/api/v1/cards/exports');
    expect(post.request.method).toBe('POST');
    expect(post.request.body).toEqual({
      includeForms: true,
      backs: true,
      size: '63x88',
      paper: 'A4',
      language: 'en',
      colour: 'full',
      copies: 1,
      bleedMm: 3,
      gapMm: 2,
      generations: [1],
      types: ['fire'],
      primaryTypeOnly: false,
    });
    post.flush(job('queued', 0));
    await settle();

    // the first look at the job comes at once, the next ones every second
    await sleep(40);
    pendingPoll().flush(job('running', 2));
    await settle();
    expect(root.querySelector('[data-testid="job-progress"]')?.textContent).toContain('2 of 4 cards');
    expect(root.querySelector('[data-testid="download"]')).toBeNull();

    await sleep(60);
    pendingPoll().flush(
      job('done', 4, { pages: 3, downloadUrl: '/api/v1/cards/exports/11111111-2222-3333-4444-555555555555/download' }),
    );
    await settle();
    expect(root.querySelector('[data-testid="job-done"]')?.textContent).toContain('3 pages');
    expect(root.querySelector('[data-testid="download"]')).not.toBeNull();
    await sleep(120); // the polling has stopped: no further request (verify() would fail)
  });

  it('sends a custom size and a different paper', async () => {
    await create({ card: ['0004_charmander'] });
    root.querySelectorAll<HTMLElement>('[data-testid="size"] button')[3].click(); // Custom
    await settle();
    const [width, height] = Array.from(root.querySelectorAll<HTMLInputElement>('input[type="number"]'));
    width.value = '50';
    width.dispatchEvent(new Event('input'));
    height.value = '70';
    height.dispatchEvent(new Event('input'));
    root.querySelectorAll<HTMLElement>('[data-testid="paper"] button')[1].click(); // US Letter
    await settle();
    startButton().click();
    await settle();
    const post = http.expectOne('/api/v1/cards/exports');
    expect(post.request.body.size).toBe('50x70');
    expect(post.request.body.paper).toBe('LETTER');
    expect(post.request.body.cards).toEqual(['0004_charmander']);
    post.flush({ code: 'INVALID_EXPORT_OPTIONS', message: 'x' }, { status: 422, statusText: 'Unprocessable' });
    await settle();
    expect(root.querySelector('[data-testid="failure"]')?.textContent).toContain('do not fit together');
  });

  it('shows a clear message when the selection is empty on the server', async () => {
    await create({ card: ['0004_charmander'] });
    startButton().click();
    await settle();
    http.expectOne('/api/v1/cards/exports').flush({ code: 'EMPTY_SELECTION', message: 'x' }, { status: 422, statusText: 'Unprocessable' });
    await settle();
    expect(root.querySelector('[data-testid="failure"]')?.textContent).toContain('No card matches this selection');
  });
});
