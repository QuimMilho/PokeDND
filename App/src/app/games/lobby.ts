import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { catchError, forkJoin, map, of, switchMap, timer } from 'rxjs';
import { problemText, toProblem } from '../core/api-error';
import { ConfirmButton } from '../shared/confirm-button';
import { runsGame, stateLabel } from './game-state';
import { Game, GamesApi, LOBBY_POLL_MS, Member } from './games-api';

/**
 * `/games/:id/lobby` (chapters 3.5 and 11.2): the join code, the requests to accept or reject, and the people who are in. The page asks
 * the API for news every few seconds; the real-time channel of phase 40 will replace that.
 */
@Component({
  selector: 'app-lobby',
  imports: [RouterLink, MatCardModule, MatButtonModule, ConfirmButton],
  template: `
    <main class="lobby">
      @if (game(); as g) {
        <header class="head">
          <h1 data-testid="game-name">{{ g.name }}</h1>
          <span class="buttons">
            @if (g.myRoles.includes('player')) {
              <a mat-flat-button [routerLink]="['/games', g.id, 'trainer']" data-testid="to-trainer" i18n="@@gameToTrainer">Your trainer</a>
            }
            <a mat-button [routerLink]="['/games', g.id]" data-testid="to-game" i18n="@@lobbyBack">Back to the game</a>
          </span>
        </header>

        <mat-card appearance="outlined">
          <mat-card-content class="code-box">
            @if (g.lobbyOpen) {
              @if (g.joinCode; as code) {
                <p class="muted" i18n="@@lobbyCodeLabel">Join code</p>
                <p class="code" data-testid="join-code">{{ code }}</p>
                <p class="muted" i18n="@@lobbyCodeHelp">Players type this code in the app. It is also shown in Minecraft.</p>
              } @else {
                <p data-testid="waiting-for-admin" i18n="@@lobbyWaiting">The lobby is open. Waiting for the admin to start the game.</p>
              }
            } @else {
              <p data-testid="lobby-closed">{{ closedText(g) }}</p>
            }
          </mat-card-content>
        </mat-card>

        @if (runs()) {
          <section aria-labelledby="requests-title">
            <h2 id="requests-title" i18n="@@lobbyRequests">Requests to join</h2>
            @if (requests().length) {
              <ul class="people" data-testid="requests">
                @for (r of requests(); track r.id) {
                  <li [attr.data-testid]="'request-' + r.displayName">
                    <span>
                      <strong>{{ r.displayName }}</strong>
                      <span class="muted"> · {{ roleLabel(r) }}</span>
                    </span>
                    <span class="buttons">
                      <button mat-flat-button type="button" (click)="answer(r, 'accept')" [attr.data-testid]="'accept-' + r.displayName" i18n="@@lobbyAccept">Accept</button>
                      <button mat-stroked-button type="button" (click)="answer(r, 'reject')" [attr.data-testid]="'reject-' + r.displayName" i18n="@@lobbyReject">Reject</button>
                    </span>
                  </li>
                }
              </ul>
            } @else {
              <p class="muted" data-testid="no-requests" i18n="@@lobbyNoRequests">Nobody is waiting.</p>
            }
          </section>
        }

        <section aria-labelledby="members-title">
          <h2 id="members-title" i18n="@@lobbyMembers">In the game</h2>
          <ul class="people" data-testid="members">
            @for (m of members(); track m.id) {
              <li [attr.data-testid]="'member-' + m.displayName">
                <span>
                  <strong>{{ m.displayName }}</strong>
                  <span class="muted"> · {{ roleLabel(m) }}</span>
                </span>
                @if (runs()) {
                  <app-confirm-button [label]="kickLabel" [testId]="'kick-' + m.displayName" (confirmed)="kick(m)" />
                }
              </li>
            } @empty {
              <li class="muted" data-testid="no-members" i18n="@@lobbyNoMembers">Nobody has joined yet.</li>
            }
          </ul>
        </section>

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
    .lobby {
      padding: 16px;
      max-width: 720px;
      margin: 0 auto;
      display: flex;
      flex-direction: column;
      gap: 12px;
    }
    .head {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: space-between;
      gap: 8px;
    }
    h1 {
      margin: 0;
      font: var(--mat-sys-headline-medium);
    }
    h2 {
      margin: 0 0 8px;
      font: var(--mat-sys-title-medium);
    }
    p {
      margin: 0;
    }
    .code-box {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 4px;
      padding-top: 16px;
      text-align: center;
    }
    .code {
      font: var(--mat-sys-display-small);
      letter-spacing: 0.2em;
    }
    .people {
      list-style: none;
      margin: 0;
      padding: 0;
      display: flex;
      flex-direction: column;
      gap: 8px;

      li {
        display: flex;
        flex-wrap: wrap;
        gap: 8px;
        align-items: center;
        justify-content: space-between;
      }
    }
    .buttons {
      display: inline-flex;
      gap: 8px;
    }
  `,
})
export class Lobby {
  private readonly api = inject(GamesApi);
  private readonly id = inject(ActivatedRoute).snapshot.paramMap.get('id') ?? '';
  private readonly pollMs = inject(LOBBY_POLL_MS);

  protected readonly game = signal<Game | null>(null);
  protected readonly members = signal<Member[]>([]);
  protected readonly requests = signal<Member[]>([]);
  protected readonly failure = signal<string | null>(null);
  protected readonly runs = computed(() => {
    const g = this.game();
    return g !== null && runsGame(g);
  });

  protected readonly kickLabel = $localize`:@@lobbyKick:Remove`;

  constructor() {
    // ask for news now and then; a call that fails is tried again at the next turn, and the page keeps what it has
    timer(0, this.pollMs)
      .pipe(
        switchMap(() =>
          this.api.game(this.id).pipe(
            map((game) => ({ game, error: null as unknown })),
            catchError((error: unknown) => of({ game: null, error })),
          ),
        ),
        takeUntilDestroyed(inject(DestroyRef)),
      )
      .subscribe(({ game, error }) => {
        if (!game) {
          this.failure.set(problemText(toProblem(error)));
          return;
        }
        this.failure.set(null);
        this.game.set(game);
        this.refreshPeople(game);
      });
  }

  private refreshPeople(game: Game): void {
    forkJoin({
      members: this.api.members(game.id).pipe(catchError(() => of(null))),
      requests: runsGame(game) ? this.api.requests(game.id).pipe(catchError(() => of(null))) : of([] as Member[]),
    }).subscribe(({ members, requests }) => {
      if (members) {
        this.members.set(members);
      }
      if (requests) {
        this.requests.set(requests);
      }
    });
  }

  protected closedText(game: Game): string {
    switch (game.state) {
      case 'draft':
        return $localize`:@@lobbyDraft:The lobby is not open yet. The admin opens it when the game is ready.`;
      case 'ended':
      case 'archived':
        return $localize`:@@lobbyOver:This game is over (${stateLabel(game.state)}:state:).`;
      default:
        return $localize`:@@lobbyClosed:The lobby is closed. The game is ${stateLabel(game.state)}:state:.`;
    }
  }

  protected roleLabel(m: Member): string {
    return m.role === 'spectator' ? $localize`:@@roleSpectator:Spectator` : $localize`:@@rolePlayer:Player`;
  }

  protected answer(request: Member, answer: 'accept' | 'reject'): void {
    this.failure.set(null);
    this.api.answer(this.id, request.id, answer).subscribe({
      next: (member) => {
        this.requests.update((list) => list.filter((r) => r.id !== request.id));
        if (answer === 'accept') {
          this.members.update((list) => [...list, member]);
        }
      },
      error: (e) => {
        this.failure.set(problemText(toProblem(e)));
        this.refreshPeople(this.game()!);
      },
    });
  }

  protected kick(member: Member): void {
    this.failure.set(null);
    this.api.kick(this.id, member.userId).subscribe({
      next: () => this.members.update((list) => list.filter((m) => m.id !== member.id)),
      error: (e) => this.failure.set(problemText(toProblem(e))),
    });
  }
}
