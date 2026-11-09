import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component, Type } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { flushUi, nextRequest, press, sleep } from '../../testing/ui';
import { GameHome } from './game-home';
import { Games } from './games';
import { Game, LOBBY_POLL_MS } from './games-api';
import { Lobby } from './lobby';
import { NewGame } from './new-game';

@Component({ selector: 'app-blank-games', template: '' })
class Blank {}

const USER = { id: 'u1', email: 'anna@example.test', emailVerified: true, displayName: 'Anna', locale: 'en', theme: 'system', siteRole: 'none' };

function game(extra: Partial<Game> = {}): Game {
  return {
    id: 'g1',
    serverId: 's1',
    name: 'Kanto Weekend',
    state: 'draft',
    lobbyOpen: false,
    ownerId: 'u1',
    myRoles: ['server_admin', 'game_admin'],
    ruleSet: 'builtin:defaults',
    createdAt: '2026-11-09T10:00:00Z',
    admins: [{ id: 'u1', displayName: 'Anna', role: 'owner' }],
    ...extra,
  };
}

describe('Game screens', () => {
  let http: HttpTestingController;
  let router: Router;
  let fixture: ComponentFixture<unknown>;
  let root: HTMLElement;

  const has = (id: string) => root.querySelector(`[data-testid="${id}"]`) !== null;
  const text = (id: string) => root.querySelector(`[data-testid="${id}"]`)?.textContent?.trim();
  const settle = () => flushUi(fixture);

  interface MountOptions {
    params?: Record<string, string>;
    siteRole?: string;
    /** The servers the person runs (answer of the menu service). */
    servers?: { id: string; name: string; status: string }[];
    pollMs?: number;
  }

  async function mount<T>(component: Type<T>, options: MountOptions = {}): Promise<void> {
    const { params = {}, siteRole = 'none', servers = [{ id: 's1', name: 'Survival', status: 'online' }], pollMs = 60_000 } = options;
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: 'games/:id', component: Blank },
          { path: 'games/:id/lobby', component: Blank },
          { path: 'games', component: Blank },
        ]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap(params), queryParamMap: convertToParamMap({}) } } },
        { provide: LOBBY_POLL_MS, useValue: pollMs },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    TestBed.inject(AuthService).login('anna@example.test', 'pw').subscribe();
    http.expectOne('/api/v1/auth/login').flush({ accessToken: 'T', tokenType: 'Bearer', expiresIn: 900, user: { ...USER, siteRole } });
    fixture = TestBed.createComponent(component);
    root = fixture.nativeElement;
    fixture.detectChanges();
    // the menu service asks which servers the person runs
    http.match('/api/v1/me/servers').forEach((r) => r.flush(servers));
  }

  afterEach(() => http.verify());

  describe('My games', () => {
    it('lists the games with their state and the role of the person, and links to the ones that can be opened', async () => {
      await mount(Games);
      http.expectOne('/api/v1/games').flush([
        game(),
        game({ id: 'g2', name: 'Johto Night', state: 'running', myRoles: ['player'], myMembership: 'accepted' }),
        game({ id: 'g3', name: 'Hoenn Run', state: 'lobby', lobbyOpen: true, myRoles: [], myMembership: 'requested' }),
      ]);
      await settle();
      expect(text('game-Kanto Weekend')).toContain('Draft');
      expect(text('game-Kanto Weekend')).toContain('You run this game');
      expect(text('game-Johto Night')).toContain('Running');
      expect(text('game-Johto Night')).toContain('Player');
      // a request that was not answered yet cannot be opened
      expect(root.querySelector('[data-testid="game-Hoenn Run"] a')).toBeNull();
      expect(root.querySelector('[data-testid="game-Hoenn Run"] [data-testid="waiting"]')?.textContent).toContain('Waiting for the admin to accept you');
      expect(root.querySelector('[data-testid="game-Kanto Weekend"] a')?.getAttribute('href')).toBe('/games/g1');
    });

    it('says so when the person is in no game, and offers "New game" to people who run a server', async () => {
      await mount(Games);
      http.expectOne('/api/v1/games').flush([]);
      await settle();
      expect(has('no-games')).toBeTrue();
      expect(has('new-game')).toBeTrue();
    });

    it('does not offer "New game" to an ordinary player', async () => {
      await mount(Games, { servers: [] });
      http.expectOne('/api/v1/games').flush([]);
      await settle();
      expect(has('new-game')).toBeFalse();
    });

    it('joins with a code, as a spectator if asked, and says that the admin must answer', async () => {
      await mount(Games);
      http.expectOne('/api/v1/games').flush([]);
      await settle();
      const input = root.querySelector('[data-testid="join-code"]') as HTMLInputElement;
      input.value = ' k7m2px ';
      input.dispatchEvent(new Event('input'));
      press(root, 'role-spectator');
      press(root, 'join');
      const post = http.expectOne('/api/v1/games/join');
      expect(post.request.body).toEqual({ code: 'k7m2px', role: 'spectator' });
      post.flush({ gameId: 'g9', gameName: 'Sinnoh', role: 'spectator', status: 'requested' });
      http.expectOne('/api/v1/games').flush([]); // the list is read again
      await settle();
      expect(text('joined')).toContain('Sinnoh');
    });

    it('goes to the lobby at once when the request was accepted automatically', async () => {
      await mount(Games);
      http.expectOne('/api/v1/games').flush([]);
      await settle();
      const navigate = spyOn(router, 'navigate').and.resolveTo(true);
      const input = root.querySelector('[data-testid="join-code"]') as HTMLInputElement;
      input.value = 'K7M2PX';
      input.dispatchEvent(new Event('input'));
      press(root, 'join');
      http.expectOne('/api/v1/games/join').flush({ gameId: 'g9', gameName: 'Sinnoh', role: 'player', status: 'accepted' });
      expect(navigate).toHaveBeenCalledWith(['/games', 'g9', 'lobby']);
    });

    it('explains a wrong code in words, and an empty box too', async () => {
      await mount(Games);
      http.expectOne('/api/v1/games').flush([]);
      await settle();
      press(root, 'join');
      await settle();
      expect(text('failure')).toBe('Enter the code from the lobby.');
      http.expectNone('/api/v1/games/join');

      const input = root.querySelector('[data-testid="join-code"]') as HTMLInputElement;
      input.value = 'NOPE';
      input.dispatchEvent(new Event('input'));
      press(root, 'join');
      http.expectOne('/api/v1/games/join').flush({ code: 'INVALID_JOIN_CODE', message: 'x' }, { status: 400, statusText: 'Bad Request' });
      await settle();
      expect(text('failure')).toBe('This code is not right, or the lobby is closed.');
    });
  });

  describe('New game', () => {
    it('picks the only server for the admin, names the game, and creates it as a draft', async () => {
      await mount(NewGame); // the one server answers the call of the screen
      http.expectOne('/api/v1/rulesets').flush([
        { id: 'builtin:defaults', scope: 'builtin', name: 'Defaults', description: null, editable: false },
        { id: 'builtin:casual', scope: 'builtin', name: 'Casual', description: null, editable: false },
      ]);
      await settle();
      const navigate = spyOn(router, 'navigate').and.resolveTo(true);
      const name = root.querySelector('[data-testid="name"]') as HTMLInputElement;
      name.value = ' Kanto Weekend ';
      name.dispatchEvent(new Event('input'));
      press(root, 'create');
      const post = http.expectOne('/api/v1/games');
      expect(post.request.body).toEqual({ serverId: 's1', name: 'Kanto Weekend', ruleSetId: 'builtin:defaults' });
      post.flush(game());
      expect(navigate).toHaveBeenCalledWith(['/games', 'g1']);
    });

    it('asks the site admin for all servers, not only their own', async () => {
      await mount(NewGame, { siteRole: 'siteAdmin' });
      http.expectOne('/api/v1/servers').flush([
        { id: 's1', name: 'Survival', status: 'online' },
        { id: 's2', name: 'Creative', status: 'offline' },
      ]);
      http.expectOne('/api/v1/rulesets').flush([]);
      await settle();
      expect(has('create')).toBeTrue();
    });

    it('does not create a game without a name', async () => {
      await mount(NewGame);
      http.expectOne('/api/v1/rulesets').flush([]);
      await settle();
      press(root, 'create');
      await settle();
      http.expectNone('/api/v1/games');
      expect(root.querySelector('mat-error')).not.toBeNull();
    });

    it('says what to do when the person runs no server', async () => {
      await mount(NewGame, { servers: [] });
      http.expectOne('/api/v1/rulesets').flush([]);
      await settle();
      expect(has('no-servers')).toBeTrue();
      expect(has('create')).toBeFalse();
    });

    it('explains it when the server is busy', async () => {
      await mount(NewGame);
      http.expectOne('/api/v1/rulesets').flush([]);
      await settle();
      const name = root.querySelector('[data-testid="name"]') as HTMLInputElement;
      name.value = 'X';
      name.dispatchEvent(new Event('input'));
      press(root, 'create');
      http.expectOne('/api/v1/games').flush({ code: 'SERVER_HAS_ACTIVE_GAME', message: 'x' }, { status: 409, statusText: 'Conflict' });
      await settle();
      expect(text('failure')).toContain('already has an active game');
    });
  });

  describe('Game page', () => {
    const buttons = () => Array.from(root.querySelectorAll('[data-testid^="action-"]')).map((b) => b.getAttribute('data-testid'));

    it('gives the admin the buttons of the state, and changes them when the state changes', async () => {
      await mount(GameHome, { params: { id: 'g1' } });
      http.expectOne('/api/v1/games/g1').flush(game());
      await settle();
      expect(text('game-state')).toBe('Draft');
      expect(buttons()).toEqual(['action-open-lobby', 'action-archive']);
      expect(has('to-settings')).toBeTrue();

      const navigate = spyOn(router, 'navigate').and.resolveTo(true);
      press(root, 'action-open-lobby');
      const post = http.expectOne('/api/v1/games/g1/lobby/open');
      expect(post.request.method).toBe('POST');
      post.flush(game({ state: 'lobby', lobbyOpen: true, joinCode: 'K7M2PX', admins: undefined }));
      await settle();
      expect(navigate).toHaveBeenCalledWith(['/games', 'g1', 'lobby']);
      expect(text('game-state')).toBe('Lobby open');
      expect(buttons()).toEqual(['action-start']);
      expect(text('admins')).toContain('Anna'); // kept from the first answer
    });

    it('walks a game through start, pause, resume and end, asking twice before the end', async () => {
      await mount(GameHome, { params: { id: 'g1' } });
      http.expectOne('/api/v1/games/g1').flush(game({ state: 'lobby', lobbyOpen: true }));
      await settle();
      press(root, 'action-start');
      http.expectOne('/api/v1/games/g1/start').flush(game({ state: 'running' }));
      await settle();
      expect(buttons()).toEqual(['action-pause', 'action-open-lobby', 'action-end']);
      press(root, 'action-pause');
      http.expectOne('/api/v1/games/g1/pause').flush(game({ state: 'paused' }));
      await settle();
      expect(buttons()).toEqual(['action-resume', 'action-end']);
      press(root, 'action-resume');
      http.expectOne('/api/v1/games/g1/resume').flush(game({ state: 'running' }));
      await settle();

      press(root, 'action-end'); // the first tap only asks
      await settle();
      http.expectNone('/api/v1/games/g1/end');
      press(root, 'action-end-yes');
      http.expectOne('/api/v1/games/g1/end').flush(game({ state: 'ended' }));
      await settle();
      expect(text('game-state')).toBe('Ended');
      expect(buttons()).toEqual(['action-archive']);
    });

    it('discards a draft with DELETE and goes back to the list', async () => {
      await mount(GameHome, { params: { id: 'g1' } });
      http.expectOne('/api/v1/games/g1').flush(game());
      await settle();
      const navigate = spyOn(router, 'navigate').and.resolveTo(true);
      press(root, 'action-archive');
      await settle();
      press(root, 'action-archive-yes');
      const del = http.expectOne('/api/v1/games/g1');
      expect(del.request.method).toBe('DELETE');
      del.flush(game({ state: 'archived' }));
      expect(navigate).toHaveBeenCalledWith(['/games']);
    });

    it('shows a player the state and the lobby, and no admin buttons', async () => {
      await mount(GameHome, { params: { id: 'g1' } });
      http.expectOne('/api/v1/games/g1').flush(game({ state: 'lobby', lobbyOpen: true, myRoles: ['player'] }));
      await settle();
      expect(buttons()).toEqual([]);
      expect(has('to-settings')).toBeFalse();
      expect(has('to-lobby')).toBeTrue();
    });

    it('explains it when the game does not exist or cannot be seen', async () => {
      await mount(GameHome, { params: { id: 'nope' } });
      http.expectOne('/api/v1/games/nope').flush({ code: 'NOT_FOUND', message: 'x' }, { status: 404, statusText: 'Not Found' });
      await settle();
      expect(text('failure')).toBe('This does not exist, or you cannot see it.');
    });

    it('explains a step the state does not allow', async () => {
      await mount(GameHome, { params: { id: 'g1' } });
      http.expectOne('/api/v1/games/g1').flush(game());
      await settle();
      press(root, 'action-open-lobby');
      http.expectOne('/api/v1/games/g1/lobby/open').flush({ code: 'INVALID_TRANSITION', message: 'x' }, { status: 409, statusText: 'Conflict' });
      await settle();
      expect(text('failure')).toBe('The game cannot do that in its present state.');
    });
  });

  describe('Lobby', () => {
    const member = (id: string, name: string, role = 'player', status = 'accepted') => ({ id: 'm' + id, userId: 'u' + id, displayName: name, role, status, since: '2026-11-09T10:00:00Z' });

    /** Answers one round of the lobby's questions: the game, the members and (for an admin) the requests. */
    async function round(g: Game, members: unknown[], requests: unknown[] | null): Promise<void> {
      (await nextRequest(http, '/api/v1/games/g1')).flush(g);
      http.expectOne('/api/v1/games/g1/members').flush(members);
      if (requests) {
        http.expectOne('/api/v1/games/g1/requests').flush(requests);
      }
      await settle();
    }

    it('shows the admin the join code, who is waiting and who is in', async () => {
      await mount(Lobby, { params: { id: 'g1' } });
      await round(game({ state: 'lobby', lobbyOpen: true, joinCode: 'K7M2PX' }), [member('2', 'Bruno')], [member('3', 'Carla', 'spectator', 'requested')]);
      expect(text('join-code')).toBe('K7M2PX');
      expect(text('request-Carla')).toContain('Spectator');
      expect(text('member-Bruno')).toContain('Player');
      fixture.destroy(); // stops the polling
    });

    it('accepts a request: the person moves to the members', async () => {
      await mount(Lobby, { params: { id: 'g1' } });
      await round(game({ state: 'lobby', lobbyOpen: true, joinCode: 'K7M2PX' }), [], [member('3', 'Carla', 'player', 'requested')]);
      press(root, 'accept-Carla');
      const post = http.expectOne('/api/v1/games/g1/requests/m3/accept');
      expect(post.request.method).toBe('POST');
      post.flush(member('3', 'Carla'));
      await settle();
      expect(has('request-Carla')).toBeFalse();
      expect(has('member-Carla')).toBeTrue();
      expect(has('no-requests')).toBeTrue();
      fixture.destroy();
    });

    it('rejects a request', async () => {
      await mount(Lobby, { params: { id: 'g1' } });
      await round(game({ state: 'lobby', lobbyOpen: true, joinCode: 'K7M2PX' }), [], [member('3', 'Carla', 'player', 'requested')]);
      press(root, 'reject-Carla');
      http.expectOne('/api/v1/games/g1/requests/m3/reject').flush(member('3', 'Carla', 'player', 'rejected'));
      await settle();
      expect(has('request-Carla')).toBeFalse();
      expect(has('member-Carla')).toBeFalse();
      fixture.destroy();
    });

    it('removes a member after a second tap', async () => {
      await mount(Lobby, { params: { id: 'g1' } });
      await round(game({ state: 'lobby', lobbyOpen: true, joinCode: 'K7M2PX' }), [member('2', 'Bruno')], []);
      press(root, 'kick-Bruno');
      await settle();
      press(root, 'kick-Bruno-yes');
      const del = http.expectOne('/api/v1/games/g1/members/u2');
      expect(del.request.method).toBe('DELETE');
      del.flush(null, { status: 204, statusText: 'No Content' });
      await settle();
      expect(has('member-Bruno')).toBeFalse();
      fixture.destroy();
    });

    it('shows a player no code and no requests, only who is in', async () => {
      await mount(Lobby, { params: { id: 'g1' } });
      await round(game({ state: 'lobby', lobbyOpen: true, myRoles: ['player'] }), [member('2', 'Bruno')], null);
      expect(has('join-code')).toBeFalse();
      expect(has('waiting-for-admin')).toBeTrue();
      expect(has('request-Bruno')).toBeFalse();
      expect(has('kick-Bruno')).toBeFalse();
      expect(has('member-Bruno')).toBeTrue();
      fixture.destroy();
    });

    it('says that the lobby is closed when it is', async () => {
      await mount(Lobby, { params: { id: 'g1' } });
      await round(game({ state: 'running' }), [], []);
      expect(text('lobby-closed')).toContain('closed');
      fixture.destroy();
    });

    it('asks for news again by itself and shows a new request', async () => {
      await mount(Lobby, { params: { id: 'g1' }, pollMs: 30 });
      await round(game({ state: 'lobby', lobbyOpen: true, joinCode: 'K7M2PX' }), [], []);
      expect(has('request-Carla')).toBeFalse();
      await round(game({ state: 'lobby', lobbyOpen: true, joinCode: 'K7M2PX' }), [], [member('3', 'Carla', 'player', 'requested')]);
      expect(has('request-Carla')).toBeTrue();
      fixture.destroy();
    });
  });
});
