import { Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { forkJoin } from 'rxjs';
import { problemText, toProblem } from '../core/api-error';
import { AuthService } from '../core/auth.service';
import { NAVIGATE } from '../core/browser';
import { AccountApi } from './account-api';

/** Links or unlinks the Discord login of this account (Discord is the only outside provider; chapter 3.2). */
@Component({
  selector: 'app-discord',
  imports: [MatCardModule, MatButtonModule],
  template: `
    @if (shown()) {
      <mat-card appearance="outlined">
        <mat-card-header>
          <mat-card-title i18n="@@discordTitle">Discord</mat-card-title>
          <mat-card-subtitle i18n="@@discordSubtitle">Use Discord as another way to log in.</mat-card-subtitle>
        </mat-card-header>
        <mat-card-content class="stack">
          @if (linked()) {
            <p data-testid="discord-linked" i18n="@@discordIsLinked">Your Discord account is linked.</p>
            <div>
              <button mat-stroked-button type="button" (click)="unlink()" data-testid="discord-unlink" i18n="@@discordUnlink">Unlink Discord</button>
            </div>
          } @else {
            <p class="muted" data-testid="discord-not-linked" i18n="@@discordIsNotLinked">Discord is not linked.</p>
            <div>
              <button mat-flat-button type="button" (click)="link()" data-testid="discord-link" i18n="@@discordLink">Link Discord</button>
            </div>
          }
          @if (failure(); as text) {
            <p class="error" role="alert" data-testid="failure">{{ text }}</p>
          }
        </mat-card-content>
      </mat-card>
    }
  `,
  styleUrl: './cards.scss',
})
export class Discord {
  private readonly api = inject(AccountApi);
  private readonly auth = inject(AuthService);
  private readonly navigate = inject(NAVIGATE);

  protected readonly shown = signal(false);
  protected readonly linked = signal(false);
  protected readonly failure = signal<string | null>(null);

  constructor() {
    forkJoin({ providers: this.auth.providers(), methods: this.api.loginMethods() }).subscribe({
      next: ({ providers, methods }) => {
        const has = methods.some((m) => m.provider === 'discord');
        this.linked.set(has);
        // the card is useful when Discord is set up on this site, or when it is linked already (so it can be unlinked)
        this.shown.set(providers.includes('discord') || has);
      },
      error: () => this.shown.set(false),
    });
  }

  protected link(): void {
    this.failure.set(null);
    this.api.discordLinkUrl().subscribe({
      next: ({ url }) => this.navigate(url),
      error: (e) => this.failure.set(problemText(toProblem(e))),
    });
  }

  protected unlink(): void {
    this.failure.set(null);
    this.api.unlinkDiscord().subscribe({
      next: () => this.linked.set(false),
      error: (e) => this.failure.set(problemText(toProblem(e))),
    });
  }
}
