import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { EMPTY, Observable, catchError, forkJoin, of, switchMap } from 'rxjs';
import { problemText, toProblem } from '../core/api-error';
import { AuthService } from '../core/auth.service';
import { GamesApi } from '../games/games-api';
import { ServerProblem, Trainer, TrainersApi } from './trainers-api';
import { StepClass, StepIdentity, StepProblems, StepReview, StepScores, StepSkills, StepStarter } from './wizard-steps';
import { STEPS, WizardState, serverProblemText, stepName } from './wizard-model';

/**
 * `/games/:id/trainer/create` and `/games/:id/trainer/edit` (chapters 4.3 and 11.3): the trainer wizard. Class, ability scores, skills,
 * starter Pokémon, name and skin, then a summary. Each step is checked here and again by the API. Editing remakes the trainer from the
 * same steps (possible while the game has not started).
 */
@Component({
  selector: 'app-trainer-wizard',
  imports: [MatButtonModule, RouterLink, StepClass, StepScores, StepSkills, StepStarter, StepIdentity, StepReview, StepProblems],
  providers: [WizardState],
  template: `
    <main class="wizard">
      <header class="head">
        <h1 data-testid="wizard-title">{{ edit ? editTitle : createTitle }}</h1>
        <a mat-button [routerLink]="['/games', gameId]" data-testid="to-game" i18n="@@wizardBack">Back to the game</a>
      </header>

      @if (loading()) {
        <p class="muted" data-testid="loading" i18n="@@wizardLoading">Loading…</p>
      } @else if (blocked(); as why) {
        <p class="error" role="alert" data-testid="blocked">{{ why }}</p>
      } @else {
        <nav aria-label="Steps" i18n-aria-label="@@wizardSteps">
          <ol class="progress">
            @for (s of steps; track s; let i = $index) {
              <li [attr.aria-current]="i === index() ? 'step' : null" [class.done]="i < index()" [attr.data-testid]="'progress-' + s">{{ i + 1 }}. {{ stepName(s) }}</li>
            }
          </ol>
        </nav>

        <div class="panel" [attr.data-step]="step()">
          @switch (step()) {
            @case ('class') {
              <app-step-class />
            }
            @case ('scores') {
              <app-step-scores />
            }
            @case ('skills') {
              <app-step-skills />
            }
            @case ('starter') {
              <app-step-starter />
            }
            @case ('identity') {
              <app-step-identity />
            }
            @case ('review') {
              <app-step-review />
            }
          }
        </div>

        @if (attempted() || step() === 'review') {
          <app-step-problems [step]="step()" />
        }

        @if (serverProblems().length) {
          <ul class="problems" role="alert" data-testid="server-problems">
            @for (p of serverProblems(); track $index) {
              <li>{{ p }}</li>
            }
          </ul>
        }
        @if (failure(); as text) {
          <p class="error" role="alert" data-testid="failure">{{ text }}</p>
        }
        @if (skinFailure(); as text) {
          <div class="stack" role="alert" data-testid="skin-failure">
            <p class="error">{{ text }}</p>
            <p i18n="@@wizardSkinFailed">Your trainer is saved, but the skin was not. You can try again, or continue and change the skin on your trainer page.</p>
            <div class="nav">
              <button mat-flat-button type="button" (click)="submit()" [disabled]="saving()" data-testid="retry-skin" i18n="@@wizardRetrySkin">Try the skin again</button>
              <button mat-stroked-button type="button" (click)="finish()" data-testid="skip-skin" i18n="@@wizardSkipSkin">Continue without the skin</button>
            </div>
          </div>
        } @else {
          <div class="nav">
            <button mat-stroked-button type="button" (click)="back()" [disabled]="index() === 0 || saving()" data-testid="back" i18n="@@wizardPrevious">Back</button>
            @if (step() !== 'review') {
              <button mat-flat-button type="button" (click)="next()" data-testid="next" i18n="@@wizardNext">Next</button>
            } @else {
              <button mat-flat-button type="button" (click)="submit()" [disabled]="saving() || !state.isValid('review')" data-testid="submit">{{ edit ? saveLabel : createLabel }}</button>
            }
          </div>
        }
      }
    </main>
  `,
  styleUrl: './trainers.scss',
  styles: `
    .wizard {
      padding: 16px;
      max-width: 720px;
      margin: 0 auto;
      display: flex;
      flex-direction: column;
      gap: 16px;
    }
    .head {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: space-between;
      gap: 8px;
    }
    h1 {
      font: var(--mat-sys-headline-medium);
    }
    .progress {
      list-style: none;
      margin: 0;
      padding: 0;
      display: flex;
      flex-wrap: wrap;
      gap: 4px 12px;
      font: var(--mat-sys-label-large);
      color: var(--mat-sys-on-surface-variant);

      li[aria-current='step'] {
        color: var(--mat-sys-primary);
        font-weight: 700;
        text-decoration: underline;
      }
      li.done {
        color: var(--mat-sys-on-surface);
      }
    }
  `,
})
export class TrainerWizard implements OnInit {
  protected readonly gameId = inject(ActivatedRoute).snapshot.paramMap.get('id') ?? '';
  protected readonly edit = inject(ActivatedRoute).snapshot.data['edit'] === true;
  protected readonly state = inject(WizardState);
  private readonly api = inject(TrainersApi);
  private readonly games = inject(GamesApi);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly steps = STEPS;
  protected readonly stepName = stepName;
  protected readonly index = signal(0);
  protected readonly step = computed(() => STEPS[this.index()]);
  protected readonly loading = signal(true);
  protected readonly blocked = signal<string | null>(null);
  protected readonly saving = signal(false);
  protected readonly attempted = signal(false);
  protected readonly failure = signal<string | null>(null);
  protected readonly skinFailure = signal<string | null>(null);
  protected readonly serverProblems = signal<string[]>([]);

  protected readonly createTitle = $localize`:@@wizardCreateTitle:Make your trainer`;
  protected readonly editTitle = $localize`:@@wizardEditTitle:Change your trainer`;
  protected readonly createLabel = $localize`:@@wizardCreate:Create the trainer`;
  protected readonly saveLabel = $localize`:@@wizardSave:Save the changes`;

  /** The trainer once it is saved, so that a second try only repeats the skin. */
  private saved: Trainer | null = null;
  private editing: Trainer | null = null;

  ngOnInit(): void {
    forkJoin({
      game: this.games.game(this.gameId),
      options: this.api.options(this.gameId),
      classes: this.api.classes(this.gameId),
      trainers: this.api.list(this.gameId),
    }).subscribe({
      next: ({ game, options, classes, trainers }) => {
        const mine = trainers.find((t) => t.userId === this.auth.user()?.id) ?? null;
        const others = trainers.filter((t) => t !== mine).map((t) => t.name);
        const may = game.myRoles.includes('player') || game.myRoles.includes('game_admin') || game.myRoles.includes('site_admin');
        if (!may) {
          this.blocked.set($localize`:@@wizardNotPlayer:Only players make trainers. Ask the admin to accept you as a player.`);
        } else if (!this.edit && mine) {
          void this.router.navigate(['/games', this.gameId, 'trainer']);
          return;
        } else if (this.edit && !mine) {
          void this.router.navigate(['/games', this.gameId, 'trainer', 'create']);
          return;
        }
        this.state.configure(options, classes, others);
        if (this.edit && mine) {
          this.editing = mine;
          this.state.load(mine);
        }
        this.loading.set(false);
      },
      error: (e) => {
        this.blocked.set(problemText(toProblem(e)));
        this.loading.set(false);
      },
    });
  }

  protected next(): void {
    if (this.state.isValid(this.step())) {
      this.attempted.set(false);
      this.index.update((i) => Math.min(i + 1, STEPS.length - 1));
    } else {
      this.attempted.set(true);
    }
  }

  protected back(): void {
    this.attempted.set(false);
    this.index.update((i) => Math.max(i - 1, 0));
  }

  protected submit(): void {
    if (!this.state.isValid('review') || this.saving()) {
      return;
    }
    this.saving.set(true);
    this.failure.set(null);
    this.skinFailure.set(null);
    this.serverProblems.set([]);
    const choices = this.state.choices();
    const save$: Observable<Trainer> = this.saved
      ? of(this.saved)
      : this.editing
        ? this.api.change(this.editing.id, choices)
        : this.api.create(this.gameId, choices);
    save$
      .pipe(
        switchMap((trainer) => {
          this.saved = trainer;
          return this.sendSkin(trainer).pipe(
            catchError((e) => {
              this.saving.set(false);
              this.skinFailure.set(problemText(toProblem(e)));
              return EMPTY;
            }),
          );
        }),
      )
      .subscribe({
        next: () => {
          this.saving.set(false);
          this.finish();
        },
        error: (e) => {
          this.saving.set(false);
          const problem = toProblem(e);
          const list = problem.details['problems'];
          if (problem.code === 'INVALID_TRAINER' && Array.isArray(list)) {
            this.serverProblems.set((list as ServerProblem[]).map((p) => serverProblemText(p, this.state.options())));
          } else {
            this.failure.set(problemText(problem));
          }
        },
      });
  }

  /** Sends the skin that was chosen, if any, to the saved trainer. */
  private sendSkin(trainer: Trainer): Observable<Trainer> {
    const skin = this.state.skin();
    switch (skin.kind) {
      case 'upload':
        return this.api.uploadSkin(trainer.id, skin.file, skin.model);
      case 'username':
        return this.api.copySkin(trainer.id, skin.username);
      default:
        return of(trainer);
    }
  }

  protected finish(): void {
    void this.router.navigate(['/games', this.gameId, 'trainer']);
  }
}
