import { Component, LOCALE_ID, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatToolbarModule } from '@angular/material/toolbar';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { filter, map } from 'rxjs';
import { AuthService } from './core/auth.service';

@Component({
  imports: [RouterOutlet, RouterLink, RouterLinkActive, MatToolbarModule, MatButtonModule],
  selector: 'app-root',
  styleUrl: './app.scss',
  templateUrl: './app.html',
})
export class App {
  private readonly router = inject(Router);
  protected readonly auth = inject(AuthService);

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

  protected languageLink(language: 'en' | 'pt'): string {
    const path = this.url();
    return path === '/' || path === '' ? `/${language}/` : `/${language}${path}`;
  }

  protected logout(): void {
    this.auth.logout().subscribe();
  }
}
