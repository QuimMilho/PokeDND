import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { Router } from '@angular/router';
import { forkJoin } from 'rxjs';
import { problemText, toProblem } from '../core/api-error';
import { AuthService } from '../core/auth.service';
import { ServerSummary } from '../core/viewer.service';
import { GamesApi, RuleSet } from './games-api';

/**
 * `/games/new` (chapter 12.1): "New game → pick the server → name it → Open lobby". The game starts as a draft; the starting point
 * is the plain defaults, or a preset or saved rule set.
 */
@Component({
  selector: 'app-new-game',
  imports: [ReactiveFormsModule, MatCardModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatSelectModule],
  template: `
    <main class="new-game">
      <mat-card appearance="outlined">
        <mat-card-header>
          <mat-card-title i18n="@@newGameTitle">New game</mat-card-title>
          <mat-card-subtitle i18n="@@newGameSubtitle">Everything can be changed later, also during the game.</mat-card-subtitle>
        </mat-card-header>
        <mat-card-content>
          @if (servers(); as list) {
            @if (!list.length) {
              <p class="error" data-testid="no-servers" i18n="@@newGameNoServers">There is no Minecraft server you can run a game on. Ask the site admin to make you an admin of one.</p>
            } @else {
              <form [formGroup]="form" (ngSubmit)="create()" novalidate class="form">
                <mat-form-field appearance="outline">
                  <mat-label i18n="@@newGameServer">Minecraft server</mat-label>
                  <mat-select formControlName="serverId" data-testid="server">
                    @for (s of list; track s.id) {
                      <mat-option [value]="s.id" [attr.data-testid]="'server-' + s.name">{{ s.name }}</mat-option>
                    }
                  </mat-select>
                </mat-form-field>
                <mat-form-field appearance="outline">
                  <mat-label i18n="@@newGameName">Name of the game</mat-label>
                  <input matInput formControlName="name" maxlength="80" autocomplete="off" data-testid="name" />
                  @if (form.controls.name.touched && form.controls.name.invalid) {
                    <mat-error i18n="@@newGameNameRequired">Give the game a name.</mat-error>
                  }
                </mat-form-field>
                <mat-form-field appearance="outline">
                  <mat-label i18n="@@newGamePreset">Starting point</mat-label>
                  <mat-select formControlName="ruleSetId" data-testid="preset">
                    @for (r of ruleSets(); track r.id) {
                      <mat-option [value]="r.id" [attr.data-testid]="'preset-' + r.id">{{ r.name }}</mat-option>
                    }
                  </mat-select>
                  <mat-hint i18n="@@newGamePresetHint">A preset or a saved rule set. “Defaults” changes nothing.</mat-hint>
                </mat-form-field>
                @if (failure(); as text) {
                  <p class="error" role="alert" data-testid="failure">{{ text }}</p>
                }
                <div>
                  <button mat-flat-button type="submit" [disabled]="busy()" data-testid="create" i18n="@@newGameCreate">Create the game</button>
                </div>
              </form>
            }
          }
        </mat-card-content>
      </mat-card>
    </main>
  `,
  styleUrl: './games.scss',
  styles: `
    .new-game {
      padding: 16px;
      max-width: 520px;
      margin: 0 auto;
    }
    .form {
      display: flex;
      flex-direction: column;
      gap: 12px;
      padding-top: 16px;
    }
  `,
})
export class NewGame {
  private readonly api = inject(GamesApi);
  private readonly router = inject(Router);

  protected readonly servers = signal<ServerSummary[] | null>(null);
  protected readonly ruleSets = signal<RuleSet[]>([]);
  protected readonly busy = signal(false);
  protected readonly failure = signal<string | null>(null);

  protected readonly form = inject(FormBuilder).nonNullable.group({
    serverId: ['', Validators.required],
    name: ['', [Validators.required, Validators.pattern(/\S/), Validators.maxLength(80)]],
    ruleSetId: ['builtin:defaults'],
  });

  constructor() {
    const siteAdmin = inject(AuthService).user()?.siteRole === 'siteAdmin';
    forkJoin({ servers: this.api.serversForNewGame(siteAdmin), ruleSets: this.api.ruleSets() }).subscribe({
      next: ({ servers, ruleSets }) => {
        this.servers.set(servers);
        this.ruleSets.set(ruleSets);
        if (servers.length === 1) {
          this.form.controls.serverId.setValue(servers[0].id); // nothing to choose
        }
      },
      error: (e) => {
        this.servers.set([]);
        this.failure.set(problemText(toProblem(e)));
      },
    });
  }

  protected create(): void {
    this.failure.set(null);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.busy.set(true);
    const { serverId, name, ruleSetId } = this.form.getRawValue();
    this.api.create(serverId, name.trim(), ruleSetId).subscribe({
      next: (game) => void this.router.navigate(['/games', game.id]),
      error: (e) => {
        this.busy.set(false);
        this.failure.set(problemText(toProblem(e)));
      },
    });
  }
}
