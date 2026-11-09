import { InjectionToken } from '@angular/core';

/** Where the API lives, from the page's own origin (the proxy in front sends `/api/*` to the API). */
export const API_BASE = new InjectionToken<string>('API_BASE', { providedIn: 'root', factory: () => '/api/v1' });
