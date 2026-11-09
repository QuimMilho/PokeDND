import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { RouterLink } from '@angular/router';
import { AuthService } from './core/auth.service';

/** The signed-in home screen. "My games" takes its place in a later phase (chapter 11.2); for now it leads to the card tools. */
@Component({
  selector: 'app-home',
  imports: [MatCardModule, MatButtonModule, RouterLink],
  template: `
    <main class="home">
      <mat-card appearance="outlined" class="home-card">
        <mat-card-header>
          <mat-card-title i18n="@@homeTitle">Welcome to PokeDnD</mat-card-title>
          <mat-card-subtitle i18n="@@homeSubtitle">Pokémon adventures with dice, played with friends.</mat-card-subtitle>
        </mat-card-header>
        <mat-card-content>
          <p data-testid="greeting" i18n="@@homeGreeting">Hello, {{ auth.user()?.displayName }}! You are signed in.</p>
          <p i18n="@@homeCardsText">Browse the Pokémon cards, or print a whole set of them.</p>
        </mat-card-content>
        <mat-card-actions>
          <a mat-flat-button routerLink="/cards" i18n="@@homeBrowse">Browse cards</a>
          <a mat-stroked-button routerLink="/cards/export" i18n="@@homeExport">Export cards</a>
        </mat-card-actions>
      </mat-card>
    </main>
  `,
  styles: `
    .home {
      display: flex;
      justify-content: center;
      padding: 16px;
    }
    .home-card {
      width: 100%;
      max-width: 560px;
    }
    @media (min-width: 900px) {
      .home {
        padding: 48px 24px;
      }
    }
  `,
})
export class Home {
  protected readonly auth = inject(AuthService);
}
