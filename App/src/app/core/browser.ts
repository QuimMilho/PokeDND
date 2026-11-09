import { InjectionToken } from '@angular/core';

/**
 * Sends the whole browser to another address (a full page load, not a route change): to the other language build, or to Discord.
 * It is a token so that tests can watch it instead of leaving the page.
 */
export const NAVIGATE = new InjectionToken<(url: string) => void>('NAVIGATE', {
  providedIn: 'root',
  factory: () => (url: string) => window.location.assign(url),
});

/** The address of the same page in the build of another language: the builds have the same routes (`/en/cards`, `/pt/cards`). */
export function languageUrl(language: 'en' | 'pt', path: string): string {
  return path === '/' || path === '' ? `/${language}/` : `/${language}${path}`;
}
