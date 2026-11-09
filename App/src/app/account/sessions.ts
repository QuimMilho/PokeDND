import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { problemText, toProblem } from '../core/api-error';
import { AccountApi, Session } from './account-api';

/** Where the account is logged in (`GET /me/sessions`), with a button to end the others. */
@Component({
  selector: 'app-sessions',
  imports: [MatCardModule, MatButtonModule, DatePipe],
  template: `
    <mat-card appearance="outlined">
      <mat-card-header>
        <mat-card-title i18n="@@sessionsTitle">Where you are logged in</mat-card-title>
        <mat-card-subtitle i18n="@@sessionsSubtitle">End a login you do not recognise.</mat-card-subtitle>
      </mat-card-header>
      <mat-card-content class="stack">
        @if (failure(); as text) {
          <p class="error" role="alert" data-testid="failure">{{ text }}</p>
        }
        <ul class="list" data-testid="sessions">
          @for (s of sessions(); track s.id) {
            <li [attr.data-testid]="'session-' + s.id">
              <span>
                {{ s.userAgent || unknownDevice }}
                @if (s.current) {
                  <strong data-testid="this-device" i18n="@@sessionThis"> (this device)</strong>
                }
                <br />
                <span class="muted" i18n="@@sessionSince">Since {{ s.createdAt | date: 'medium' }}</span>
              </span>
              @if (!s.current) {
                <button mat-stroked-button type="button" (click)="end(s)" [attr.data-testid]="'end-' + s.id" i18n="@@sessionEnd">End this login</button>
              }
            </li>
          }
        </ul>
      </mat-card-content>
    </mat-card>
  `,
  styleUrl: './cards.scss',
})
export class Sessions {
  private readonly api = inject(AccountApi);

  protected readonly sessions = signal<Session[]>([]);
  protected readonly failure = signal<string | null>(null);
  protected readonly unknownDevice = $localize`:@@sessionUnknown:Unknown device`;

  constructor() {
    this.load();
  }

  private load(): void {
    this.api.sessions().subscribe({
      next: (list) => this.sessions.set(list),
      error: (e) => this.failure.set(problemText(toProblem(e))),
    });
  }

  protected end(session: Session): void {
    this.failure.set(null);
    this.api.endSession(session.id).subscribe({
      next: () => this.sessions.update((list) => list.filter((s) => s.id !== session.id)),
      error: (e) => this.failure.set(problemText(toProblem(e))),
    });
  }
}
