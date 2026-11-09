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
import { TrainerWizard } from './trainer-wizard';
import { Trainer, TrainerOptions } from './trainers-api';

@Component({ selector: 'app-blank-wizard', template: '' })
class Blank {}

const USER = { id: 'u1', email: 'anna@example.test', emailVerified: true, displayName: 'Anna', locale: 'en', theme: 'system', siteRole: 'none' };

/** A 24-byte PNG header (signature, IHDR length and type, width, height) padded with zeros: what the skin picker looks at. */
function pngFile(width: number, height: number, name = 'skin.png', extra = 200): File {
  const bytes = new Uint8Array(24 + extra);
  bytes.set([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0, 0, 0, 13, 0x49, 0x48, 0x44, 0x52]);
  const view = new DataView(bytes.buffer);
  view.setUint32(16, width);
  view.setUint32(20, height);
  return new File([bytes], name, { type: 'image/png' });
}

describe('Trainer wizard', () => {
  let http: HttpTestingController;
  let router: Router;
  let navigate: jasmine.Spy;
  let fixture: ComponentFixture<TrainerWizard>;
  let root: HTMLElement;

  const q = (id: string) => root.querySelector<HTMLElement>(`[data-testid="${id}"]`);
  const has = (id: string) => q(id) !== null;
  const text = (id: string) => q(id)?.textContent?.trim();
  const settle = () => flushUi(fixture);
  const click = async (id: string) => {
    press(root, id);
    await settle();
  };
  const type = async (id: string, value: string) => {
    const input = q(id) as HTMLInputElement;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    await settle();
  };
  const radio = async (id: string) => {
    (q(id)!.querySelector('input') as HTMLInputElement).click();
    await settle();
  };
  const next = () => click('next');
  const problems = () => Array.from(root.querySelectorAll('[data-testid="step-problems"] li')).map((li) => li.textContent?.trim());
  const step = () => root.querySelector('.panel')?.getAttribute('data-step');

  interface Setup {
    game?: Game;
    options?: TrainerOptions;
    trainers?: Trainer[];
    edit?: boolean;
  }

  async function mount(s: Setup = {}): Promise<void> {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: '**', component: Blank }]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ id: 'g1' }), data: { edit: s.edit ?? false } } } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    navigate = spyOn(router, 'navigate').and.resolveTo(true);
    TestBed.inject(AuthService).login('anna@example.test', 'pw').subscribe();
    http.expectOne('/api/v1/auth/login').flush({ accessToken: 'T', tokenType: 'Bearer', expiresIn: 900, user: USER });
    fixture = TestBed.createComponent(TrainerWizard);
    root = fixture.nativeElement;
    fixture.detectChanges();
    http.expectOne('/api/v1/games/g1').flush(s.game ?? game());
    http.expectOne('/api/v1/games/g1/trainer-options').flush(s.options ?? options());
    http.expectOne('/api/v1/games/g1/classes').flush(classes());
    http.expectOne('/api/v1/games/g1/trainers').flush(s.trainers ?? []);
    await settle();
  }

  /** Walks to a step by filling in the ones before it. */
  async function fillUntil(target: 'scores' | 'skills' | 'starter' | 'identity' | 'review'): Promise<void> {
    await click('class-ace_trainer');
    await next();
    if (target === 'scores') return;
    await next();
    if (target === 'skills') return;
    (q('skill-insight') as HTMLInputElement).click();
    (q('skill-pokemon_handling') as HTMLInputElement).click();
    await settle();
    await next();
    if (target === 'starter') return;
    await click('starter-charmander');
    await next();
    if (target === 'identity') return;
    await type('name', 'Ash_K');
    await next();
  }

  afterEach(() => {
    http.verify();
    useEnglish();
  });

  describe('step 1: the class', () => {
    it('shows the classes of the game and wants one before going on', async () => {
      await mount();
      expect(step()).toBe('class');
      expect(text('wizard-title')).toBe('Make your trainer');
      expect(root.querySelectorAll('[data-testid="classes"] li').length).toBe(2);
      expect(text('class-ace_trainer')).toContain('Ace Trainer');
      expect(text('class-ace_trainer')).toContain('Hit die d8');
      expect(text('class-ace_trainer')).toContain('Commanding Presence');
      expect(problems()).toEqual([]);

      await next();
      expect(step()).toBe('class');
      expect(problems()).toEqual(['Choose a class.']);

      await click('class-hiker');
      expect(q('class-hiker')?.getAttribute('aria-pressed')).toBe('true');
      expect(q('class-ace_trainer')?.getAttribute('aria-pressed')).toBe('false');
      await next();
      expect(step()).toBe('scores');
      expect(problems()).toEqual([]);
    });

    it('asks for the class in Portuguese too', async () => {
      usePortuguese();
      await mount();
      await next();
      expect(problems()).toEqual(['Escolha uma classe.']);
      expect(text('wizard-title')).toBe('Crie o seu treinador');
    });
  });

  describe('step 2: the ability scores', () => {
    it('point buy starts at 8, spends the budget and stops there', async () => {
      await mount();
      await fillUntil('scores');
      expect(text('points-left')).toBe('Points left: 27 of 27');
      expect(has('method-fixed')).toBeTrue();
      expect(text('score-str')).toBe('8');
      // 8 -> 15 costs 9, then 14 (7) and 13 (5) and 11 (3) and 10 (2) and 9 (1) = 27
      for (let i = 0; i < 7; i++) await click('inc-str');
      expect(text('score-str')).toBe('15');
      for (let i = 0; i < 6; i++) await click('inc-dex');
      for (let i = 0; i < 5; i++) await click('inc-con');
      for (let i = 0; i < 3; i++) await click('inc-int');
      for (let i = 0; i < 2; i++) await click('inc-wis');
      await click('inc-cha');
      expect(text('points-left')).toBe('Points left: 0 of 27');
      expect(text('mod-str')).toBe('+2');
      expect(text('mod-cha')).toBe('−1');
      // nothing more can be bought, but a point can be given back
      expect((q('inc-cha') as HTMLButtonElement).disabled).toBeTrue();
      expect((q('inc-str') as HTMLButtonElement).disabled).toBeTrue();
      expect((q('dec-str') as HTMLButtonElement).disabled).toBeFalse();
      await click('dec-str');
      expect(text('points-left')).toBe('Points left: 2 of 27');
      expect((q('dec-wis') as HTMLButtonElement).disabled).toBeFalse();
      expect((q('dec-int') as HTMLButtonElement).disabled).toBeFalse();
      // 8 is the lowest
      await click('dec-cha');
      expect((q('dec-cha') as HTMLButtonElement).disabled).toBeTrue();
      await next();
      expect(step()).toBe('skills');
    });

    it('lets the player choose the method when the game allows several, and the standard array uses each number once', async () => {
      await mount({ options: options({ scoreMethods: ['pointBuy', 'standardArray'] }) });
      await fillUntil('scores');
      expect(has('method-pointBuy')).toBeTrue();
      await click('method-standardArray');
      expect(text('mod-str')).toBeUndefined();
      await next();
      expect(problems()).toEqual(['Set all six ability scores.']);

      const pick = async (ability: string, value: string) => {
        const select = q(`array-${ability}`) as HTMLSelectElement;
        select.value = value;
        select.dispatchEvent(new Event('change'));
        await settle();
      };
      await pick('str', '15');
      // 15 is used now, so the others cannot take it
      const dexOptions = Array.from((q('array-dex') as HTMLSelectElement).options);
      expect(dexOptions.find((o) => o.value === '15')?.disabled).toBeTrue();
      expect(dexOptions.find((o) => o.value === '14')?.disabled).toBeFalse();
      await pick('dex', '14');
      await pick('con', '13');
      await pick('int', '12');
      await pick('wis', '10');
      await pick('cha', '8');
      expect(text('mod-str')).toBe('+2');
      await next();
      expect(step()).toBe('skills');
    });

    it('rolled scores are typed or rolled by the app, and must be 3 to 18', async () => {
      await mount({ options: options({ scoreMethods: ['roll4d6DropLowest'] }) });
      await fillUntil('scores');
      await click('roll-all');
      for (const a of ['str', 'dex', 'con', 'int', 'wis', 'cha']) {
        const v = Number((q(`roll-${a}`) as HTMLInputElement).value);
        expect(v).withContext(a).toBeGreaterThanOrEqual(3);
        expect(v).withContext(a).toBeLessThanOrEqual(18);
      }
      await type('roll-str', '19');
      await next();
      expect(step()).toBe('scores');
      expect(problems()).toEqual(['Strength is 19, but 4d6 drop the lowest gives 3 to 18.']);
      await type('roll-str', '18');
      await next();
      expect(step()).toBe('skills');
    });

    it('asks for the improvement points when the game starts above level 3', async () => {
      await mount({ options: options({ startLevel: 5 }) });
      await fillUntil('scores');
      expect(text('improvement-help')).toContain('level 5');
      await next();
      expect(problems()).toEqual(['Spend exactly 2 improvement points (0 so far).']);
      await type('imp-str', '2');
      await next();
      expect(step()).toBe('skills');
    });

    it('shows what each ability is for', async () => {
      await mount();
      await fillUntil('scores');
      expect(text('ability-wis')).toContain('Wisdom');
      expect(text('ability-wis')).toContain('Pokémon Handling');
    });
  });

  describe('step 3: the skills', () => {
    it('takes exactly as many skills as the class gives, and shows what each would add', async () => {
      await mount();
      await fillUntil('skills');
      expect(text('skills-left')).toBe('Pick 2 of the skills of the Ace Trainer. Picked: 0.');
      expect(root.querySelectorAll('.checks li').length).toBe(5);
      expect(root.querySelector('.checks')?.textContent).toContain('Pokémon Handling (Wisdom, +1)');

      await next();
      expect(step()).toBe('skills');
      expect(problems()).toEqual(['Pick 2 skills (0 chosen).']);

      (q('skill-insight') as HTMLInputElement).click();
      await settle();
      await next();
      expect(problems()).toEqual(['Pick 2 skills (1 chosen).']);
      (q('skill-athletics') as HTMLInputElement).click();
      await settle();
      // two are picked: the others are locked, the picked ones can be put back
      expect((q('skill-persuasion') as HTMLInputElement).disabled).toBeTrue();
      expect((q('skill-athletics') as HTMLInputElement).disabled).toBeFalse();
      await next();
      expect(step()).toBe('starter');
    });

    it('the skills are forgotten when another class is chosen', async () => {
      await mount();
      await fillUntil('skills');
      (q('skill-insight') as HTMLInputElement).click();
      await settle();
      await click('back');
      await click('back');
      expect(step()).toBe('class');
      await click('class-hiker');
      await next();
      await next();
      expect(text('skills-left')).toBe('Pick 2 of the skills of the Hiker. Picked: 0.');
    });
  });

  describe('step 4: the starter Pokémon', () => {
    it('lets the player pick one when the game says pick', async () => {
      await mount();
      await fillUntil('starter');
      expect(root.querySelectorAll('[data-testid="starters"] li').length).toBe(3);
      expect(q('starter-bulbasaur')?.querySelector('img')?.getAttribute('src')).toBe('/api/v1/art/0001_bulbasaur/bulbasaur.png?size=icon');
      expect(text('starter-squirtle')).toContain('#7 · water');
      await next();
      expect(problems()).toEqual(['Choose a starter Pokémon.']);
      await click('starter-squirtle');
      expect(q('starter-squirtle')?.getAttribute('aria-pressed')).toBe('true');
      await next();
      expect(step()).toBe('identity');
    });

    it('has nothing to choose when the admin assigns the starter', async () => {
      await mount({ options: options({ starters: { ...options().starters, method: 'assign' } }) });
      await fillUntil('starter');
      expect(has('starter-assign')).toBeTrue();
      expect(has('starters')).toBeFalse();
      await next();
      expect(step()).toBe('identity');
    });

    it('says that the game picks at random', async () => {
      await mount({ options: options({ starters: { ...options().starters, method: 'random' } }) });
      await fillUntil('starter');
      expect(has('starter-random')).toBeTrue();
      await next();
      expect(step()).toBe('identity');
    });
  });

  describe('step 5: the name and the skin', () => {
    it('checks the name and says what is wrong, in English and in Portuguese', async () => {
      await mount({ trainers: [trainer({ userId: 'u2', name: 'Brock' })] });
      await fillUntil('identity');
      await next();
      expect(problems()).toEqual(['The trainer needs a name.']);
      await type('name', 'ab');
      await next();
      expect(problems()).toEqual(['The name must be 3 to 16 characters long.']);
      await type('name', 'Ash Ketchum');
      await next();
      expect(problems()).toEqual(['The name may only use letters, digits and underscores (like a Minecraft username).']);
      await type('name', 'brock');
      await next();
      expect(problems()).toEqual(['Another trainer in this game is already called brock.']);
      await type('name', 'Misty_W');
      await next();
      expect(step()).toBe('review');
    });

    it('speaks Portuguese when the build is Portuguese', async () => {
      usePortuguese();
      await mount();
      await fillUntil('identity');
      await next();
      expect(problems()).toEqual(['O treinador precisa de um nome.']);
      await type('name', 'x');
      await next();
      expect(problems()).toEqual(['O nome tem de ter de 3 a 16 caracteres.']);
    });

    it('does not ask for a name when the game uses the Minecraft name', async () => {
      await mount({ options: options({ nameMode: 'accountName' }) });
      await fillUntil('identity');
      expect(has('name-from-account')).toBeTrue();
      expect(has('name')).toBeFalse();
      await next();
      expect(step()).toBe('review');
    });

    it('hides the skin choice when the game allows no skins', async () => {
      await mount({ options: options({ skin: { allowUpload: false, allowCopyFromUsername: false, maxBytes: 65536, size: 64 } }) });
      await fillUntil('identity');
      expect(has('skins-off')).toBeTrue();
      expect(has('skin-none')).toBeFalse();
    });

    it('checks a skin file before sending it: PNG, 64 by 64, not too big', async () => {
      await mount();
      await fillUntil('identity');
      await radio('skin-mode-upload');
      const choose = async (file: File) => {
        const input = q('skin-file') as HTMLInputElement;
        const transfer = new DataTransfer();
        transfer.items.add(file);
        input.files = transfer.files;
        input.dispatchEvent(new Event('change'));
        for (let i = 0; i < 5; i++) await settle();
      };
      await choose(pngFile(64, 32));
      expect(text('skin-problem')).toBe('The skin must be 64×64 pixels, not 64×32.');
      await choose(new File([new Uint8Array(100)], 'x.png'));
      expect(text('skin-problem')).toBe('The skin must be a PNG picture.');
      await choose(pngFile(64, 64, 'big.png', 70_000));
      expect(text('skin-problem')).toContain('This file is too big');
      await choose(pngFile(64, 64, 'alex.png'));
      expect(has('skin-problem')).toBeFalse();
      expect(text('skin-chosen')).toBe('alex.png');
    });

    it('checks a Minecraft name for the skin', async () => {
      await mount();
      await fillUntil('identity');
      await radio('skin-mode-username');
      await type('skin-username', 'no good!');
      expect(text('skin-problem')).toBe('A Minecraft name has 3 to 16 letters, digits or underscores.');
      await type('skin-username', 'SteveFan');
      expect(has('skin-problem')).toBeFalse();
    });
  });

  describe('step 6 and saving', () => {
    it('summarises the choices and creates the trainer', async () => {
      await mount();
      await fillUntil('review');
      expect(step()).toBe('review');
      expect(text('sum-name')).toBe('Ash_K');
      expect(text('sum-class')).toBe('Ace Trainer');
      expect(text('sum-scores')).toContain('Strength 8');
      expect(text('sum-skills')).toBe('Insight, Pokémon Handling');
      expect(text('sum-starter')).toBe('Charmander');
      expect(text('sum-skin')).toBe('None');

      await click('submit');
      const post = http.expectOne('/api/v1/games/g1/trainers');
      expect(post.request.method).toBe('POST');
      expect(post.request.body).toEqual({
        name: 'Ash_K',
        classId: 'ace_trainer',
        method: 'pointBuy',
        scores: { str: 8, dex: 8, con: 8, int: 8, wis: 8, cha: 8 },
        skills: ['insight', 'pokemon_handling'],
        improvements: {},
        starter: 'charmander',
      });
      post.flush(trainer(), { status: 201, statusText: 'Created' });
      await settle();
      expect(navigate).toHaveBeenCalledWith(['/games', 'g1', 'trainer']);
    });

    it('can go back and change a step', async () => {
      await mount();
      await fillUntil('review');
      await click('back');
      expect(step()).toBe('identity');
      await type('name', 'Gary_O');
      await next();
      expect(text('sum-name')).toBe('Gary_O');
    });

    it('uploads the chosen skin after the trainer is saved, then opens the trainer page', async () => {
      await mount();
      await fillUntil('identity');
      await radio('skin-mode-upload');
      const input = q('skin-file') as HTMLInputElement;
      const transfer = new DataTransfer();
      transfer.items.add(pngFile(64, 64, 'alex.png'));
      input.files = transfer.files;
      input.dispatchEvent(new Event('change'));
      for (let i = 0; i < 5; i++) await settle();
      await radio('skin-slim');
      await type('name', 'Ash_K');
      await next();
      expect(text('sum-skin')).toBe('Upload: alex.png');

      await click('submit');
      http.expectOne('/api/v1/games/g1/trainers').flush(trainer(), { status: 201, statusText: 'Created' });
      await settle();
      const put = http.expectOne('/api/v1/trainers/t1/skin');
      expect(put.request.method).toBe('PUT');
      const form = put.request.body as FormData;
      expect((form.get('file') as File).name).toBe('alex.png');
      expect(form.get('model')).toBe('slim');
      put.flush(trainer(), { status: 200, statusText: 'OK' });
      await settle();
      expect(navigate).toHaveBeenCalledWith(['/games', 'g1', 'trainer']);
    });

    it('copies a skin by Minecraft name', async () => {
      await mount();
      await fillUntil('identity');
      await radio('skin-mode-username');
      await type('skin-username', 'SteveFan');
      await type('name', 'Ash_K');
      await next();
      await click('submit');
      http.expectOne('/api/v1/games/g1/trainers').flush(trainer(), { status: 201, statusText: 'Created' });
      await settle();
      const put = http.expectOne('/api/v1/trainers/t1/skin');
      expect(put.request.body).toEqual({ username: 'SteveFan' });
      put.flush(trainer());
    });

    it('keeps the saved trainer when the skin fails, and only repeats the skin on a second try', async () => {
      await mount();
      await fillUntil('identity');
      await radio('skin-mode-username');
      await type('skin-username', 'NobodyHere');
      await type('name', 'Ash_K');
      await next();
      await click('submit');
      http.expectOne('/api/v1/games/g1/trainers').flush(trainer(), { status: 201, statusText: 'Created' });
      await settle();
      http.expectOne('/api/v1/trainers/t1/skin').flush({ code: 'MINECRAFT_USER_NOT_FOUND', message: 'x' }, { status: 404, statusText: 'Not Found' });
      await settle();
      expect(text('skin-failure')).toContain('There is no Minecraft account with that name.');
      expect(navigate).not.toHaveBeenCalled();

      await click('retry-skin');
      http.expectNone('/api/v1/games/g1/trainers'); // the trainer is not made twice
      http.expectOne('/api/v1/trainers/t1/skin').flush(trainer());
      await settle();
      expect(navigate).toHaveBeenCalledWith(['/games', 'g1', 'trainer']);
    });

    it('can go on without the skin after it failed', async () => {
      await mount();
      await fillUntil('identity');
      await radio('skin-mode-username');
      await type('skin-username', 'MojangDown');
      await type('name', 'Ash_K');
      await next();
      await click('submit');
      http.expectOne('/api/v1/games/g1/trainers').flush(trainer(), { status: 201, statusText: 'Created' });
      await settle();
      http.expectOne('/api/v1/trainers/t1/skin').flush({ code: 'MOJANG_UNAVAILABLE', message: 'x' }, { status: 502, statusText: 'Bad Gateway' });
      await settle();
      expect(text('skin-failure')).toContain('Mojang could not be reached');
      await click('skip-skin');
      expect(navigate).toHaveBeenCalledWith(['/games', 'g1', 'trainer']);
    });

    it('shows what the API refused, in the language of the build', async () => {
      await mount();
      await fillUntil('review');
      await click('submit');
      http.expectOne('/api/v1/games/g1/trainers').flush(
        {
          code: 'INVALID_TRAINER',
          message: 'English text',
          details: { problems: [{ code: 'name_taken', field: 'name', message: 'English text' }, { code: 'method_not_allowed', field: 'scores', message: 'English text' }] },
        },
        { status: 422, statusText: 'Unprocessable' },
      );
      await settle();
      const shown = Array.from(root.querySelectorAll('[data-testid="server-problems"] li')).map((li) => li.textContent?.trim());
      expect(shown).toEqual(['Another trainer in this game has this name already.', 'This game does not allow that way of making ability scores.']);
      // the button works again
      expect((q('submit') as HTMLButtonElement).disabled).toBeFalse();
    });

    it('shows other errors with the usual sentences', async () => {
      await mount();
      await fillUntil('review');
      await click('submit');
      http.expectOne('/api/v1/games/g1/trainers').flush({ code: 'TRAINER_EXISTS', message: 'x' }, { status: 409, statusText: 'Conflict' });
      await settle();
      expect(text('failure')).toBe('You have a trainer in this game already. Change or remove it instead.');
    });
  });

  describe('who may use it', () => {
    it('is not for a spectator', async () => {
      await mount({ game: game({ myRoles: ['spectator'] }) });
      expect(text('blocked')).toBe('Only players make trainers. Ask the admin to accept you as a player.');
      expect(has('next')).toBeFalse();
    });

    it('sends a person who has a trainer already to the trainer page', async () => {
      await mount({ trainers: [trainer()] });
      expect(navigate).toHaveBeenCalledWith(['/games', 'g1', 'trainer']);
    });
  });

  describe('changing a trainer', () => {
    it('starts from the saved choices and sends a change, not a new trainer', async () => {
      await mount({ edit: true, trainers: [trainer()], game: game({ state: 'lobby' }) });
      expect(text('wizard-title')).toBe('Change your trainer');
      // the class, skills, starter and name are filled in; the scores are chosen again
      expect(q('class-ace_trainer')?.getAttribute('aria-pressed')).toBe('true');
      await next();
      for (const a of ['str', 'dex', 'con']) {
        // scores come from the trainer, so they are above 8
        expect(Number(text(`score-${a}`))).toBeGreaterThan(8);
      }
      await next();
      expect((q('skill-insight') as HTMLInputElement).checked).toBeTrue();
      await next();
      expect(q('starter-charmander')?.getAttribute('aria-pressed')).toBe('true');
      await next();
      expect((q('name') as HTMLInputElement).value).toBe('Ash_K');
      // the trainer's own name is not "taken"
      await next();
      expect(step()).toBe('review');
      expect(text('submit')).toBe('Save the changes');
      await click('submit');
      const patch = http.expectOne('/api/v1/trainers/t1');
      expect(patch.request.method).toBe('PATCH');
      expect(patch.request.body.name).toBe('Ash_K');
      patch.flush(trainer());
    });
  });
});
