import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE } from '../core/api-config';

export type AbilityId = 'str' | 'dex' | 'con' | 'int' | 'wis' | 'cha';
export const ABILITIES: readonly AbilityId[] = ['str', 'dex', 'con', 'int', 'wis', 'cha'];

export type ScoreMethodId = 'pointBuy' | 'standardArray' | 'roll4d6DropLowest';

/** One effect of a class feature (`FeatureEffect` in Core). */
export interface FeatureEffect {
  key: string;
  value: number;
  params: Record<string, string>;
}

export interface ClassFeature {
  level: number;
  id: string;
  name: string;
  text: string;
  effects: FeatureEffect[];
}

/** A class of the game (`TrainerClass` in Core). */
export interface TrainerClass {
  id: string;
  name: string;
  description: string;
  hitDie: number;
  primaryAbility: AbilityId;
  saves: AbilityId[];
  skillChoices: { pick: number; from: string[] };
  acBonus: number;
  startingMoney: number;
  startingItems: Record<string, number>;
  features: ClassFeature[];
}

export interface SkillDef {
  id: string;
  name: string;
  ability: AbilityId;
}

export interface Species {
  id: string;
  name: string;
  dex: number;
  types: string[];
}

/** Everything the wizard needs to draw its steps (`OptionsDto` in the API). */
export interface TrainerOptions {
  nameMode: 'perGame' | 'accountName';
  nameMinLength: number;
  nameMaxLength: number;
  scoreMethods: ScoreMethodId[];
  pointBuyBudget: number;
  /** Cost of each point-buy score; the keys are the scores as text. */
  pointBuyCosts: Record<string, number>;
  standardArray: number[];
  startLevel: number;
  improvementLevels: number[];
  skills: SkillDef[];
  starters: { method: 'pick' | 'assign' | 'random'; level: number; species: Species[] };
  skin: { allowUpload: boolean; allowCopyFromUsername: boolean; maxBytes: number; size: number };
  hasTrainer: boolean;
}

export interface Sheet {
  proficiencyBonus: number;
  armorClass: number;
  modifiers: Record<AbilityId, number>;
  savingThrows: Record<AbilityId, number>;
  skillBonuses: Record<string, number>;
  proficient: string[];
  handlingModifier: number;
  features: ClassFeature[];
  effects: Record<string, number>;
  narrative: string[];
}

export interface TrainerSkin {
  model: 'classic' | 'slim';
  source: 'upload' | 'username';
  username?: string;
  sha256: string;
  updatedAt: string;
}

/** A trainer (`TrainerDto` in the API). `sheet` is missing when the game's class list no longer has the trainer's class. */
export interface Trainer {
  id: string;
  gameId: string;
  userId: string;
  ownerName: string;
  name: string;
  classId: string;
  className: string;
  level: number;
  xp: number;
  xpForNextLevel?: number;
  abilityScores: Record<AbilityId, number>;
  skills: string[];
  hp: number;
  hpMax: number;
  money: number;
  items: Record<string, number>;
  scoreMethod: ScoreMethodId;
  starter?: { species: string; name: string; level: number };
  skin?: TrainerSkin;
  sheet?: Sheet;
}

/** What the wizard sends (`ChoicesBody` in the API). */
export interface TrainerChoices {
  name: string;
  classId: string;
  method: ScoreMethodId;
  scores: Record<AbilityId, number>;
  skills: string[];
  improvements: Record<string, number>;
  starter: string | null;
}

/** One reason the API refused a trainer (`details.problems` of `INVALID_TRAINER`). */
export interface ServerProblem {
  code: string;
  field: string;
  message: string;
}

/** The trainer calls of chapter 18.1 that the wizard and the trainer page use. */
@Injectable({ providedIn: 'root' })
export class TrainersApi {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_BASE);

  classes(gameId: string): Observable<TrainerClass[]> {
    return this.http.get<TrainerClass[]>(`${this.base}/games/${gameId}/classes`);
  }

  options(gameId: string): Observable<TrainerOptions> {
    return this.http.get<TrainerOptions>(`${this.base}/games/${gameId}/trainer-options`);
  }

  list(gameId: string): Observable<Trainer[]> {
    return this.http.get<Trainer[]>(`${this.base}/games/${gameId}/trainers`);
  }

  create(gameId: string, choices: TrainerChoices): Observable<Trainer> {
    return this.http.post<Trainer>(`${this.base}/games/${gameId}/trainers`, choices);
  }

  change(id: string, choices: TrainerChoices): Observable<Trainer> {
    return this.http.patch<Trainer>(`${this.base}/trainers/${id}`, choices);
  }

  remove(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/trainers/${id}`);
  }

  uploadSkin(id: string, file: File, model: 'classic' | 'slim'): Observable<Trainer> {
    const form = new FormData();
    form.append('file', file, file.name);
    form.append('model', model);
    return this.http.put<Trainer>(`${this.base}/trainers/${id}/skin`, form);
  }

  copySkin(id: string, username: string): Observable<Trainer> {
    return this.http.put<Trainer>(`${this.base}/trainers/${id}/skin`, { username });
  }

  clearSkin(id: string): Observable<Trainer> {
    return this.http.delete<Trainer>(`${this.base}/trainers/${id}/skin`);
  }

  /** The skin picture. (It needs the login, so an `<img>` cannot ask for it; the page makes an object URL from the bytes.) */
  skin(id: string): Observable<Blob> {
    return this.http.get(`${this.base}/trainers/${id}/skin`, { responseType: 'blob' });
  }
}
