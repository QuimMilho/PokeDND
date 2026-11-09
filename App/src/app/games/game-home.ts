import { Component, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { problemText, toProblem } from '../core/api-error';
import { ConfirmButton } from '../shared/confirm-button';
import { Action, actionLabel, actionsFor, runsGame, stateLabel } from './game-state';
import { Game, GamesApi } from './games-api';

/** `/games/:id` (chapter 11.2): the game's state and, for the person who runs it, a button for each step the state allows (3.4). */
@Component({
  selector: 'app-game-home',
  imports: [RouterLink, MatCardModule, MatButtonModule, ConfirmButton],
  template: `
    <main class="game">
      @if (game(); as g) {
        <mat-card appearance="outlined">
          <mat-card-header>
            <mat-card-title data-testid="game-name">{{ g.name }}</mat-card-title>
            <mat-card-subtitle>
              <span data-testid="game-state" [attr.data-state]="g.state">{{ stateLabel(g.state) }}</span>
            </mat-card-subtitle>
          </mat-card-header>
          <mat-card-content class="stack">
            @if (g.admins?.length) {
              <p class="muted" data-testid="admins" i18n="@@gameAdmins">Run by {{ adminNames() }}.</p>
            }
            @if (g.state === 'draft') {
              @if (runs()) {
                <p i18n="@@gameDraftAdmin">This game is a draft. Nobody can join yet. Change its settings, then open the lobby.</p>
              } @else {
                <p i18n="@@gameDraftPlayer">The admin is still preparing this game.</p>
              }
            }
            @if (g.state === 'ended' || g.state === 'archived') {
              <p i18n="@@gameOver">This game is over.</p>
            }
            @if (g.lobbyOpen) {
              <p data-testid="lobby-open" i18n="@@gameLobbyOpen">The lobby is open.</p>
            }
          </mat-card-content>
          <mat-card-actions class="actions">
            @if (g.lobbyOpen || runs()) {
              <a mat-stroked-button [routerLink]="['/games', g.id, 'lobby']" data-testid="to-lobby" i18n="@@gameToLobby">Lobby and members</a>
            }
            @if (g.myRoles.includes('player') && g.state !== 'archived') {
              <a mat-flat-button [routerLink]="['/games', g.id, 'trainer']" data-testid="to-trainer" i18n="@@gameToTrainer">Your trainer</a>
            }
            @if (runs()) {
              <a mat-stroked-button [routerLink]="['/games', g.id, 'admin', 'settings']" data-testid="to-settings" i18n="@@gameToSettings">Settings</a>
              @for (a of actions(); track a.id) {
                @if (a.destructive) {
                  <app-confirm-button [label]="label(a)" [testId]="'action-' + a.id" [disabled]="busy()" (confirmed)="run(a)" />
                } @else {
                  <button mat-flat-button type="button" [disabled]="busy()" (click)="run(a)" [attr.data-testid]="'action-' + a.id">{{ label(a) }}</button>
                }
              }
            }
          </mat-card-actions>
        </mat-card>
        @if (failure(); as text) {
          <p class="error" role="alert" data-testid="failure">{{ text }}</p>
        }
      } @else if (failure(); as text) {
        <p class="error" role="alert" data-testid="failure">{{ text }}</p>
      }
    </main>
  `,
  styleUrl: './games.scss',
  styles: `
    .game {
      padding: 16px;
      max-width: 720px;
      margin: 0 auto;
      display: flex;
      flex-direction: column;
      gap: 12px;
    }
    .stack {
      display: flex;
      flex-direction: column;
      gap: 8px;
      padding-top: 12px;
    }
    .actions {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
      padding: 8px 16px 16px;
    }
    p {
      margin: 0;
    }
  `,
})
export class GameHome {
  private readonly api = inject(GamesApi);
  private readonly router = inject(Router);
  private readonly id = inject(ActivatedRoute).snapshot.paramMap.get('id') ?? '';

  protected readonly game = signal<Game | null>(null);
  protected readonly busy = signal(false);
  protected readonly failure = signal<string | null>(null);

  protected readonly runs = computed(() => {
    const g = this.game();
    return g !== null && runsGame(g);
  });
  protected readonly actions = computed(() => {
    const g = this.game();
    return g ? actionsFor(g.state) : [];
  });
  protected readonly adminNames = computed(() => (this.game()?.admins ?? []).map((a) => a.displayName).join(', '));

  protected readonly stateLabel = stateLabel;

  constructor() {
    this.api.game(this.id).subscribe({
      next: (g) => this.game.set(g),
      error: (e) => this.failure.set(problemText(toProblem(e))),
    });
  }

  protected label(action: Action): string {
    return actionLabel(action.id, this.game()?.state ?? 'draft');
  }

  protected run(action: Action): void {
    const g = this.game();
    if (!g) {
      return;
    }
    this.busy.set(true);
    this.failure.set(null);
    const call = action.command === 'archive' ? this.api.archive(g.id) : this.api.command(g.id, action.command);
    call.subscribe({
      next: (updated) => {
        this.busy.set(false);
        if (updated.state === 'archived') {
          void this.router.navigate(['/games']); // archived games leave the lists
          return;
        }
        // the answer to a command has the admins; keep the ones we had if it does not
        this.game.set({ ...updated, admins: updated.admins ?? g.admins });
        if (action.id === 'open-lobby') {
          void this.router.navigate(['/games', g.id, 'lobby']); // the join code is shown there
        }
      },
      error: (e) => {
        this.busy.set(false);
        this.failure.set(problemText(toProblem(e)));
      },
    });
  }
}
