import { Injectable, computed, signal } from '@angular/core';
import {
  ABILITIES,
  AbilityId,
  ScoreMethodId,
  ServerProblem,
  Trainer,
  TrainerChoices,
  TrainerClass,
  TrainerOptions,
} from './trainers-api';

/** The steps of the trainer wizard, in order (chapter 4.3 and 11.3). */
export type StepId = 'class' | 'scores' | 'skills' | 'starter' | 'identity' | 'review';
export const STEPS: readonly StepId[] = ['class', 'scores', 'skills', 'starter', 'identity', 'review'];

/** What the player chose for the skin. It is sent after the trainer is saved. */
export type SkinChoice =
  | { kind: 'none' }
  | { kind: 'upload'; file: File; model: 'classic' | 'slim' }
  | { kind: 'username'; username: string };

/** One reason a step is not ready, in the language of this build. */
export interface Problem {
  code: string;
  text: string;
}

export type Scores = Record<AbilityId, number | null>;

export function modifier(score: number): number {
  return Math.floor((score - 10) / 2);
}

/** `+2`, `0`, `−1`. */
export function signed(n: number): string {
  return n > 0 ? `+${n}` : n < 0 ? `−${-n}` : '0';
}

export function abilityName(id: AbilityId): string {
  switch (id) {
    case 'str':
      return $localize`:@@abilityStr:Strength`;
    case 'dex':
      return $localize`:@@abilityDex:Dexterity`;
    case 'con':
      return $localize`:@@abilityCon:Constitution`;
    case 'int':
      return $localize`:@@abilityInt:Intelligence`;
    case 'wis':
      return $localize`:@@abilityWis:Wisdom`;
    case 'cha':
      return $localize`:@@abilityCha:Charisma`;
  }
}

/** What the ability is used for in PokeDnD (chapter 4.1), in a few words. */
export function abilityUse(id: AbilityId): string {
  switch (id) {
    case 'str':
      return $localize`:@@abilityStrUse:Athletics, carrying`;
    case 'dex':
      return $localize`:@@abilityDexUse:Movement roll, Armor Class, fleeing`;
    case 'con':
      return $localize`:@@abilityConUse:Trainer HP`;
    case 'int':
      return $localize`:@@abilityIntUse:Pokémon Lore, Nature, Investigation`;
    case 'wis':
      return $localize`:@@abilityWisUse:Pokémon Handling: accuracy and catching`;
    case 'cha':
      return $localize`:@@abilityChaUse:Persuasion, Performance, shop prices`;
  }
}

export function methodName(m: ScoreMethodId): string {
  switch (m) {
    case 'pointBuy':
      return $localize`:@@methodPointBuy:Point buy`;
    case 'standardArray':
      return $localize`:@@methodStandardArray:Standard array`;
    case 'roll4d6DropLowest':
      return $localize`:@@methodRoll:Roll 4d6, drop the lowest`;
  }
}

export function stepName(step: StepId): string {
  switch (step) {
    case 'class':
      return $localize`:@@stepClass:Class`;
    case 'scores':
      return $localize`:@@stepScores:Ability scores`;
    case 'skills':
      return $localize`:@@stepSkills:Skills`;
    case 'starter':
      return $localize`:@@stepStarter:Starter Pokémon`;
    case 'identity':
      return $localize`:@@stepIdentity:Name and skin`;
    case 'review':
      return $localize`:@@stepReview:Summary`;
  }
}

/** How many Ability Score Improvement points a trainer who starts at the game's starting level has already earned (chapter 4.1). */
export function improvementPointsOwed(o: TrainerOptions): number {
  return 2 * o.improvementLevels.filter((level) => level <= o.startLevel).length;
}

/** The point-buy cost of a score, or null when point buy has no such score. */
export function pointBuyCost(score: number, o: TrainerOptions): number | null {
  return o.pointBuyCosts[String(score)] ?? null;
}

export function pointBuyRange(o: TrainerOptions): { min: number; max: number } {
  const scores = Object.keys(o.pointBuyCosts).map(Number);
  return { min: Math.min(...scores), max: Math.max(...scores) };
}

export function pointsSpent(scores: Scores, o: TrainerOptions): number {
  return ABILITIES.reduce((total, a) => total + (pointBuyCost(scores[a] ?? -1, o) ?? 0), 0);
}

/** The scores a method starts from. */
export function defaultScores(method: ScoreMethodId, o: TrainerOptions): Scores {
  const start = method === 'pointBuy' ? pointBuyRange(o).min : null;
  return { str: start, dex: start, con: start, int: start, wis: start, cha: start };
}

// ------------------------------------------------------------------ the checks, one per step (the API checks them again)

export function checkScores(method: ScoreMethodId, scores: Scores, o: TrainerOptions): Problem[] {
  const problems: Problem[] = [];
  const missing = ABILITIES.filter((a) => scores[a] === null || scores[a] === undefined || Number.isNaN(scores[a]));
  if (missing.length) {
    problems.push({ code: 'scores_missing', text: $localize`:@@wzScoresMissing:Set all six ability scores.` });
    return problems;
  }
  const values = ABILITIES.map((a) => scores[a] as number);
  switch (method) {
    case 'pointBuy': {
      const { min, max } = pointBuyRange(o);
      for (const a of ABILITIES) {
        const s = scores[a] as number;
        if (s < min || s > max) {
          problems.push({ code: 'score_out_of_range', text: $localize`:@@wzScoreRange:${abilityName(a)}:ability: is ${s}:score:, but with point buy a score is ${min}:min: to ${max}:max:.` });
        }
      }
      if (!problems.length) {
        const spent = pointsSpent(scores, o);
        if (spent > o.pointBuyBudget) {
          problems.push({ code: 'too_many_points', text: $localize`:@@wzTooManyPoints:These scores cost ${spent}:spent: points, but you only have ${o.pointBuyBudget}:budget:.` });
        }
      }
      break;
    }
    case 'standardArray': {
      const sorted = [...values].sort((x, y) => y - x);
      if (sorted.join() !== [...o.standardArray].sort((x, y) => y - x).join()) {
        problems.push({ code: 'not_standard_array', text: $localize`:@@wzNotArray:Use each number of the standard array (${o.standardArray.join(', ')}:numbers:) once.` });
      }
      break;
    }
    case 'roll4d6DropLowest':
      for (const a of ABILITIES) {
        const s = scores[a] as number;
        if (!Number.isInteger(s) || s < 3 || s > 18) {
          problems.push({ code: 'roll_out_of_range', text: $localize`:@@wzRollRange:${abilityName(a)}:ability: is ${s}:score:, but 4d6 drop the lowest gives 3 to 18.` });
        }
      }
      break;
  }
  return problems;
}

export function checkImprovements(imp: Record<AbilityId, number>, scores: Scores, o: TrainerOptions): Problem[] {
  const owed = improvementPointsOwed(o);
  if (owed === 0) {
    return [];
  }
  const given = ABILITIES.reduce((n, a) => n + imp[a], 0);
  if (given !== owed) {
    return [{ code: 'improvement_points', text: $localize`:@@wzImprovementPoints:Spend exactly ${owed}:owed: improvement points (${given}:given: so far).` }];
  }
  const problems: Problem[] = [];
  for (const a of ABILITIES) {
    const after = (scores[a] ?? 0) + imp[a];
    if (after > 20) {
      problems.push({ code: 'score_cap', text: $localize`:@@wzScoreCap:${abilityName(a)}:ability: would be ${after}:score:, but no score goes above 20.` });
    }
  }
  return problems;
}

export function checkSkills(cls: TrainerClass | undefined, picked: string[]): Problem[] {
  if (!cls) {
    return [];
  }
  const pick = cls.skillChoices.pick;
  if (picked.length !== pick) {
    return [{ code: 'skill_count', text: $localize`:@@wzSkillCount:Pick ${pick}:pick: skills (${picked.length}:picked: chosen).` }];
  }
  return [];
}

export function checkName(name: string, o: TrainerOptions, taken: string[]): Problem[] {
  if (o.nameMode === 'accountName') {
    return [];
  }
  const problems: Problem[] = [];
  if (name.length === 0) {
    return [{ code: 'name_missing', text: $localize`:@@wzNameMissing:The trainer needs a name.` }];
  }
  if (name.length < o.nameMinLength || name.length > o.nameMaxLength) {
    problems.push({ code: 'name_length', text: $localize`:@@wzNameLength:The name must be ${o.nameMinLength}:min: to ${o.nameMaxLength}:max: characters long.` });
  }
  if (!/^[A-Za-z0-9_]+$/.test(name)) {
    problems.push({ code: 'name_characters', text: $localize`:@@wzNameCharacters:The name may only use letters, digits and underscores (like a Minecraft username).` });
  }
  if (!problems.length && taken.some((t) => t.toLowerCase() === name.toLowerCase())) {
    problems.push({ code: 'name_taken', text: $localize`:@@wzNameTaken:Another trainer in this game is already called ${name}:name:.` });
  }
  return problems;
}

export function checkStarter(o: TrainerOptions, starter: string | null): Problem[] {
  if (o.starters.method === 'pick' && !starter) {
    return [{ code: 'starter_missing', text: $localize`:@@wzStarterMissing:Choose a starter Pokémon.` }];
  }
  return [];
}

/**
 * The sentence for a problem the API found. The wizard checks most things itself first, so these show up only when something
 * changed in the meantime (another player took the name, the admin changed a setting). The API's English text is never shown.
 */
export function serverProblemText(p: ServerProblem, o: TrainerOptions | null): string {
  switch (p.code) {
    case 'name_taken':
      return $localize`:@@srvNameTaken:Another trainer in this game has this name already.`;
    case 'name_missing':
      return $localize`:@@wzNameMissing:The trainer needs a name.`;
    case 'name_length':
      return $localize`:@@wzNameLength:The name must be ${o?.nameMinLength ?? 3}:min: to ${o?.nameMaxLength ?? 16}:max: characters long.`;
    case 'name_characters':
      return $localize`:@@wzNameCharacters:The name may only use letters, digits and underscores (like a Minecraft username).`;
    case 'unknown_class':
      return $localize`:@@srvUnknownClass:This game does not have that class (the admin may have changed the class list). Choose another.`;
    case 'scores_missing':
      return $localize`:@@wzScoresMissing:Set all six ability scores.`;
    case 'too_many_points':
      return $localize`:@@srvTooManyPoints:These scores cost more points than the game allows.`;
    case 'method_not_allowed':
      return $localize`:@@srvMethodNotAllowed:This game does not allow that way of making ability scores.`;
    case 'skill_count':
    case 'skill_duplicate':
    case 'skill_not_allowed':
      return $localize`:@@srvSkills:The skills do not fit the class. Choose them again.`;
    case 'starter_missing':
      return $localize`:@@wzStarterMissing:Choose a starter Pokémon.`;
    case 'starter_not_allowed':
      return $localize`:@@srvStarterNotAllowed:That Pokémon is not one of the starters of this game.`;
    default:
      return $localize`:@@srvInvalid:The trainer does not follow the rules of this game. Check each step.`;
  }
}

/** The numbers 1 to 6 on four dice, the lowest dropped (chapter 4.3). `die` gives 1 to 6. */
export function rollFourD6DropLowest(die: () => number): number {
  const dice = [die(), die(), die(), die()];
  return dice.reduce((a, b) => a + b, 0) - Math.min(...dice);
}

/** A fair d6 from the browser's secure random numbers. */
export function secureD6(): number {
  const buffer = new Uint32Array(1);
  const limit = Math.floor(0x100000000 / 6) * 6;
  do {
    crypto.getRandomValues(buffer);
  } while (buffer[0] >= limit);
  return (buffer[0] % 6) + 1;
}

/**
 * The choices of the person making a trainer, shared by the steps of the wizard. One of these is provided by the wizard itself, so
 * a new visit starts clean.
 */
@Injectable()
export class WizardState {
  readonly options = signal<TrainerOptions | null>(null);
  readonly classes = signal<TrainerClass[]>([]);
  /** The names of the other trainers of the game. */
  readonly takenNames = signal<string[]>([]);

  readonly classId = signal<string | null>(null);
  readonly method = signal<ScoreMethodId>('pointBuy');
  readonly scores = signal<Scores>({ str: 8, dex: 8, con: 8, int: 8, wis: 8, cha: 8 });
  readonly improvements = signal<Record<AbilityId, number>>({ str: 0, dex: 0, con: 0, int: 0, wis: 0, cha: 0 });
  readonly skills = signal<string[]>([]);
  readonly starter = signal<string | null>(null);
  readonly name = signal('');
  readonly skin = signal<SkinChoice>({ kind: 'none' });

  readonly selectedClass = computed(() => this.classes().find((c) => c.id === this.classId()));

  /** Sets what the game offers. The first allowed method is the starting one. */
  configure(options: TrainerOptions, classes: TrainerClass[], takenNames: string[]): void {
    this.options.set(options);
    this.classes.set(classes);
    this.takenNames.set(takenNames);
    this.setMethod(options.scoreMethods[0] ?? 'pointBuy');
  }

  /** Fills the wizard with an existing trainer (editing). */
  load(trainer: Trainer): void {
    this.classId.set(trainer.classId);
    this.name.set(trainer.name);
    this.skills.set([...trainer.skills]);
    this.starter.set(trainer.starter?.species ?? null);
    const o = this.options();
    const owed = o ? improvementPointsOwed(o) : 0;
    this.method.set(trainer.scoreMethod);
    // the stored scores include the improvements of a trainer that starts above level 3; the wizard cannot tell them apart, so the
    // player spends the improvements again
    this.scores.set({ ...trainer.abilityScores });
    this.improvements.set({ str: 0, dex: 0, con: 0, int: 0, wis: 0, cha: 0 });
    if (owed > 0) {
      this.setMethod(trainer.scoreMethod);
    }
  }

  setClass(id: string): void {
    if (this.classId() !== id) {
      this.classId.set(id);
      this.skills.set([]);
    }
  }

  setMethod(method: ScoreMethodId): void {
    this.method.set(method);
    const o = this.options();
    if (o) {
      this.scores.set(defaultScores(method, o));
    }
    this.improvements.set({ str: 0, dex: 0, con: 0, int: 0, wis: 0, cha: 0 });
  }

  setScore(ability: AbilityId, value: number | null): void {
    this.scores.update((s) => ({ ...s, [ability]: value }));
  }

  setImprovement(ability: AbilityId, value: number): void {
    this.improvements.update((s) => ({ ...s, [ability]: Math.max(0, value) }));
  }

  toggleSkill(id: string): void {
    this.skills.update((list) => (list.includes(id) ? list.filter((s) => s !== id) : [...list, id]));
  }

  /** Rolls all six scores (4d6, the lowest die dropped), for the method "roll". */
  rollAll(die: () => number = secureD6): void {
    const rolled = ABILITIES.map(() => rollFourD6DropLowest(die));
    this.scores.set({ str: rolled[0], dex: rolled[1], con: rolled[2], int: rolled[3], wis: rolled[4], cha: rolled[5] });
  }

  problems(step: StepId): Problem[] {
    const o = this.options();
    if (!o) {
      return [];
    }
    switch (step) {
      case 'class':
        return this.selectedClass() ? [] : [{ code: 'class_missing', text: $localize`:@@wzClassMissing:Choose a class.` }];
      case 'scores':
        return [...checkScores(this.method(), this.scores(), o), ...checkImprovements(this.improvements(), this.scores(), o)];
      case 'skills':
        return checkSkills(this.selectedClass(), this.skills());
      case 'starter':
        return checkStarter(o, this.starter());
      case 'identity':
        return checkName(this.name().trim(), o, this.takenNames());
      case 'review':
        return STEPS.slice(0, 5).flatMap((s) => this.problems(s));
    }
  }

  isValid(step: StepId): boolean {
    return this.problems(step).length === 0;
  }

  /** What to send to the API. Call only when every step is valid. */
  choices(): TrainerChoices {
    const improvements: Record<string, number> = {};
    for (const a of ABILITIES) {
      if (this.improvements()[a] > 0) {
        improvements[a] = this.improvements()[a];
      }
    }
    return {
      name: this.name().trim(),
      classId: this.classId() ?? '',
      method: this.method(),
      scores: ABILITIES.reduce((acc, a) => ({ ...acc, [a]: this.scores()[a] ?? 0 }), {} as Record<AbilityId, number>),
      skills: [...this.skills()],
      improvements,
      starter: this.options()?.starters.method === 'pick' ? this.starter() : null,
    };
  }
}
