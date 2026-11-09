import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE } from '../core/api-config';

/** A login of the account (`SessionDto`): the refresh token behind one browser or device. */
export interface Session {
  id: string;
  userAgent: string | null;
  createdAt: string;
  lastUsedAt: string | null;
  expiresAt: string;
  /** True for the session that made this very call. */
  current: boolean;
}

/** A Minecraft account linked to the app account (`LinkDto`). */
export interface McLink {
  mcUuid: string;
  mcName: string;
  linkedAt: string;
}

/** A code to type in Minecraft (`CodeDto`). */
export interface LinkCode {
  code: string;
  expiresAt: string;
}

/** One way to log in (`MethodDto`): `password` or `discord`. */
export interface LoginMethod {
  provider: string;
  since: string;
}

/** The calls of the account page (chapter 18.1, "Authentication and account"). */
@Injectable({ providedIn: 'root' })
export class AccountApi {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_BASE);

  sessions(): Observable<Session[]> {
    return this.http.get<Session[]>(`${this.base}/me/sessions`);
  }

  endSession(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/me/sessions/${encodeURIComponent(id)}`);
  }

  minecraftAccounts(): Observable<McLink[]> {
    return this.http.get<McLink[]>(`${this.base}/me/minecraft`);
  }

  /** The player typed the code that Minecraft printed (`/pokednd link`). */
  linkMinecraft(code: string): Observable<McLink> {
    return this.http.post<McLink>(`${this.base}/me/minecraft/link`, { code });
  }

  /** The other direction: a code that the app shows and the player types in Minecraft. */
  newLinkCode(): Observable<LinkCode> {
    return this.http.post<LinkCode>(`${this.base}/me/minecraft/link-code`, null);
  }

  unlinkMinecraft(uuid: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/me/minecraft/${encodeURIComponent(uuid)}`);
  }

  loginMethods(): Observable<LoginMethod[]> {
    return this.http.get<LoginMethod[]>(`${this.base}/me/identities`);
  }

  /** The address at Discord to send the browser to; the API's answer also sets the cookie that protects the redirect. */
  discordLinkUrl(): Observable<{ url: string }> {
    return this.http.post<{ url: string }>(`${this.base}/me/identities/discord/link`, null);
  }

  unlinkDiscord(): Observable<void> {
    return this.http.delete<void>(`${this.base}/me/identities/discord`);
  }
}
