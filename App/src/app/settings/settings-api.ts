import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { API_BASE } from '../core/api-config';

export type SettingType = 'bool' | 'int' | 'decimal' | 'enum' | 'string' | 'csv' | 'intList' | 'json' | 'data';

/** Where the value of a setting comes from (chapter 12.2): nothing changed it, the rule set did, or the game's own change did. */
export type SettingSource = 'default' | 'ruleSet' | 'game';

/** A setting with its value in this game (`SettingValueDto`). The API leaves out fields that are null. */
export interface Setting {
  key: string;
  group: string;
  type: SettingType;
  value?: unknown;
  defaultValue?: unknown;
  source: SettingSource;
  overridden: boolean;
  chapter: number;
  description: string;
  allowed?: string[];
  min?: number;
  max?: number;
}

export interface RuleSetRef {
  id: string;
  name: string;
}

export interface GameSettings {
  gameId: string;
  ruleSet: RuleSetRef;
  settings: Setting[];
}

/** One line of the preview of a rule set: what would change (`ChangeDto`). */
export interface Change {
  key: string;
  from?: unknown;
  to?: unknown;
  fromSource: string;
  toSource: string;
}

export interface Applied {
  ruleSet: RuleSetRef;
  applied: boolean;
  changes: Change[];
}

export interface Preset {
  id: string;
  name: string;
  description: string;
  settings: Record<string, unknown>;
}

export interface Presets {
  enabled: boolean;
  presets: Preset[];
}

/** The settings calls of chapter 18.1 ("Games and settings"). */
@Injectable({ providedIn: 'root' })
export class SettingsApi {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_BASE);

  get(gameId: string): Observable<GameSettings> {
    return this.http.get<GameSettings>(`${this.base}/games/${gameId}/settings`);
  }

  /** Sets the game's own value of some settings; `null` removes it. Answers the settings that changed, with their new values. */
  change(gameId: string, settings: Record<string, unknown>): Observable<Setting[]> {
    return this.http.patch<{ changed: Setting[] }>(`${this.base}/games/${gameId}/settings`, { settings }).pipe(map((r) => r.changed));
  }

  reset(gameId: string, keys: string[] | 'all'): Observable<Setting[]> {
    const body = keys === 'all' ? { all: true } : { keys };
    return this.http.post<{ changed: Setting[] }>(`${this.base}/games/${gameId}/settings/reset`, body).pipe(map((r) => r.changed));
  }

  undo(gameId: string): Observable<Setting[]> {
    return this.http.post<{ changed: Setting[] }>(`${this.base}/games/${gameId}/settings/undo`, null).pipe(map((r) => r.changed));
  }

  presets(gameId: string): Observable<Presets> {
    return this.http.get<Presets>(`${this.base}/games/${gameId}/presets`);
  }

  /** Loads a rule set or a preset; with `dryRun` it only lists what would change. */
  apply(gameId: string, ruleSetId: string, dryRun: boolean, resetOverrides: boolean): Observable<Applied> {
    return this.http.post<Applied>(`${this.base}/games/${gameId}/rulesets/apply`, { ruleSetId, dryRun, resetOverrides });
  }

  /** Saves the rules of the game as a rule set of the signed-in person. */
  saveRuleSet(gameId: string, name: string): Observable<{ id: string; name: string }> {
    return this.http.post<{ id: string; name: string }>(`${this.base}/rulesets`, { name, fromGameId: gameId });
  }
}
