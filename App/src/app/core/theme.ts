import { DOCUMENT } from '@angular/common';
import { Injectable, effect, inject, signal } from '@angular/core';
import { AuthService } from './auth.service';

export type Theme = 'system' | 'light' | 'dark';

/** The three themes of `app.theme.default` (chapter 11.8). */
export const THEMES: readonly Theme[] = ['system', 'light', 'dark'];

const STORAGE_KEY = 'pokednd.theme';

export function isTheme(value: unknown): value is Theme {
  return typeof value === 'string' && (THEMES as readonly string[]).includes(value);
}

function stored(): Theme | null {
  try {
    const value = localStorage.getItem(STORAGE_KEY);
    return isTheme(value) ? value : null;
  } catch {
    return null; // storage can be blocked; the theme then lasts only until the page closes
  }
}

/**
 * The colour theme: `system` follows the device, `light` and `dark` force one. It is written on the page as `data-theme` on `<html>`
 * (the style sheet turns that into `color-scheme`). A copy is kept in the browser so the page has the right colours from the first
 * moment, and the choice is saved on the account so it follows the person to other devices.
 */
@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly html = inject(DOCUMENT).documentElement;
  private readonly auth = inject(AuthService);

  private readonly current = signal<Theme>(stored() ?? 'system');
  readonly theme = this.current.asReadonly();

  constructor() {
    this.apply(this.current());
    // when someone signs in, their saved theme wins over the copy in the browser
    effect(() => {
      const saved = this.auth.user()?.theme;
      if (isTheme(saved)) {
        this.show(saved);
      }
    });
  }

  /** The person chose a theme: show it, keep it in the browser, and save it on the account when signed in. */
  choose(theme: Theme): void {
    this.show(theme);
    if (this.auth.signedIn()) {
      this.auth.updateProfile({ theme }).subscribe({ error: () => undefined }); // the theme is shown either way
    }
  }

  private show(theme: Theme): void {
    this.current.set(theme);
    try {
      localStorage.setItem(STORAGE_KEY, theme);
    } catch {
      // see stored()
    }
    this.apply(theme);
  }

  private apply(theme: Theme): void {
    this.html.setAttribute('data-theme', theme);
  }
}
