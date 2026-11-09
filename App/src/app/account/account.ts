import { Component, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { oauthText } from '../core/api-error';
import { Discord } from './discord';
import { Minecraft } from './minecraft';
import { Preferences } from './preferences';
import { Sessions } from './sessions';

/** `/account` (chapter 11.2): profile, language, theme, sessions, linked Minecraft accounts and the Discord link. */
@Component({
  selector: 'app-account',
  imports: [Preferences, Sessions, Minecraft, Discord],
  template: `
    <main class="account">
      <h1 i18n="@@accountTitle">Your account</h1>
      @if (notice(); as text) {
        <p class="notice" role="status" data-testid="oauth-notice">{{ text }}</p>
      }
      <app-preferences />
      <app-minecraft />
      <app-discord />
      <app-sessions />
    </main>
  `,
  styles: `
    .account {
      display: flex;
      flex-direction: column;
      gap: 16px;
      padding: 16px;
      max-width: 640px;
      margin: 0 auto;
    }
    h1 {
      margin: 0;
      font: var(--mat-sys-headline-medium);
    }
    .notice {
      margin: 0;
      padding: 12px 16px;
      border-radius: 8px;
      background: var(--mat-sys-secondary-container);
      color: var(--mat-sys-on-secondary-container);
    }
  `,
})
export class Account {
  private readonly query = inject(ActivatedRoute).snapshot.queryParamMap;

  /** After the round trip to Discord the API sends the browser back here with `?oauth=linked` or `?oauth=error&code=…`. */
  protected readonly notice = () => oauthText(this.query.get('oauth'), this.query.get('code'));
}
