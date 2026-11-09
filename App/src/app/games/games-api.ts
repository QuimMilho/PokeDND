import { HttpClient } from '@angular/common/http';
import { Injectable, InjectionToken, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE } from '../core/api-config';
import { ServerSummary } from '../core/viewer.service';

export type GameStateId = 'draft' | 'lobby' | 'running' | 'paused' | 'ended' | 'archived';

/** A person who runs the game (`AdminDto`). */
export interface GameAdmin {
  id: string;
  displayName: string;
  role: string;
}

/** A game, as the API shows it (`GameDto`). `joinCode` is there only for people who run the game, and only while the lobby is open. */
export interface Game {
  id: string;
  serverId: string;
  name: string;
  state: GameStateId;
  lobbyOpen: boolean;
  joinCode?: string;
  ownerId: string;
  /** For example `game_admin`, `server_admin`, `site_admin`, `player`, `spectator`. */
  myRoles: string[];
  /** `requested`, `accepted`, `rejected` or `kicked`, when the person has a request or a membership. */
  myMembership?: string;
  admins?: GameAdmin[];
  ruleSet: string;
  createdAt: string;
}

/** A person in the game or asking to be (`MemberDto`). */
export interface Member {
  id: string;
  userId: string;
  displayName: string;
  role: 'player' | 'spectator';
  status: string;
  since: string;
}

/** The answer to joining: where the request stands (`JoinedDto`). */
export interface Joined {
  gameId: string;
  gameName: string;
  role: string;
  status: string;
}

/** A saved rule set or a built-in one (`RuleSetService.View`). */
export interface RuleSet {
  id: string;
  scope: string;
  name: string;
  description: string | null;
  editable: boolean;
}

export type GameCommand = 'lobby/open' | 'lobby/close' | 'start' | 'pause' | 'resume' | 'end';

/** How often the lobby asks the API for news, in milliseconds. (WebSocket events replace this in phase 40; the tests make it shorter.) */
export const LOBBY_POLL_MS = new InjectionToken<number>('LOBBY_POLL_MS', { providedIn: 'root', factory: () => 3000 });

/** The game calls of chapter 18.1 ("Games and settings") that the screens of phase 36 use. */
@Injectable({ providedIn: 'root' })
export class GamesApi {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_BASE);

  myGames(): Observable<Game[]> {
    return this.http.get<Game[]>(`${this.base}/games`);
  }

  game(id: string): Observable<Game> {
    return this.http.get<Game>(`${this.base}/games/${id}`);
  }

  create(serverId: string, name: string, ruleSetId: string | null): Observable<Game> {
    return this.http.post<Game>(`${this.base}/games`, { serverId, name, ruleSetId });
  }

  /** Opens or closes the lobby, starts, pauses, resumes or ends the game. */
  command(id: string, command: GameCommand): Observable<Game> {
    return this.http.post<Game>(`${this.base}/games/${id}/${command}`, null);
  }

  /** Archives the game: a draft is discarded, an ended game is put away. */
  archive(id: string): Observable<Game> {
    return this.http.delete<Game>(`${this.base}/games/${id}`);
  }

  join(code: string, role: 'player' | 'spectator'): Observable<Joined> {
    return this.http.post<Joined>(`${this.base}/games/join`, { code, role });
  }

  requests(id: string): Observable<Member[]> {
    return this.http.get<Member[]>(`${this.base}/games/${id}/requests`);
  }

  answer(id: string, requestId: string, answer: 'accept' | 'reject'): Observable<Member> {
    return this.http.post<Member>(`${this.base}/games/${id}/requests/${requestId}/${answer}`, null);
  }

  members(id: string): Observable<Member[]> {
    return this.http.get<Member[]>(`${this.base}/games/${id}/members`);
  }

  kick(id: string, userId: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/games/${id}/members/${userId}`);
  }

  /** The servers a new game can be put on: all of them for the site admin, the ones the person runs for a server admin. */
  serversForNewGame(siteAdmin: boolean): Observable<ServerSummary[]> {
    return this.http.get<ServerSummary[]>(siteAdmin ? `${this.base}/servers` : `${this.base}/me/servers`);
  }

  ruleSets(): Observable<RuleSet[]> {
    return this.http.get<RuleSet[]>(`${this.base}/rulesets`);
  }
}
