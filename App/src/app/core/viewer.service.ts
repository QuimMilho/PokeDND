import { HttpClient } from '@angular/common/http';
import { Injectable, computed, effect, inject, signal, untracked } from '@angular/core';
import { API_BASE } from './api-config';
import { AuthService } from './auth.service';
import { Viewer } from './navigation';

/** A Minecraft server, as far as the app needs it (`ServerDto` in the API). */
export interface ServerSummary {
  id: string;
  name: string;
  status: string;
}

/**
 * What kind of person is signed in: the site role of the account, plus whether they run any Minecraft server (the API's
 * `GET /me/servers`). The menu and the "New game" button depend on it.
 */
@Injectable({ providedIn: 'root' })
export class ViewerService {
  private readonly auth = inject(AuthService);
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_BASE);

  private readonly servers = signal<ServerSummary[]>([]);

  readonly viewer = computed<Viewer>(() => ({
    signedIn: this.auth.signedIn(),
    siteAdmin: this.auth.user()?.siteRole === 'siteAdmin',
    serverAdmin: this.servers().length > 0,
  }));

  constructor() {
    effect(() => {
      const id = this.auth.user()?.id;
      untracked(() => {
        if (!id) {
          this.servers.set([]);
          return;
        }
        this.http.get<ServerSummary[]>(`${this.base}/me/servers`).subscribe({
          next: (list) => this.servers.set(list),
          error: () => this.servers.set([]),
        });
      });
    });
  }
}
