import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { flushUi, nextRequest, press } from '../../testing/ui';
import { CardDetail } from './card-detail';
import { SpeciesDetail } from './reference-api';

const MAROWAK = {
  id: 'marowak',
  dex: 105,
  name: 'Marowak',
  generation: 1,
  types: ['ground'],
  baseStats: { hp: 60, attack: 80, defense: 110, specialAttack: 50, specialDefense: 80, speed: 45 },
  abilities: ['rockhead', 'lightningrod'],
  hiddenAbility: 'battlearmor',
  forms: [
    { id: 'marowak_base', kind: 'BASE', slug: 'base', text: '', types: ['ground'], playable: true, abilities: ['rockhead'], weaknesses: { ice: 2, water: 2 } },
    { id: 'marowak_alolan', kind: 'REGIONAL', slug: 'alolan', text: 'Alola', types: ['fire', 'ghost'], playable: true, abilities: ['cursedbody'], weaknesses: { dark: 2 } },
  ],
  derived: {
    stage: 'STAGE_1',
    signature: {
      marowak_base: ['stompingtantrum', 'retaliate', 'boneclub', 'headbutt'],
      marowak_alolan: ['shadowbone', 'flamewheel', 'hex', 'doubleedge'],
    },
  },
} as unknown as SpeciesDetail;

const move = (id: string, type: string, power: number) => ({ id, name: id.toUpperCase(), type, power });

describe('CardDetail', () => {
  let fixture: ComponentFixture<CardDetail>;
  let http: HttpTestingController;
  let root: HTMLElement;

  async function open(id: string) {
    TestBed.configureTestingModule({
      imports: [CardDetail],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ActivatedRoute, useValue: { paramMap: of(convertToParamMap({ id })) } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(CardDetail);
    root = fixture.nativeElement;
    await flushUi(fixture);
  }

  afterEach(() => http.verify());

  it('shows the card preview with the four signature moves of the first form', async () => {
    await open('marowak');
    (await nextRequest(http, '/api/v1/data/species/marowak')).flush(MAROWAK);
    await flushUi(fixture);
    for (const id of MAROWAK.derived!.signature!['marowak_base']) {
      (await nextRequest(http, `/api/v1/data/moves/${id}`)).flush(move(id, 'Ground', 60));
    }
    await flushUi(fixture);

    expect(root.querySelector('[data-testid="card-name"]')?.textContent).toBe('Marowak');
    expect(root.querySelectorAll('[data-testid="card-move"]').length).toBe(4);
    expect(root.querySelector('[data-testid="card-preview"]')?.textContent).toContain('STOMPINGTANTRUM');
    expect(root.querySelector('[data-testid="card-preview"]')?.textContent).toContain('HP');
    expect(root.querySelector('[data-testid="card-preview"] img')?.getAttribute('src')).toBe('/api/v1/art/0105_marowak/marowak.png');
    expect(root.querySelector('[data-testid="export-this"]')?.getAttribute('href')).toBe('/cards/export?card=0105_marowak');
  });

  it('switches to an alternate form: its name, its art and its own four moves', async () => {
    await open('marowak');
    (await nextRequest(http, '/api/v1/data/species/marowak')).flush(MAROWAK);
    await flushUi(fixture);
    for (const id of MAROWAK.derived!.signature!['marowak_base']) {
      (await nextRequest(http, `/api/v1/data/moves/${id}`)).flush(move(id, 'Ground', 60));
    }
    await flushUi(fixture);

    press(root, 'form-marowak_alolan');
    await flushUi(fixture);
    for (const id of MAROWAK.derived!.signature!['marowak_alolan']) {
      (await nextRequest(http, `/api/v1/data/moves/${id}`)).flush(move(id, 'Fire', 80));
    }
    await flushUi(fixture);

    expect(root.querySelector('[data-testid="card-name"]')?.textContent).toBe('Marowak (Alola)');
    expect(root.querySelector('[data-testid="card-preview"] img')?.getAttribute('src')).toBe('/api/v1/art/0105_marowak/marowak_alolan.png');
    expect(root.querySelector('[data-testid="card-preview"]')?.textContent).toContain('SHADOWBONE');
    expect(root.querySelector('[data-testid="export-this"]')?.getAttribute('href')).toBe('/cards/export?card=0105_marowak_alolan');
  });

  it('says so when the Pokémon does not exist', async () => {
    await open('missingno');
    (await nextRequest(http, '/api/v1/data/species/missingno')).flush({ code: 'NOT_FOUND', message: 'x' }, { status: 404, statusText: 'Not Found' });
    await flushUi(fixture);
    expect(root.querySelector('[data-testid="not-found"]')).not.toBeNull();
  });
});
