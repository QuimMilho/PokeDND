import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, expand, map, reduce, EMPTY } from 'rxjs';
import { API_BASE } from '../core/api-config';
import { SpeciesFilter, toParams } from './species-filter';
import { SpeciesPage, SpeciesSummary } from './species';

/** One move, as `GET /data/moves/{id}` gives it (only what the card shows). */
export interface MoveInfo {
  id: string;
  name: string;
  type: string;
  category?: string;
  power?: number | null;
  accuracy?: number | null;
  pp?: number | null;
  description?: string;
  effect?: unknown;
  [key: string]: unknown;
}

/** One species in full, as `GET /data/species/{id}` gives it (only what the card preview shows). */
export interface SpeciesDetail {
  id: string;
  name: string;
  dex: number;
  generation: number;
  types: string[];
  baseStats: Record<string, number>;
  abilities?: string[];
  hiddenAbility?: string | null;
  forms: {
    id: string;
    slug: string;
    kind: string;
    playable: boolean;
    text?: string;
    types?: string[];
    abilities?: string[];
    hiddenAbility?: string | null;
    weaknesses?: Record<string, number>;
  }[];
  derived?: {
    stage?: string;
    weaknesses?: Record<string, number>;
    resistances?: Record<string, number>;
    immunities?: string[];
    /** The four signature moves of each form (chapter 9.3), by form id. */
    signature?: Record<string, string[]>;
  };
  evolutions?: unknown[];
  [key: string]: unknown;
}

/** The reference data of the API (chapter 18.1), read-only. */
@Injectable({ providedIn: 'root' })
export class ReferenceApi {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_BASE);

  /** One page of species. Pass the `next` of the previous page to get the following one. */
  species(filter: SpeciesFilter, options: { limit?: number; cursor?: string | null } = {}): Observable<SpeciesPage> {
    return this.http.get<SpeciesPage>(`${this.base}/data/species`, { params: new HttpParams({ fromObject: toParams(filter, options) }) });
  }

  /** Every playable species that passes the filter, all pages in a row (the export screen picks cards from this list). */
  allSpecies(filter: SpeciesFilter): Observable<SpeciesSummary[]> {
    return this.species(filter, { limit: 500 }).pipe(
      expand((page) => (page.next ? this.species(filter, { limit: 500, cursor: page.next }) : EMPTY)),
      map((page) => page.items),
      reduce((all, items) => all.concat(items), [] as SpeciesSummary[]),
    );
  }

  oneSpecies(id: string): Observable<SpeciesDetail> {
    return this.http.get<SpeciesDetail>(`${this.base}/data/species/${encodeURIComponent(id)}`);
  }

  move(id: string): Observable<MoveInfo> {
    return this.http.get<MoveInfo>(`${this.base}/data/moves/${encodeURIComponent(id)}`);
  }
}
