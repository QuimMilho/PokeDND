import { Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { Router, RouterLink } from '@angular/router';
import { problemText, toProblem } from '../core/api-error';
import { ViewerService } from '../core/viewer.service';
import { membershipLabel, runsGame, stateLabel } from './game-state';
import { Game, GamesApi } from './games-api';

/** `/games` (chapter 11.2): my games, and joining a game with the code from its lobby. */
@Component({
  selector: 'app-games',
  imports: [ReactiveFormsModule, RouterLink, MatCardModule, MatButtonModule, MatButtonToggleModule, MatFormFieldModule, MatInputModule],
  templateUrl: './games.html',
  styleUrl: './games.scss',
})
export class Games {
  private readonly api = inject(GamesApi);
  private readonly router = inject(Router);
  private readonly viewer = inject(ViewerService);

  protected readonly games = signal<Game[] | null>(null);
  protected readonly canCreate = computed(() => this.viewer.viewer().siteAdmin || this.viewer.viewer().serverAdmin);

  protected readonly form = inject(FormBuilder).nonNullable.group({
    code: ['', [Validators.required, Validators.pattern(/\S/)]],
    role: ['player' as 'player' | 'spectator'],
  });
  protected readonly joined = signal<string | null>(null);
  protected readonly failure = signal<string | null>(null);
  protected readonly listFailure = signal<string | null>(null);

  protected readonly stateLabel = stateLabel;

  constructor() {
    this.load();
  }

  private load(): void {
    this.api.myGames().subscribe({
      next: (list) => this.games.set(list),
      error: (e) => {
        this.games.set([]);
        this.listFailure.set(problemText(toProblem(e)));
      },
    });
  }

  /** Can the person open the game page? Not while they have only asked to join. */
  protected open(game: Game): boolean {
    return game.myRoles.length > 0;
  }

  protected waiting(game: Game): string | null {
    return this.open(game) ? null : membershipLabel(game.myMembership);
  }

  /** The strongest role of the person in the game, in words. */
  protected role(game: Game): string {
    if (runsGame(game)) {
      return $localize`:@@roleAdmin:You run this game`;
    }
    if (game.myRoles.includes('player')) {
      return $localize`:@@rolePlayer:Player`;
    }
    if (game.myRoles.includes('spectator')) {
      return $localize`:@@roleSpectator:Spectator`;
    }
    return $localize`:@@roleServerAdmin:Server admin`;
  }

  protected join(): void {
    this.joined.set(null);
    this.failure.set(null);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.failure.set($localize`:@@errJoinCodeRequired:Enter the code from the lobby.`);
      return;
    }
    const { code, role } = this.form.getRawValue();
    this.api.join(code.trim(), role).subscribe({
      next: (joined) => {
        this.form.controls.code.reset();
        if (joined.status === 'accepted') {
          void this.router.navigate(['/games', joined.gameId, 'lobby']);
          return;
        }
        this.joined.set(joined.gameName);
        this.load();
      },
      error: (e) => this.failure.set(problemText(toProblem(e))),
    });
  }
}
