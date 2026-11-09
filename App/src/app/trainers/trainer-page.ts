import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { problemText, toProblem } from '../core/api-error';
import { AuthService } from '../core/auth.service';
import { runsGame } from '../games/game-state';
import { Game, GamesApi } from '../games/games-api';
import { ConfirmButton } from '../shared/confirm-button';
import { SkinPicker } from './skin-picker';
import { ABILITIES, SkillDef, Trainer, TrainerOptions, TrainersApi } from './trainers-api';
import { SkinChoice, abilityName, signed } from './wizard-model';

/**
 * `/games/:id/trainer` (chapters 4.1 and 11.3): the trainer sheet and a summary of the choices, with a way to change or remove the
 * trainer before the game starts, and to change the skin. The other trainers of the game are listed below.
 */
@Component({
  selector: 'app-trainer-page',
  imports: [MatCardModule, MatButtonModule, RouterLink, ConfirmButton, SkinPicker],
  template: `
    <main class="page">
      <header class="head">
        <h1 data-testid="page-title" i18n="@@trainerPageTitle">Your trainer</h1>
        <a mat-button [routerLink]="['/games', gameId]" data-testid="to-game" i18n="@@wizardBack">Back to the game</a>
      </header>

      @if (loading()) {
        <p class="muted" data-testid="loading" i18n="@@wizardLoading">Loading…</p>
      }
      @if (failure(); as text) {
        <p class="error" role="alert" data-testid="failure">{{ text }}</p>
      }

      @if (mine(); as t) {
        <mat-card appearance="outlined">
          <mat-card-header>
            <mat-card-title data-testid="trainer-name">{{ t.name }}</mat-card-title>
            <mat-card-subtitle data-testid="trainer-class">{{ t.className }} · <span i18n="@@levelN">Level {{ t.level }}</span></mat-card-subtitle>
          </mat-card-header>
          <mat-card-content class="stack">
            <div class="top">
              <div class="stack">
                @if (skinUrl(); as url) {
                  <img class="skin-image" [src]="url" alt="Skin of the trainer" i18n-alt="@@skinAlt" data-testid="skin-image" />
                } @else {
                  <p class="muted" data-testid="no-skin" i18n="@@noSkin">No skin yet.</p>
                }
                @if (t.skin) {
                  <p class="muted" data-testid="skin-info">{{ skinInfo(t) }}</p>
                }
              </div>
              <dl class="summary" data-testid="vitals">
                <dt i18n="@@vitalHp">HP</dt>
                <dd data-testid="hp">{{ t.hp }} / {{ t.hpMax }}</dd>
                @if (t.sheet; as s) {
                  <dt i18n="@@vitalAc">Armor Class</dt>
                  <dd data-testid="ac">{{ s.armorClass }}</dd>
                  <dt i18n="@@vitalProficiency">Proficiency</dt>
                  <dd data-testid="proficiency">{{ signed(s.proficiencyBonus) }}</dd>
                  <dt i18n="@@vitalHandling">Pokémon Handling</dt>
                  <dd data-testid="handling">{{ signed(s.handlingModifier) }}</dd>
                }
                <dt i18n="@@vitalXp">XP</dt>
                <dd data-testid="xp">{{ t.xp }}@if (t.xpForNextLevel !== undefined) { / {{ t.xpForNextLevel }} }</dd>
                <dt i18n="@@vitalMoney">Money</dt>
                <dd data-testid="money">₽ {{ t.money }}</dd>
                @if (t.starter; as st) {
                  <dt i18n="@@vitalStarter">Starter Pokémon</dt>
                  <dd data-testid="starter">{{ st.name }} <span i18n="@@levelN">Level {{ st.level }}</span></dd>
                }
              </dl>
            </div>

            @if (t.sheet; as s) {
              <h2 i18n="@@sheetAbilities">Abilities</h2>
              <table class="sheet" data-testid="abilities">
                <thead>
                  <tr>
                    <th scope="col" i18n="@@colAbility">Ability</th>
                    <th scope="col" i18n="@@colScore">Score</th>
                    <th scope="col" i18n="@@colModifier">Modifier</th>
                    <th scope="col" i18n="@@colSave">Saving throw</th>
                  </tr>
                </thead>
                <tbody>
                  @for (a of abilities; track a) {
                    <tr [attr.data-testid]="'row-' + a">
                      <th scope="row">{{ abilityName(a) }}</th>
                      <td>{{ t.abilityScores[a] }}</td>
                      <td>{{ signed(s.modifiers[a]) }}</td>
                      <td>{{ signed(s.savingThrows[a]) }}</td>
                    </tr>
                  }
                </tbody>
              </table>

              <h2 i18n="@@sheetSkills">Skills</h2>
              <ul class="skills" data-testid="skills">
                @for (sk of skillList(); track sk.id) {
                  <li [class.proficient]="s.proficient.includes(sk.id)">
                    {{ sk.name }} {{ signed(s.skillBonuses[sk.id] ?? 0) }}
                    @if (s.proficient.includes(sk.id)) {
                      <span class="muted" i18n="@@proficientMark">(proficient)</span>
                    }
                  </li>
                }
              </ul>

              <h2 i18n="@@sheetFeatures">Class features</h2>
              <ul class="features" data-testid="features">
                @for (f of s.features; track f.id) {
                  <li><strong>{{ f.name }}</strong> <span class="muted" i18n="@@levelN">Level {{ f.level }}</span><br />{{ f.text }}</li>
                }
              </ul>
            } @else {
              <p class="error" data-testid="no-sheet" i18n="@@noSheet">The class of this trainer is not in the game's class list any more, so the sheet cannot be shown.</p>
            }

            <h2 i18n="@@sheetBag">Bag</h2>
            <ul class="features" data-testid="bag">
              @for (item of bag(t); track item[0]) {
                <li>{{ item[1] }} × {{ item[0] }}</li>
              } @empty {
                <li class="muted" i18n="@@bagEmpty">Nothing yet.</li>
              }
            </ul>
          </mat-card-content>
          <mat-card-actions class="actions">
            @if (canChange()) {
              <a mat-flat-button [routerLink]="['/games', gameId, 'trainer', 'edit']" data-testid="edit" i18n="@@trainerEdit">Change the trainer</a>
              <app-confirm-button [label]="removeLabel" testId="remove" (confirmed)="remove(t)" />
            } @else {
              <p class="muted" data-testid="locked" i18n="@@trainerLocked">The game has started, so only the admin can change this trainer now.</p>
            }
          </mat-card-actions>
        </mat-card>

        @if (options(); as o) {
          @if (o.skin.allowUpload || o.skin.allowCopyFromUsername) {
            <mat-card appearance="outlined">
              <mat-card-header><mat-card-title i18n="@@skinSectionTitle">Change the skin</mat-card-title></mat-card-header>
              <mat-card-content class="stack">
                <app-skin-picker [allowUpload]="o.skin.allowUpload" [allowCopy]="o.skin.allowCopyFromUsername" [maxBytes]="o.skin.maxBytes" (changed)="newSkin.set($event)" />
                @if (skinFailure(); as text) {
                  <p class="error" role="alert" data-testid="skin-failure">{{ text }}</p>
                }
              </mat-card-content>
              <mat-card-actions class="actions">
                <button mat-flat-button type="button" [disabled]="newSkin().kind === 'none' || busy()" (click)="saveSkin(t)" data-testid="save-skin" i18n="@@skinSave">Use this skin</button>
                @if (t.skin) {
                  <app-confirm-button [label]="removeSkinLabel" testId="remove-skin" (confirmed)="removeSkin(t)" />
                }
              </mat-card-actions>
            </mat-card>
          }
        }
      }

      @if (others().length) {
        <section class="stack" aria-labelledby="others-title">
          <h2 id="others-title" i18n="@@othersTitle">Other trainers in this game</h2>
          <ul class="features" data-testid="others">
            @for (o of others(); track o.id) {
              <li [attr.data-testid]="'other-' + o.name"><strong>{{ o.name }}</strong> · {{ o.className }} · <span i18n="@@levelN">Level {{ o.level }}</span> <span class="muted">({{ o.ownerName }})</span></li>
            }
          </ul>
        </section>
      }
    </main>
  `,
  styleUrl: './trainers.scss',
  styles: `
    .page {
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
    .top {
      display: flex;
      flex-wrap: wrap;
      gap: 16px;
      padding-top: 12px;
    }
    .skills,
    .features {
      margin: 0;
      padding-left: 20px;
      display: flex;
      flex-direction: column;
      gap: 4px;
    }
    .skills {
      columns: 2;
      display: block;
    }
    .proficient {
      font-weight: 600;
    }
    .actions {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
      padding: 8px 16px 16px;
    }
  `,
})
export class TrainerPage implements OnInit {
  protected readonly gameId = inject(ActivatedRoute).snapshot.paramMap.get('id') ?? '';
  private readonly api = inject(TrainersApi);
  private readonly games = inject(GamesApi);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly destroy = inject(DestroyRef);

  protected readonly abilities = ABILITIES;
  protected readonly abilityName = abilityName;
  protected readonly signed = signed;
  protected readonly loading = signal(true);
  protected readonly failure = signal<string | null>(null);
  protected readonly skinFailure = signal<string | null>(null);
  protected readonly busy = signal(false);
  protected readonly game = signal<Game | null>(null);
  protected readonly options = signal<TrainerOptions | null>(null);
  protected readonly trainers = signal<Trainer[]>([]);
  protected readonly skinUrl = signal<string | null>(null);
  protected readonly newSkin = signal<SkinChoice>({ kind: 'none' });

  protected readonly mine = computed(() => this.trainers().find((t) => t.userId === this.auth.user()?.id) ?? null);
  protected readonly others = computed(() => this.trainers().filter((t) => t.userId !== this.auth.user()?.id));
  protected readonly skillList = computed<SkillDef[]>(() => this.options()?.skills ?? []);

  /** The owner may change the trainer while the game is a draft or a lobby; a game admin may always (until the trainer has played). */
  protected readonly canChange = computed(() => {
    const g = this.game();
    return g !== null && (g.state === 'draft' || g.state === 'lobby' || runsGame(g));
  });

  protected readonly removeLabel = $localize`:@@trainerRemove:Remove the trainer`;
  protected readonly removeSkinLabel = $localize`:@@skinRemove:Remove the skin`;

  ngOnInit(): void {
    this.destroy.onDestroy(() => this.revokeSkin());
    forkJoin({ game: this.games.game(this.gameId), options: this.api.options(this.gameId), trainers: this.api.list(this.gameId) }).subscribe({
      next: ({ game, options, trainers }) => {
        this.game.set(game);
        this.options.set(options);
        this.trainers.set(trainers);
        this.loading.set(false);
        const mine = this.mine();
        if (!mine && (game.myRoles.includes('player') || game.myRoles.includes('game_admin'))) {
          // nothing to show yet: start the wizard
          if (game.myRoles.includes('player')) {
            void this.router.navigate(['/games', this.gameId, 'trainer', 'create']);
          }
        }
        if (mine?.skin) {
          this.loadSkin(mine);
        }
      },
      error: (e) => {
        this.loading.set(false);
        this.failure.set(problemText(toProblem(e)));
      },
    });
  }

  protected bag(t: Trainer): [string, number][] {
    return Object.entries(t.items).sort((a, b) => a[0].localeCompare(b[0]));
  }

  protected skinInfo(t: Trainer): string {
    const s = t.skin;
    if (!s) {
      return '';
    }
    const arms = s.model === 'slim' ? $localize`:@@armsSlim:slim arms` : $localize`:@@armsClassic:classic arms`;
    return s.source === 'username' ? $localize`:@@skinInfoCopy:Copied from ${s.username ?? ''}:name:, ${arms}:arms:` : $localize`:@@skinInfoUpload:Uploaded, ${arms}:arms:`;
  }

  protected saveSkin(t: Trainer): void {
    const skin = this.newSkin();
    if (skin.kind === 'none') {
      return;
    }
    this.busy.set(true);
    this.skinFailure.set(null);
    const call = skin.kind === 'upload' ? this.api.uploadSkin(t.id, skin.file, skin.model) : this.api.copySkin(t.id, skin.username);
    call.subscribe({
      next: (updated) => {
        this.busy.set(false);
        this.replace(updated);
        this.loadSkin(updated);
      },
      error: (e) => {
        this.busy.set(false);
        this.skinFailure.set(problemText(toProblem(e)));
      },
    });
  }

  protected removeSkin(t: Trainer): void {
    this.busy.set(true);
    this.api.clearSkin(t.id).subscribe({
      next: (updated) => {
        this.busy.set(false);
        this.revokeSkin();
        this.replace(updated);
      },
      error: (e) => {
        this.busy.set(false);
        this.skinFailure.set(problemText(toProblem(e)));
      },
    });
  }

  protected remove(t: Trainer): void {
    this.failure.set(null);
    this.api.remove(t.id).subscribe({
      next: () => void this.router.navigate(['/games', this.gameId, 'trainer', 'create']),
      error: (e) => this.failure.set(problemText(toProblem(e))),
    });
  }

  private replace(updated: Trainer): void {
    this.trainers.update((list) => list.map((t) => (t.id === updated.id ? updated : t)));
  }

  private loadSkin(t: Trainer): void {
    this.api.skin(t.id).subscribe({
      next: (blob) => {
        this.revokeSkin();
        this.skinUrl.set(URL.createObjectURL(blob));
      },
      error: () => this.skinUrl.set(null),
    });
  }

  private revokeSkin(): void {
    const url = this.skinUrl();
    if (url) {
      URL.revokeObjectURL(url);
      this.skinUrl.set(null);
    }
  }
}
