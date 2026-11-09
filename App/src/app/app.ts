import { Component, LOCALE_ID, computed, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatToolbarModule } from '@angular/material/toolbar';
import { NavigationEnd, Router, RouterLink, RouterOutlet } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { filter, map } from 'rxjs';
import { AuthService } from './core/auth.service';
import { languageUrl } from './core/browser';
import { activeNavId, navigationFor } from './core/navigation';
import { ThemeService } from './core/theme';
import { ViewerService } from './core/viewer.service';

@Component({
  imports: [RouterOutlet, RouterLink, MatToolbarModule, MatButtonModule],
  selector: 'app-root',
  styleUrl: './app.scss',
  templateUrl: './app.html',
})
export class App {
  private readonly router = inject(Router);
  protected readonly auth = inject(AuthService);
  private readonly viewer = inject(ViewerService);

  constructor() {
    inject(ThemeService); // starts the theme at once, before any screen is drawn
  }

  /** The language this build was made for ("en" or "pt"). */
  protected readonly language = inject(LOCALE_ID).slice(0, 2);

  /** The page the person is on, so that the language links stay on it (the other language build has the same routes). */
  private readonly url = toSignal(
    this.router.events.pipe(
      filter((e) => e instanceof NavigationEnd),
      map(() => this.router.url),
    ),
    { initialValue: this.router.url },
  );

  /** The main menu for this person (chapter 11.2). */
  protected readonly menu = computed(() => navigationFor(this.viewer.viewer()));
  protected readonly activeId = computed(() => activeNavId(this.url(), this.menu()));

  protected languageLink(language: 'en' | 'pt'): string {
    return languageUrl(language, this.url());
  }

  protected logout(): void {
    this.auth.logout().subscribe();
  }
}
