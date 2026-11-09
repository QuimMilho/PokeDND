import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { flushUi, press } from '../../testing/ui';
import { Game } from '../games/games-api';
import { Setting } from './settings-api';
import { SettingsScreen } from './settings-screen';

const GAME: Game = {
  id: 'g1',
  serverId: 's1',
  name: 'Kanto Weekend',
  state: 'draft',
  lobbyOpen: false,
  ownerId: 'u1',
  myRoles: ['game_admin'],
  ruleSet: 'builtin:defaults',
  createdAt: '2026-11-09T10:00:00Z',
};

function setting(key: string, group: string, type: Setting['type'], value: unknown, extra: Partial<Setting> = {}): Setting {
  return {
    key,
    group,
    type,
    value,
    defaultValue: value,
    source: 'default',
    overridden: false,
    chapter: 1,
    description: `What ${key} does.`,
    ...extra,
  };
}

/** One setting of every kind, in three groups, like a small copy of Appendix A. */
function registry(): Setting[] {
  return [
    setting('game.admin.plays', 'Game, lobby and accounts', 'bool', false),
    setting('game.players.max', 'Game, lobby and accounts', 'int', 0, { min: 0, max: 64 }),
    setting('game.import.copy', 'Game, lobby and accounts', 'csv', 'trainer,pokemon', { allowed: ['trainer', 'pokemon', 'bag', 'money'] }),
    setting('dice.style', 'Dice', 'enum', 'smart', { allowed: ['smart', 'classic'] }),
    setting('dice.forceDie', 'Dice', 'json', {}),
    setting('dice.crit.chance', 'Dice', 'decimal', 0.05, { min: 0, max: 1 }),
    setting('combat.ai.level', 'Combat', 'enum', 'normal', { allowed: ['easy', 'normal', 'hard'] }),
    setting('combat.fleeing.steps', 'Combat', 'intList', [1, 2, 3]),
    setting('combat.typeChart', 'Combat', 'data', 'official'),
    setting('trainer.xp.table', 'Combat', 'data', undefined, { defaultValue: undefined }),
    setting('trainer.start.items', 'Combat', 'string', 'pokeBall×5'),
  ];
}

describe('Settings screen', () => {
  let fixture: ComponentFixture<SettingsScreen>;
  let http: HttpTestingController;
  let root: HTMLElement;

  const settle = () => flushUi(fixture);
  const keys = () => Array.from(root.querySelectorAll('app-setting-row [data-key]')).map((e) => e.getAttribute('data-key'));
  const row = (key: string) => root.querySelector(`[data-key="${key}"]`) as HTMLElement;
  const has = (id: string) => root.querySelector(`[data-testid="${id}"]`) !== null;
  const text = (id: string) => root.querySelector(`[data-testid="${id}"]`)?.textContent?.trim();
  const type = (id: string, value: string, event = 'input') => {
    const input = root.querySelector(`[data-testid="${id}"]`) as HTMLInputElement;
    input.value = value;
    input.dispatchEvent(new Event(event));
  };

  async function open(settings: Setting[] = registry(), game: Game = GAME, presetsEnabled = true): Promise<void> {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ id: 'g1' }) } } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(SettingsScreen);
    root = fixture.nativeElement;
    http.expectOne('/api/v1/games/g1').flush(game);
    http.expectOne('/api/v1/games/g1/settings').flush({ gameId: 'g1', ruleSet: { id: 'builtin:defaults', name: 'Defaults' }, settings });
    http.expectOne('/api/v1/games/g1/presets').flush({
      enabled: presetsEnabled,
      presets: [
        { id: 'builtin:defaults', name: 'Defaults', description: 'Nothing.', settings: {} },
        { id: 'builtin:casual', name: 'Casual', description: 'Easy.', settings: { 'combat.ai.level': 'easy' } },
      ],
    });
    http.expectOne('/api/v1/rulesets').flush([
      { id: 'builtin:defaults', scope: 'builtin', name: 'Defaults', description: null, editable: false },
      { id: 'r1', scope: 'user', name: 'Kanto Weekend rules', description: null, editable: true },
    ]);
    await settle();
  }

  async function openAll(): Promise<void> {
    press(root, 'open-all');
    await settle();
  }

  afterEach(() => http.verify());

  describe('the groups', () => {
    it('shows exactly the keys of the registry, each once, and no others', async () => {
      const all = registry();
      await open(all);
      await openAll();
      expect(keys().sort()).toEqual(all.map((s) => s.key).sort());
      expect(new Set(keys()).size).toBe(all.length);
    });

    it('keeps the groups in the order of Appendix A, with the number of settings in each', async () => {
      await open();
      const titles = Array.from(root.querySelectorAll('mat-panel-title')).map((h) => h.textContent?.trim());
      const counts = Array.from(root.querySelectorAll('mat-panel-description')).map((h) => h.textContent?.trim());
      expect(titles).toEqual(['Game, lobby and accounts', 'Dice', 'Combat']);
      expect(counts).toEqual(['3', '3', '5']);
    });

    it('draws a control that fits each kind of value', async () => {
      await open();
      await openAll();
      expect(row('game.admin.plays').querySelector('mat-slide-toggle')).not.toBeNull();
      expect(row('game.players.max').querySelector('input[type="number"]')).not.toBeNull();
      expect(row('dice.crit.chance').querySelector('input[type="number"]')).not.toBeNull();
      expect(row('dice.style').querySelector('select')).not.toBeNull();
      expect(Array.from(row('dice.style').querySelectorAll('option')).map((o) => o.textContent)).toEqual(['smart', 'classic']);
      expect(row('game.import.copy').querySelectorAll('mat-checkbox').length).toBe(4);
      expect(row('dice.forceDie').querySelector('textarea')).not.toBeNull();
      expect(row('trainer.xp.table').querySelector('textarea')).not.toBeNull();
      expect(row('trainer.start.items').querySelector('input[type="text"]')).not.toBeNull();
      expect((row('combat.fleeing.steps').querySelector('input') as HTMLInputElement).value).toBe('1, 2, 3');
    });

    it('reports the number of settings shown', async () => {
      await open();
      expect(text('count')).toBe('11 of 11 settings shown · 0 changed');
    });
  });

  describe('default markers and "reset to default"', () => {
    function mixed(): Setting[] {
      const all = registry();
      all[0] = { ...all[0], value: true, overridden: true, source: 'game' };
      all[3] = { ...all[3], value: 'classic', overridden: true, source: 'ruleSet' };
      return all;
    }

    it('says for every setting whether it is the default, comes from the rule set or was changed in this game', async () => {
      await open(mixed());
      await openAll();
      expect(text('source-game.admin.plays')).toBe('Changed in this game');
      expect(text('source-dice.style')).toBe('From the rule set');
      expect(text('source-game.players.max')).toBe('Default');
      expect(text('default-game.admin.plays')).toBe('Default: false');
      expect(text('default-trainer.xp.table')).toBe('Default: built-in data');
    });

    it('offers "reset to default" only where the game itself changed the value', async () => {
      await open(mixed());
      await openAll();
      expect(has('reset-game.admin.plays')).toBeTrue();
      expect(has('reset-dice.style')).toBeFalse(); // it comes from the rule set: loading another rule set changes it
      expect(has('reset-game.players.max')).toBeFalse();
    });

    it('puts one setting back to its default', async () => {
      await open(mixed());
      await openAll();
      press(root, 'reset-game.admin.plays');
      const post = http.expectOne('/api/v1/games/g1/settings/reset');
      expect(post.request.body).toEqual({ keys: ['game.admin.plays'] });
      post.flush({ changed: [setting('game.admin.plays', 'Game, lobby and accounts', 'bool', false)] });
      await settle();
      expect(text('source-game.admin.plays')).toBe('Default');
      expect(has('reset-game.admin.plays')).toBeFalse();
      expect(text('count')).toContain('1 changed'); // the rule set one is still there
    });

    it('puts everything back after a second tap', async () => {
      await open(mixed());
      press(root, 'reset-all');
      await settle();
      expect(has('reset-all-yes')).toBeTrue();
      http.expectNone('/api/v1/games/g1/settings/reset'); // the first tap only asks
      press(root, 'reset-all-yes');
      const post = http.expectOne('/api/v1/games/g1/settings/reset');
      expect(post.request.body).toEqual({ all: true });
      post.flush({ changed: [setting('game.admin.plays', 'Game, lobby and accounts', 'bool', false)] });
      await settle();
      expect(text('count')).toContain('1 changed');
    });
  });

  describe('changing a value', () => {
    it('saves a switch at once and shows the new state', async () => {
      await open();
      await openAll();
      press(root, 'input-game.admin.plays');
      const patch = http.expectOne('/api/v1/games/g1/settings');
      expect(patch.request.method).toBe('PATCH');
      expect(patch.request.body).toEqual({ settings: { 'game.admin.plays': true } });
      patch.flush({ changed: [setting('game.admin.plays', 'Game, lobby and accounts', 'bool', true, { source: 'game', overridden: true })] });
      await settle();
      expect(text('source-game.admin.plays')).toBe('Changed in this game');
      expect(has('reset-game.admin.plays')).toBeTrue();
    });

    it('saves a number as a number', async () => {
      await open();
      await openAll();
      type('input-game.players.max', '12', 'change');
      const patch = http.expectOne('/api/v1/games/g1/settings');
      expect(patch.request.body).toEqual({ settings: { 'game.players.max': 12 } });
      patch.flush({ changed: [setting('game.players.max', 'Game, lobby and accounts', 'int', 12, { source: 'game', overridden: true })] });
    });

    it('does not send what is not a number, or is outside the limits', async () => {
      await open();
      await openAll();
      type('input-game.players.max', 'many', 'change');
      await settle();
      expect(text('error-game.players.max')).toBe('Enter a whole number.');
      type('input-game.players.max', '100', 'change');
      await settle();
      expect(text('error-game.players.max')).toBe('The largest value is 64.');
      type('input-game.players.max', '-1', 'change');
      await settle();
      expect(text('error-game.players.max')).toBe('The smallest value is 0.');
      http.expectNone('/api/v1/games/g1/settings');
    });

    it('does not send JSON that is broken, and sends JSON that is not', async () => {
      await open();
      await openAll();
      type('input-dice.forceDie', '{ nope', 'change');
      await settle();
      expect(text('error-dice.forceDie')).toBe('This is not valid JSON.');
      type('input-dice.forceDie', '{"attack": 20}', 'change');
      expect(http.expectOne('/api/v1/games/g1/settings').request.body).toEqual({ settings: { 'dice.forceDie': { attack: 20 } } });
    });

    it('saves a ticked box of a list as the list in the order of the registry', async () => {
      await open();
      await openAll();
      const box = row('game.import.copy').querySelector('[data-testid="option-game.import.copy-money"] input') as HTMLInputElement;
      box.click();
      expect(http.expectOne('/api/v1/games/g1/settings').request.body).toEqual({ settings: { 'game.import.copy': 'trainer,pokemon,money' } });
    });

    it('saves a choice from a list', async () => {
      await open();
      await openAll();
      const select = row('dice.style').querySelector('select') as HTMLSelectElement;
      select.value = 'classic';
      select.dispatchEvent(new Event('change'));
      expect(http.expectOne('/api/v1/games/g1/settings').request.body).toEqual({ settings: { 'dice.style': 'classic' } });
    });

    it('shows what the API says is wrong with a value, next to the setting', async () => {
      await open();
      await openAll();
      type('input-game.players.max', '12', 'change');
      http
        .expectOne('/api/v1/games/g1/settings')
        .flush({ code: 'INVALID_SETTING', message: 'game.players.max must be at most 8.' }, { status: 400, statusText: 'Bad Request' });
      await settle();
      expect(text('error-game.players.max')).toBe('This value is not allowed for this setting. game.players.max must be at most 8.');
    });

    it('takes back the last change', async () => {
      await open();
      press(root, 'undo');
      http.expectOne('/api/v1/games/g1/settings/undo').flush({ changed: [] });
      await settle();
      expect(has('failure')).toBeFalse();
    });
  });

  describe('finding settings', () => {
    it('searches the keys, the descriptions and the group names, and opens the groups that match', async () => {
      await open();
      type('search', 'dice');
      await settle();
      // "dice" is a group name and part of two keys: the whole group Dice, and no row of the others
      expect(keys().sort()).toEqual(['dice.crit.chance', 'dice.forceDie', 'dice.style']);
      expect(text('count')).toBe('3 of 11 settings shown · 0 changed');

      type('search', 'combat.ai');
      await settle();
      expect(keys()).toEqual(['combat.ai.level']);

      type('search', 'zzzz');
      await settle();
      expect(has('no-match')).toBeTrue();
    });

    it('can show only what was changed', async () => {
      const all = registry();
      all[6] = { ...all[6], value: 'hard', overridden: true, source: 'game' };
      await open(all);
      press(root, 'changed-only');
      await settle();
      expect(keys()).toEqual(['combat.ai.level']);
    });
  });

  describe('presets and rule sets', () => {
    it('offers the presets and the saved rule sets (the built-in ones are the presets)', async () => {
      await open();
      const presets = Array.from(root.querySelectorAll('[data-testid="preset-picker"] option')).map((o) => o.textContent?.trim());
      expect(presets).toEqual(['Choose…', 'Defaults', 'Casual']);
      const ruleSets = Array.from(root.querySelectorAll('[data-testid="ruleset-picker"] option')).map((o) => o.textContent?.trim());
      expect(ruleSets).toEqual(['Choose…', 'Kanto Weekend rules']);
      expect(text('rule-set-name')).toBe('Defaults');
    });

    it('hides the presets when the game turned them off (game.presets.enabled)', async () => {
      await open(registry(), GAME, false);
      expect(has('preset-picker')).toBeFalse();
      expect(has('ruleset-picker')).toBeTrue();
    });

    it('previews what a preset would change, then loads it', async () => {
      await open();
      const select = root.querySelector('[data-testid="preset-picker"]') as HTMLSelectElement;
      select.value = 'builtin:casual';
      select.dispatchEvent(new Event('change'));
      const dry = http.expectOne('/api/v1/games/g1/rulesets/apply');
      expect(dry.request.body).toEqual({ ruleSetId: 'builtin:casual', dryRun: true, resetOverrides: false });
      dry.flush({
        ruleSet: { id: 'builtin:casual', name: 'Casual' },
        applied: false,
        changes: [{ key: 'combat.ai.level', from: 'normal', to: 'easy', fromSource: 'default', toSource: 'ruleSet' }],
      });
      await settle();
      expect(text('change-combat.ai.level')).toContain('combat.ai.level: normal → easy');

      press(root, 'apply');
      const real = http.expectOne('/api/v1/games/g1/rulesets/apply');
      expect(real.request.body).toEqual({ ruleSetId: 'builtin:casual', dryRun: false, resetOverrides: false });
      real.flush({ ruleSet: { id: 'builtin:casual', name: 'Casual' }, applied: true, changes: [] });
      // the screen asks for the settings again, because many values changed
      const all = registry();
      all[6] = { ...all[6], value: 'easy', overridden: true, source: 'ruleSet' };
      http.expectOne('/api/v1/games/g1/settings').flush({ gameId: 'g1', ruleSet: { id: 'builtin:casual', name: 'Casual' }, settings: all });
      await settle();
      expect(text('applied')).toContain('Casual');
      expect(text('rule-set-name')).toBe('Casual');
      expect(has('preview')).toBeFalse();
    });

    it('says when nothing would change, and can be cancelled', async () => {
      await open();
      const select = root.querySelector('[data-testid="preset-picker"]') as HTMLSelectElement;
      select.value = 'builtin:defaults';
      select.dispatchEvent(new Event('change'));
      http.expectOne('/api/v1/games/g1/rulesets/apply').flush({ ruleSet: { id: 'builtin:defaults', name: 'Defaults' }, applied: false, changes: [] });
      await settle();
      expect(has('no-changes')).toBeTrue();
      press(root, 'cancel-preview');
      await settle();
      expect(has('preview')).toBeFalse();
    });

    it('asks again with "discard my changes" when that box is ticked', async () => {
      await open();
      const select = root.querySelector('[data-testid="ruleset-picker"]') as HTMLSelectElement;
      select.value = 'r1';
      select.dispatchEvent(new Event('change'));
      http.expectOne('/api/v1/games/g1/rulesets/apply').flush({ ruleSet: { id: 'r1', name: 'Kanto Weekend rules' }, applied: false, changes: [] });
      await settle();
      (root.querySelector('[data-testid="discard-own"] input') as HTMLInputElement).click();
      const again = http.expectOne('/api/v1/games/g1/rulesets/apply');
      expect(again.request.body).toEqual({ ruleSetId: 'r1', dryRun: true, resetOverrides: true });
      again.flush({ ruleSet: { id: 'r1', name: 'Kanto Weekend rules' }, applied: false, changes: [] });
    });

    it('saves the rules of the game as a rule set', async () => {
      await open();
      type('save-name', 'Friday rules');
      await settle();
      press(root, 'save-rule-set');
      const post = http.expectOne('/api/v1/rulesets');
      expect(post.request.body).toEqual({ name: 'Friday rules', fromGameId: 'g1' });
      post.flush({ id: 'r2', name: 'Friday rules' });
      await settle();
      expect(text('saved')).toContain('Friday rules');
      expect(Array.from(root.querySelectorAll('[data-testid="ruleset-picker"] option')).map((o) => o.textContent?.trim())).toContain('Friday rules');
    });
  });

  describe('who may use it', () => {
    it('tells a player that only the admins change settings, and shows no controls', async () => {
      await open(registry(), { ...GAME, myRoles: ['player'] });
      expect(has('not-admin')).toBeTrue();
      expect(has('groups')).toBeFalse();
    });
  });
});
