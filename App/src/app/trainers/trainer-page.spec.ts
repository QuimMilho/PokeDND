import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { classes, game, options, trainer } from '../../testing/trainers';
import { useEnglish, usePortuguese } from '../../testing/translations';
import { flushUi, press } from '../../testing/ui';
import { AuthService } from '../core/auth.service';
import { Game } from '../games/games-api';
import { TrainerPage } from './trainer-page';
import { Trainer } from './trainers-api';

@Component({ selector: 'app-blank-page', template: '' })
class Blank {}

const USER = { id: 'u1', email: 'anna@example.test', emailVerified: true, displayName: 'Anna', locale: 'en', theme: 'system', siteRole: 'none' };

describe('Trainer page', () => {
  let http: HttpTestingController;
  let router: Router;
  let navigate: jasmine.Spy;
  let fixture: ComponentFixture<TrainerPage>;
  let root: HTMLElement;

  const q = (id: string) => root.querySelector<HTMLElement>(`[data-testid="${id}"]`);
  const text = (id: string) => q(id)?.textContent?.trim();
  const settle = () => flushUi(fixture);

  async function mount(g: Game = game(), trainers: Trainer[] = [trainer()], skinBlob: Blob | null = new Blob(['png'], { type: 'image/png' })): Promise<void> {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: '**', component: Blank }]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ id: 'g1' }) } } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    navigate = spyOn(router, 'navigate').and.resolveTo(true);
    TestBed.inject(AuthService).login('anna@example.test', 'pw').subscribe();
    http.expectOne('/api/v1/auth/login').flush({ accessToken: 'T', tokenType: 'Bearer', expiresIn: 900, user: USER });
    fixture = TestBed.createComponent(TrainerPage);
    root = fixture.nativeElement;
    fixture.detectChanges();
    http.expectOne('/api/v1/games/g1').flush(g);
    http.expectOne('/api/v1/games/g1/trainer-options').flush(options());
    http.expectOne('/api/v1/games/g1/trainers').flush(trainers);
    await settle();
    if (skinBlob && trainers.some((t) => t.userId === 'u1' && t.skin)) {
      http.expectOne('/api/v1/trainers/t1/skin').flush(skinBlob);
      await settle();
    }
  }

  afterEach(() => {
    http.verify();
    useEnglish();
  });

  it('shows the sheet: vitals, abilities with modifiers and saves, skills, features and the bag', async () => {
    await mount();
    expect(text('trainer-name')).toBe('Ash_K');
    expect(text('trainer-class')).toContain('Ace Trainer');
    expect(text('trainer-class')).toContain('Level 1');
    expect(text('hp')).toBe('9 / 9');
    expect(text('ac')).toBe('12');
    expect(text('proficiency')).toBe('+2');
    expect(text('handling')).toBe('+2');
    expect(text('xp')).toBe('0 / 50');
    expect(text('money')).toBe('₽ 3000');
    expect(text('starter')).toContain('Charmander');
    expect(q('row-dex')?.textContent).toContain('Dexterity');
    expect(q('row-dex')?.textContent).toContain('14');
    expect(q('row-dex')?.textContent).toContain('+2');
    expect(q('row-dex')?.textContent).toContain('+4');
    expect(q('row-cha')?.textContent).toContain('−1');
    expect(q('skills')?.textContent).toContain('Pokémon Handling +2');
    expect(q('skills')?.textContent).toContain('(proficient)');
    expect(text('features')).toContain('Commanding Presence');
    expect(text('bag')).toContain('5 × poke_ball');
    expect(q('others')).toBeNull();
  });

  it('shows the texts the page builds in Portuguese (the texts of the template are checked by the catalogue test)', async () => {
    usePortuguese();
    await mount(game(), [trainer({ skin: { model: 'slim', source: 'username', username: 'AlexFan', sha256: 'abc', updatedAt: '2026-11-09T10:00:00Z' } })]);
    expect(text('skin-info')).toBe('Copiada de AlexFan, braços finos');
    expect(text('remove')).toBe('Remover o treinador');
  });

  it('can change or remove the trainer while the game has not started', async () => {
    await mount(game({ state: 'lobby' }));
    expect(q('edit')?.getAttribute('href')).toBe('/games/g1/trainer/edit');
    expect(q('remove')).not.toBeNull();
    expect(q('locked')).toBeNull();
  });

  it('says the trainer is locked once the game is running', async () => {
    await mount(game({ state: 'running' }));
    expect(q('edit')).toBeNull();
    expect(text('locked')).toBe('The game has started, so only the admin can change this trainer now.');
  });

  it('lets the admin change a trainer after the start', async () => {
    await mount(game({ state: 'running', myRoles: ['player', 'game_admin'] }));
    expect(q('edit')).not.toBeNull();
  });

  it('removes the trainer after a second tap and goes to the wizard', async () => {
    await mount();
    press(root, 'remove');
    await settle();
    expect(http.match('/api/v1/trainers/t1').length).toBe(0);
    press(root, 'remove-yes');
    await settle();
    const del = http.expectOne('/api/v1/trainers/t1');
    expect(del.request.method).toBe('DELETE');
    del.flush(null, { status: 204, statusText: 'No Content' });
    await settle();
    expect(navigate).toHaveBeenCalledWith(['/games', 'g1', 'trainer', 'create']);
  });

  it('shows the skin and how it was made', async () => {
    await mount(game(), [trainer({ skin: { model: 'slim', source: 'username', username: 'AlexFan', sha256: 'abc', updatedAt: '2026-11-09T10:00:00Z' } })]);
    expect(q('skin-image')?.getAttribute('src')).toMatch(/^blob:/);
    expect(text('skin-info')).toBe('Copied from AlexFan, slim arms');
    expect(q('no-skin')).toBeNull();
  });

  it('says there is no skin yet', async () => {
    await mount();
    expect(q('no-skin')).not.toBeNull();
    expect(q('skin-image')).toBeNull();
  });

  it('copies a skin from a Minecraft name and shows the new one', async () => {
    await mount();
    const radio = q('skin-mode-username')!.querySelector('input') as HTMLInputElement;
    radio.click();
    await settle();
    const name = q('skin-username') as HTMLInputElement;
    name.value = 'SteveFan';
    name.dispatchEvent(new Event('input'));
    await settle();
    press(root, 'save-skin');
    const put = http.expectOne('/api/v1/trainers/t1/skin');
    expect(put.request.body).toEqual({ username: 'SteveFan' });
    put.flush(trainer({ skin: { model: 'classic', source: 'username', username: 'SteveFan', sha256: 'abc', updatedAt: '2026-11-09T10:00:00Z' } }));
    await settle();
    http.expectOne('/api/v1/trainers/t1/skin').flush(new Blob(['png'], { type: 'image/png' }));
    await settle();
    expect(q('skin-image')).not.toBeNull();
  });

  it('shows why a skin was refused', async () => {
    await mount();
    (q('skin-mode-username')!.querySelector('input') as HTMLInputElement).click();
    await settle();
    const name = q('skin-username') as HTMLInputElement;
    name.value = 'NobodyHere';
    name.dispatchEvent(new Event('input'));
    await settle();
    press(root, 'save-skin');
    http.expectOne('/api/v1/trainers/t1/skin').flush({ code: 'MINECRAFT_USER_NOT_FOUND', message: 'x' }, { status: 404, statusText: 'Not Found' });
    await settle();
    expect(text('skin-failure')).toBe('There is no Minecraft account with that name.');
  });

  it('removes the skin after a second tap', async () => {
    await mount(game(), [trainer({ skin: { model: 'classic', source: 'upload', sha256: 'abc', updatedAt: '2026-11-09T10:00:00Z' } })]);
    press(root, 'remove-skin');
    await settle();
    press(root, 'remove-skin-yes');
    await settle();
    const del = http.expectOne('/api/v1/trainers/t1/skin');
    expect(del.request.method).toBe('DELETE');
    del.flush(trainer());
    await settle();
    expect(q('no-skin')).not.toBeNull();
  });

  it('lists the other trainers of the game', async () => {
    await mount(game(), [trainer(), trainer({ id: 't2', userId: 'u2', ownerName: 'Bob', name: 'Brock', className: 'Hiker', classId: 'hiker', level: 3 })]);
    expect(text('other-Brock')).toContain('Brock');
    expect(text('other-Brock')).toContain('Hiker');
    expect(text('other-Brock')).toContain('Level 3');
    expect(text('other-Brock')).toContain('Bob');
  });

  it('says so when the class of the trainer is not in the game any more', async () => {
    await mount(game(), [trainer({ sheet: undefined })]);
    expect(q('no-sheet')).not.toBeNull();
    expect(q('abilities')).toBeNull();
  });

  it('sends a player who has no trainer to the wizard', async () => {
    await mount(game(), []);
    expect(navigate).toHaveBeenCalledWith(['/games', 'g1', 'trainer', 'create']);
  });

  it('does not send a spectator anywhere', async () => {
    await mount(game({ myRoles: ['spectator'] }), [trainer({ userId: 'u2', ownerName: 'Bob', name: 'Brock' })]);
    expect(navigate).not.toHaveBeenCalled();
    expect(q('trainer-name')).toBeNull();
    expect(text('others')).toContain('Brock');
  });

  it('shows an error when the game cannot be read', async () => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: '**', component: Blank }]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ id: 'g1' }) } } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    TestBed.inject(AuthService).login('anna@example.test', 'pw').subscribe();
    http.expectOne('/api/v1/auth/login').flush({ accessToken: 'T', tokenType: 'Bearer', expiresIn: 900, user: USER });
    fixture = TestBed.createComponent(TrainerPage);
    root = fixture.nativeElement;
    fixture.detectChanges();
    http.expectOne('/api/v1/games/g1').flush({ code: 'NOT_FOUND', message: 'x' }, { status: 404, statusText: 'Not Found' });
    http.expectOne('/api/v1/games/g1/trainer-options');
    http.expectOne('/api/v1/games/g1/trainers');
    await settle();
    expect(text('failure')).toBe('This does not exist, or you cannot see it.');
    // keep the unused import honest
    expect(classes().length).toBe(2);
  });
});
