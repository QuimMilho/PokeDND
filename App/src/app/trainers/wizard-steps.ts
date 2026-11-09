import { Component, computed, inject, input } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { SkinPicker } from './skin-picker';
import { ABILITIES, AbilityId, SkillDef, Species } from './trainers-api';
import {
  StepId,
  WizardState,
  abilityName,
  abilityUse,
  improvementPointsOwed,
  methodName,
  modifier,
  pointBuyCost,
  pointBuyRange,
  pointsSpent,
  signed,
} from './wizard-model';

/** The problems of a step, under it. Shown only when there is something to say. */
@Component({
  selector: 'app-step-problems',
  template: `
    @if (items().length) {
      <ul class="problems" role="status" data-testid="step-problems">
        @for (p of items(); track p.code) {
          <li [attr.data-code]="p.code">{{ p.text }}</li>
        }
      </ul>
    }
  `,
  styleUrl: './trainers.scss',
})
export class StepProblems {
  private readonly state = inject(WizardState);
  readonly step = input.required<StepId>();
  protected readonly items = computed(() => this.state.problems(this.step()));
}

// ------------------------------------------------------------------ 1. class

/** Step 1: the class (chapter 4.4). */
@Component({
  selector: 'app-step-class',
  imports: [],
  template: `
    <section class="stack" aria-labelledby="class-title">
      <h2 id="class-title" i18n="@@stepClassTitle">Choose a class</h2>
      <p class="muted" i18n="@@stepClassHelp">Your class decides your hit die, the skills you can pick and the features you get as you level up.</p>
      <ul class="choices" data-testid="classes">
        @for (c of state.classes(); track c.id) {
          <li>
            <button type="button" class="choice" [attr.aria-pressed]="state.classId() === c.id" (click)="state.setClass(c.id)" [attr.data-testid]="'class-' + c.id">
              <strong>{{ c.name }}</strong>
              <span>{{ c.description }}</span>
              <span class="muted" i18n="@@classFacts">Hit die d{{ c.hitDie }} · best at {{ abilityName(c.primaryAbility) }}</span>
              @if (c.features[0]; as f) {
                <span class="muted"><em>{{ f.name }}:</em> {{ f.text }}</span>
              }
            </button>
          </li>
        }
      </ul>
    </section>
  `,
  styleUrl: './trainers.scss',
})
export class StepClass {
  protected readonly state = inject(WizardState);
  protected readonly abilityName = abilityName;
}

// ------------------------------------------------------------------ 2. ability scores

/** Step 2: the ability scores, by point buy, the standard array or 4d6 (chapter 4.3). */
@Component({
  selector: 'app-step-scores',
  imports: [MatButtonModule],
  template: `
    @if (state.options(); as o) {
      <section class="stack" aria-labelledby="scores-title">
        <h2 id="scores-title" i18n="@@stepScoresTitle">Ability scores</h2>
        @if (o.scoreMethods.length > 1) {
          <div class="methods" role="group" aria-label="Method" i18n-aria-label="@@methodGroup">
            @for (m of o.scoreMethods; track m) {
              <button mat-stroked-button type="button" [attr.aria-pressed]="state.method() === m" (click)="state.setMethod(m)" [attr.data-testid]="'method-' + m">{{ methodName(m) }}</button>
            }
          </div>
        } @else {
          <p class="muted" data-testid="method-fixed">{{ methodName(state.method()) }}</p>
        }

        @switch (state.method()) {
          @case ('pointBuy') {
            <p data-testid="points-left" i18n="@@pointsLeft">Points left: {{ o.pointBuyBudget - spent() }} of {{ o.pointBuyBudget }}</p>
          }
          @case ('standardArray') {
            <p class="muted" i18n="@@arrayHelp">Give each number to one ability: {{ o.standardArray.join(', ') }}.</p>
          }
          @case ('roll4d6DropLowest') {
            <div class="stack">
              <p class="muted" i18n="@@rollHelp">Roll four six-sided dice for each ability and drop the lowest one. Type your results, or let the app roll for you.</p>
              <div><button mat-stroked-button type="button" (click)="state.rollAll()" data-testid="roll-all" i18n="@@rollAll">Roll for me</button></div>
            </div>
          }
        }

        <div class="abilities">
          @for (a of abilities; track a) {
            <div class="ability" [attr.data-testid]="'ability-' + a">
              <span class="name">{{ abilityName(a) }}</span>
              <span class="controls">
                @switch (state.method()) {
                  @case ('pointBuy') {
                    <button mat-stroked-button type="button" class="round" (click)="step(a, -1)" [disabled]="!canStep(a, -1)" [attr.data-testid]="'dec-' + a" [attr.aria-label]="lessLabel(a)">−</button>
                    <span class="value" [attr.data-testid]="'score-' + a">{{ state.scores()[a] }}</span>
                    <button mat-stroked-button type="button" class="round" (click)="step(a, 1)" [disabled]="!canStep(a, 1)" [attr.data-testid]="'inc-' + a" [attr.aria-label]="moreLabel(a)">+</button>
                  }
                  @case ('standardArray') {
                    <select (change)="pick(a, $any($event.target).value)" [attr.data-testid]="'array-' + a" [attr.aria-label]="abilityName(a)">
                      <option value="" [selected]="state.scores()[a] === null">—</option>
                      @for (n of o.standardArray; track n) {
                        <option [value]="n" [selected]="state.scores()[a] === n" [disabled]="usedElsewhere(a, n)">{{ n }}</option>
                      }
                    </select>
                  }
                  @case ('roll4d6DropLowest') {
                    <input type="number" min="3" max="18" inputmode="numeric" [value]="state.scores()[a] ?? ''" (input)="type(a, $any($event.target).value)" [attr.data-testid]="'roll-' + a" [attr.aria-label]="abilityName(a)" />
                  }
                }
                @if (state.scores()[a] !== null) {
                  <span class="muted" [attr.data-testid]="'mod-' + a">{{ signed(mod(a)) }}</span>
                }
              </span>
              <span class="use muted">{{ abilityUse(a) }}</span>
            </div>
          }
        </div>

        @if (owed() > 0) {
          <section class="stack" aria-labelledby="imp-title">
            <h3 id="imp-title" i18n="@@improvementTitle">Ability improvements</h3>
            <p class="muted" data-testid="improvement-help" i18n="@@improvementHelp">This game starts at level {{ o.startLevel }}, so you have {{ owed() }} points to add to your scores (no score above 20).</p>
            <div class="abilities">
              @for (a of abilities; track a) {
                <label class="ability">
                  <span class="name">{{ abilityName(a) }}</span>
                  <input type="number" min="0" [max]="owed()" inputmode="numeric" [value]="state.improvements()[a]" (input)="state.setImprovement(a, +$any($event.target).value)" [attr.data-testid]="'imp-' + a" />
                </label>
              }
            </div>
          </section>
        }
      </section>
    }
  `,
  styleUrl: './trainers.scss',
})
export class StepScores {
  protected readonly state = inject(WizardState);
  protected readonly abilities = ABILITIES;
  protected readonly abilityName = abilityName;
  protected readonly abilityUse = abilityUse;
  protected readonly methodName = methodName;
  protected readonly signed = signed;

  protected readonly spent = computed(() => {
    const o = this.state.options();
    return o ? pointsSpent(this.state.scores(), o) : 0;
  });
  protected readonly owed = computed(() => {
    const o = this.state.options();
    return o ? improvementPointsOwed(o) : 0;
  });

  protected mod(a: AbilityId): number {
    return modifier(this.state.scores()[a] ?? 10);
  }

  protected lessLabel(a: AbilityId): string {
    return $localize`:@@lessOf:Less ${abilityName(a)}:ability:`;
  }

  protected moreLabel(a: AbilityId): string {
    return $localize`:@@moreOf:More ${abilityName(a)}:ability:`;
  }

  /** Point buy: can this score go up or down by one without leaving the table or breaking the budget? */
  protected canStep(a: AbilityId, by: 1 | -1): boolean {
    const o = this.state.options();
    const current = this.state.scores()[a];
    if (!o || current === null) {
      return false;
    }
    const next = current + by;
    const range = pointBuyRange(o);
    if (next < range.min || next > range.max) {
      return false;
    }
    if (by === 1) {
      const extra = (pointBuyCost(next, o) ?? 0) - (pointBuyCost(current, o) ?? 0);
      return this.spent() + extra <= o.pointBuyBudget;
    }
    return true;
  }

  protected step(a: AbilityId, by: 1 | -1): void {
    if (this.canStep(a, by)) {
      this.state.setScore(a, (this.state.scores()[a] as number) + by);
    }
  }

  protected pick(a: AbilityId, value: string): void {
    this.state.setScore(a, value === '' ? null : Number(value));
  }

  protected type(a: AbilityId, value: string): void {
    this.state.setScore(a, value === '' ? null : Number(value));
  }

  /** The standard array gives each number once: a number another ability has cannot be chosen again. */
  protected usedElsewhere(a: AbilityId, n: number): boolean {
    return ABILITIES.some((other) => other !== a && this.state.scores()[other] === n);
  }
}

// ------------------------------------------------------------------ 3. skills

/** Step 3: the skills the class lets the player pick (chapter 4.1). */
@Component({
  selector: 'app-step-skills',
  template: `
    @if (state.selectedClass(); as cls) {
      <section class="stack" aria-labelledby="skills-title">
        <h2 id="skills-title" i18n="@@stepSkillsTitle">Skills</h2>
        <p data-testid="skills-left" i18n="@@skillsHelp">Pick {{ cls.skillChoices.pick }} of the skills of the {{ cls.name }}. Picked: {{ state.skills().length }}.</p>
        <ul class="checks">
          @for (s of choices(); track s.id) {
            <li>
              <label>
                <input type="checkbox" [checked]="state.skills().includes(s.id)" [disabled]="!state.skills().includes(s.id) && full()" (change)="state.toggleSkill(s.id)" [attr.data-testid]="'skill-' + s.id" />
                <span>{{ s.name }} <span class="muted">({{ abilityName(s.ability) }}, {{ signed(bonus(s)) }})</span></span>
              </label>
            </li>
          }
        </ul>
      </section>
    }
  `,
  styleUrl: './trainers.scss',
})
export class StepSkills {
  protected readonly state = inject(WizardState);
  protected readonly abilityName = abilityName;
  protected readonly signed = signed;

  /** The skills the class offers, with their names from the game's skill list. */
  protected readonly choices = computed<SkillDef[]>(() => {
    const cls = this.state.selectedClass();
    const all = this.state.options()?.skills ?? [];
    return (cls?.skillChoices.from ?? []).map((id) => all.find((s) => s.id === id) ?? { id, name: id, ability: 'wis' as AbilityId });
  });
  protected readonly full = computed(() => this.state.skills().length >= (this.state.selectedClass()?.skillChoices.pick ?? 0));

  /** What the skill adds if picked, at level 1: the ability modifier and the proficiency bonus (+2). */
  protected bonus(s: SkillDef): number {
    const score = this.state.scores()[s.ability] ?? 10;
    return modifier(score) + 2;
  }
}

// ------------------------------------------------------------------ 4. starter Pokémon

/** Step 4: the starter Pokémon (chapter 5.11). */
@Component({
  selector: 'app-step-starter',
  template: `
    @if (state.options(); as o) {
      <section class="stack" aria-labelledby="starter-title">
        <h2 id="starter-title" i18n="@@stepStarterTitle">Starter Pokémon</h2>
        @switch (o.starters.method) {
          @case ('pick') {
            <p class="muted" i18n="@@starterPick">Choose your first Pokémon. It starts at level {{ o.starters.level }}.</p>
            <ul class="choices" data-testid="starters">
              @for (s of o.starters.species; track s.id) {
                <li>
                  <button type="button" class="choice" [attr.aria-pressed]="state.starter() === s.id" (click)="state.starter.set(s.id)" [attr.data-testid]="'starter-' + s.id">
                    <img class="starter-art" [src]="icon(s)" alt="" width="64" height="64" loading="lazy" />
                    <strong>{{ s.name }}</strong>
                    <span class="muted">#{{ s.dex }} · {{ s.types.join(' / ') }}</span>
                  </button>
                </li>
              } @empty {
                <li class="error" data-testid="no-starters" i18n="@@noStarters">This game has no starter Pokémon to choose from. Ask the admin.</li>
              }
            </ul>
          }
          @case ('assign') {
            <p data-testid="starter-assign" i18n="@@starterAssign">The admin will give you your first Pokémon. Nothing to choose here.</p>
          }
          @case ('random') {
            <p data-testid="starter-random" i18n="@@starterRandom">The game picks your first Pokémon at random when you save your trainer.</p>
          }
        }
      </section>
    }
  `,
  styleUrl: './trainers.scss',
})
export class StepStarter {
  protected readonly state = inject(WizardState);

  protected icon(s: Species): string {
    return `/api/v1/art/${String(s.dex).padStart(4, '0')}_${s.id}/${s.id}.png?size=icon`;
  }
}

// ------------------------------------------------------------------ 5. name and skin

/** Step 5: the trainer's name (chapter 3.6) and skin. */
@Component({
  selector: 'app-step-identity',
  imports: [MatFormFieldModule, MatInputModule, SkinPicker],
  template: `
    @if (state.options(); as o) {
      <section class="stack" aria-labelledby="identity-title">
        <h2 id="identity-title" i18n="@@stepIdentityTitle">Name and skin</h2>
        @if (o.nameMode === 'accountName') {
          <p data-testid="name-from-account" i18n="@@nameFromAccount">In this game your trainer is called after your linked Minecraft account. Link one on the account page if you have not.</p>
        } @else {
          <mat-form-field appearance="outline">
            <mat-label i18n="@@trainerName">Trainer name</mat-label>
            <input matInput [value]="state.name()" (input)="state.name.set($any($event.target).value)" [attr.maxlength]="o.nameMaxLength + 8" autocomplete="off" autocapitalize="off" data-testid="name" />
            <mat-hint i18n="@@trainerNameHint">{{ o.nameMinLength }} to {{ o.nameMaxLength }} letters, digits or underscores. It must be free in this game.</mat-hint>
          </mat-form-field>
        }
        @if (o.skin.allowUpload || o.skin.allowCopyFromUsername) {
          <app-skin-picker [allowUpload]="o.skin.allowUpload" [allowCopy]="o.skin.allowCopyFromUsername" [maxBytes]="o.skin.maxBytes" [initial]="state.skin()" (changed)="state.skin.set($event)" />
        } @else {
          <p class="muted" data-testid="skins-off" i18n="@@skinsOff">This game does not allow custom skins.</p>
        }
      </section>
    }
  `,
  styleUrl: './trainers.scss',
})
export class StepIdentity {
  protected readonly state = inject(WizardState);
}

// ------------------------------------------------------------------ 6. summary

/** Step 6: everything chosen, before it is saved. */
@Component({
  selector: 'app-step-review',
  template: `
    <section class="stack" aria-labelledby="review-title">
      <h2 id="review-title" i18n="@@stepReviewTitle">Summary</h2>
      <dl class="summary" data-testid="summary">
        <dt i18n="@@sumName">Name</dt>
        <dd data-testid="sum-name">{{ nameText() }}</dd>
        <dt i18n="@@sumClass">Class</dt>
        <dd data-testid="sum-class">{{ state.selectedClass()?.name }}</dd>
        <dt i18n="@@sumScores">Ability scores</dt>
        <dd data-testid="sum-scores">{{ scoreText() }}</dd>
        <dt i18n="@@sumSkills">Skills</dt>
        <dd data-testid="sum-skills">{{ skillText() }}</dd>
        <dt i18n="@@sumStarter">Starter Pokémon</dt>
        <dd data-testid="sum-starter">{{ starterText() }}</dd>
        <dt i18n="@@sumSkin">Skin</dt>
        <dd data-testid="sum-skin">{{ skinText() }}</dd>
      </dl>
    </section>
  `,
  styleUrl: './trainers.scss',
})
export class StepReview {
  protected readonly state = inject(WizardState);

  protected nameText(): string {
    return this.state.options()?.nameMode === 'accountName' ? $localize`:@@sumNameAccount:Your Minecraft name` : this.state.name().trim();
  }

  protected scoreText(): string {
    const imp = this.state.improvements();
    return ABILITIES.map((a) => `${abilityName(a)} ${(this.state.scores()[a] ?? 0) + imp[a]}`).join(' · ');
  }

  protected skillText(): string {
    const all = this.state.options()?.skills ?? [];
    return this.state.skills().map((id) => all.find((s) => s.id === id)?.name ?? id).join(', ') || '—';
  }

  protected starterText(): string {
    const o = this.state.options();
    if (!o) {
      return '';
    }
    switch (o.starters.method) {
      case 'pick':
        return o.starters.species.find((s) => s.id === this.state.starter())?.name ?? '—';
      case 'random':
        return $localize`:@@sumStarterRandom:Picked at random`;
      default:
        return $localize`:@@sumStarterAssign:Given by the admin`;
    }
  }

  protected skinText(): string {
    const s = this.state.skin();
    switch (s.kind) {
      case 'upload':
        return $localize`:@@sumSkinUpload:Upload: ${s.file.name}:file:`;
      case 'username':
        return $localize`:@@sumSkinUsername:Copied from ${s.username}:name:`;
      default:
        return $localize`:@@sumSkinNone:None`;
    }
  }
}
