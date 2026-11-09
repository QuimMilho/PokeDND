import { TestBed } from '@angular/core/testing';
import { options } from '../../testing/trainers';
import { classes } from '../../testing/trainers';
import { useEnglish, usePortuguese } from '../../testing/translations';
import {
  WizardState,
  checkImprovements,
  checkName,
  checkScores,
  checkSkills,
  checkStarter,
  defaultScores,
  improvementPointsOwed,
  modifier,
  pointBuyCost,
  pointsSpent,
  rollFourD6DropLowest,
  serverProblemText,
  signed,
} from './wizard-model';

const texts = (problems: { text: string }[]) => problems.map((p) => p.text);

describe('Wizard rules (chapter 4.3)', () => {
  afterEach(() => useEnglish());

  it('the modifier of a score is floor((score - 10) / 2), written with a sign', () => {
    expect([8, 9, 10, 11, 12, 15, 18].map(modifier)).toEqual([-1, -1, 0, 0, 1, 2, 4]);
    expect([2, 0, -1].map(signed)).toEqual(['+2', '0', '−1']);
  });

  it('point buy costs 0, 1, 2, 3, 4, 5, 7 and 9 for 8 to 15, and 27 points buy 15 14 13 11 10 9', () => {
    const o = options();
    expect([8, 9, 10, 11, 12, 13, 14, 15].map((s) => pointBuyCost(s, o))).toEqual([0, 1, 2, 3, 4, 5, 7, 9]);
    expect(pointBuyCost(16, o)).toBeNull();
    expect(pointsSpent({ str: 15, dex: 14, con: 13, int: 11, wis: 10, cha: 9 }, o)).toBe(27);
    expect(pointsSpent(defaultScores('pointBuy', o), o)).toBe(0);
  });

  describe('ability scores', () => {
    it('accepts 27 points and refuses 28, saying how many the scores cost', () => {
      const o = options();
      expect(checkScores('pointBuy', { str: 15, dex: 14, con: 13, int: 11, wis: 10, cha: 9 }, o)).toEqual([]);
      expect(texts(checkScores('pointBuy', { str: 15, dex: 15, con: 13, int: 11, wis: 10, cha: 9 }, o))).toEqual([
        'These scores cost 29 points, but you only have 27.',
      ]);
    });

    it('refuses a point-buy score outside 8 to 15, naming the ability', () => {
      const p = checkScores('pointBuy', { str: 16, dex: 8, con: 8, int: 8, wis: 8, cha: 7 }, options());
      expect(texts(p)).toEqual(['Strength is 16, but with point buy a score is 8 to 15.', 'Charisma is 7, but with point buy a score is 8 to 15.']);
      expect(p[0].code).toBe('score_out_of_range');
    });

    it('wants all six scores', () => {
      expect(texts(checkScores('standardArray', defaultScores('standardArray', options()), options()))).toEqual(['Set all six ability scores.']);
    });

    it('the standard array must be used once each', () => {
      const o = options({ scoreMethods: ['standardArray'] });
      expect(checkScores('standardArray', { str: 8, dex: 10, con: 12, int: 13, wis: 14, cha: 15 }, o)).toEqual([]);
      expect(texts(checkScores('standardArray', { str: 15, dex: 15, con: 13, int: 12, wis: 10, cha: 8 }, o))).toEqual([
        'Use each number of the standard array (15, 14, 13, 12, 10, 8) once.',
      ]);
    });

    it('rolled scores are 3 to 18', () => {
      const ok = { str: 18, dex: 3, con: 12, int: 14, wis: 9, cha: 16 };
      expect(checkScores('roll4d6DropLowest', ok, options())).toEqual([]);
      expect(texts(checkScores('roll4d6DropLowest', { ...ok, str: 19 }, options()))).toEqual(['Strength is 19, but 4d6 drop the lowest gives 3 to 18.']);
      expect(checkScores('roll4d6DropLowest', { ...ok, str: 2.5 }, options()).length).toBe(1);
    });

    it('4d6 drop the lowest keeps the best three dice', () => {
      const dice = [6, 1, 5, 4];
      let i = 0;
      expect(rollFourD6DropLowest(() => dice[i++])).toBe(15);
      expect(rollFourD6DropLowest(() => 1)).toBe(3);
      expect(rollFourD6DropLowest(() => 6)).toBe(18);
    });

    it('says the same in Portuguese', () => {
      usePortuguese();
      expect(texts(checkScores('pointBuy', { str: 15, dex: 15, con: 13, int: 11, wis: 10, cha: 9 }, options()))).toEqual([
        'Estes valores custam 29 pontos, mas só tem 27.',
      ]);
      expect(texts(checkScores('pointBuy', { str: 16, dex: 8, con: 8, int: 8, wis: 8, cha: 8 }, options()))).toEqual([
        'Força é 16, mas na compra de pontos um valor vai de 8 a 15.',
      ]);
    });
  });

  describe('improvements for a game that starts above level 3', () => {
    it('earns 2 points for each improvement level up to the starting level', () => {
      expect([1, 3, 4, 7, 8, 19, 20].map((startLevel) => improvementPointsOwed(options({ startLevel })))).toEqual([0, 0, 2, 2, 4, 10, 10]);
    });

    it('wants exactly the points owed and no score above 20', () => {
      const o = options({ startLevel: 5 });
      const none = { str: 0, dex: 0, con: 0, int: 0, wis: 0, cha: 0 };
      expect(checkImprovements(none, defaultScores('pointBuy', o), o).map((p) => p.code)).toEqual(['improvement_points']);
      expect(texts(checkImprovements(none, defaultScores('pointBuy', o), o))).toEqual(['Spend exactly 2 improvement points (0 so far).']);
      expect(checkImprovements({ ...none, str: 2 }, defaultScores('pointBuy', o), o)).toEqual([]);
      expect(texts(checkImprovements({ ...none, str: 2 }, { str: 19, dex: 8, con: 8, int: 8, wis: 8, cha: 8 }, o))).toEqual([
        'Strength would be 21, but no score goes above 20.',
      ]);
      expect(checkImprovements(none, defaultScores('pointBuy', options()), options())).toEqual([]);
    });
  });

  describe('skills, name and starter', () => {
    it('the class says how many skills to pick', () => {
      const ace = classes()[0];
      expect(texts(checkSkills(ace, ['insight']))).toEqual(['Pick 2 skills (1 chosen).']);
      expect(checkSkills(ace, ['insight', 'athletics'])).toEqual([]);
      expect(texts(checkSkills(ace, ['insight', 'athletics', 'persuasion']))).toEqual(['Pick 2 skills (3 chosen).']);
      expect(checkSkills(undefined, [])).toEqual([]);
    });

    it('a name is 3 to 16 letters, digits or underscores and unique, whatever the case', () => {
      const o = options();
      for (const ok of ['Ana', 'Ash_Ketchum', 'a1b', 'ABCDEFGHIJKLMNOP']) {
        expect(checkName(ok, o, [])).withContext(ok).toEqual([]);
      }
      expect(texts(checkName('', o, []))).toEqual(['The trainer needs a name.']);
      expect(texts(checkName('ab', o, []))).toEqual(['The name must be 3 to 16 characters long.']);
      expect(texts(checkName('ABCDEFGHIJKLMNOPQ', o, []))).toEqual(['The name must be 3 to 16 characters long.']);
      expect(checkName('Ash Ketchum', o, []).map((p) => p.code)).toEqual(['name_characters']);
      expect(texts(checkName('ana', o, ['Ana']))).toEqual(['Another trainer in this game is already called ana.']);
      expect(checkName('Cat', o, ['Ana'])).toEqual([]);
    });

    it('the name is not checked when the game uses the Minecraft name', () => {
      expect(checkName('', options({ nameMode: 'accountName' }), [])).toEqual([]);
    });

    it('a starter is needed only when the game lets the player pick', () => {
      expect(texts(checkStarter(options(), null))).toEqual(['Choose a starter Pokémon.']);
      expect(checkStarter(options(), 'bulbasaur')).toEqual([]);
      expect(checkStarter(options({ starters: { ...options().starters, method: 'assign' } }), null)).toEqual([]);
      expect(checkStarter(options({ starters: { ...options().starters, method: 'random' } }), null)).toEqual([]);
    });

    it('name problems read well in Portuguese', () => {
      usePortuguese();
      expect(texts(checkName('ab', options(), []))).toEqual(['O nome tem de ter de 3 a 16 caracteres.']);
      expect(texts(checkName('', options(), []))).toEqual(['O treinador precisa de um nome.']);
      expect(texts(checkStarter(options(), null))).toEqual(['Escolha um Pokémon inicial.']);
      expect(texts(checkSkills(classes()[0], []))).toEqual(['Escolha 2 perícias (0 escolhidas).']);
    });
  });

  describe('what the API answers', () => {
    it('is shown in the language of the build, never the API text', () => {
      const p = { code: 'name_taken', field: 'name', message: 'Another trainer in this game is already called Ash.' };
      expect(serverProblemText(p, options())).toBe('Another trainer in this game has this name already.');
      usePortuguese();
      expect(serverProblemText(p, options())).toBe('Outro treinador deste jogo já tem este nome.');
      expect(serverProblemText({ code: 'something_new', field: '', message: 'x' }, null)).toBe('O treinador não cumpre as regras deste jogo. Verifique cada passo.');
    });
  });

  describe('WizardState', () => {
    function state(extra = options()): WizardState {
      TestBed.configureTestingModule({ providers: [WizardState] });
      const s = TestBed.inject(WizardState);
      s.configure(extra, classes(), ['Bob']);
      return s;
    }

    it('starts with point buy at 8 everywhere and nothing chosen', () => {
      const s = state();
      expect(s.method()).toBe('pointBuy');
      expect(s.scores()).toEqual({ str: 8, dex: 8, con: 8, int: 8, wis: 8, cha: 8 });
      expect(s.isValid('class')).toBeFalse();
      expect(s.isValid('scores')).toBeTrue();
      expect(s.isValid('skills')).toBeTrue(); // no class yet: nothing to pick
      expect(s.isValid('starter')).toBeFalse();
      expect(s.isValid('identity')).toBeFalse();
      expect(s.isValid('review')).toBeFalse();
    });

    it('is valid once everything is chosen, and then says what to send', () => {
      const s = state();
      s.setClass('ace_trainer');
      s.toggleSkill('insight');
      s.toggleSkill('pokemon_handling');
      s.starter.set('charmander');
      s.name.set('  Ash_K ');
      for (const [a, v] of Object.entries({ str: 15, dex: 14, con: 13, int: 11, wis: 10, cha: 9 })) {
        s.setScore(a as 'str', v);
      }
      expect(s.problems('review')).toEqual([]);
      expect(s.choices()).toEqual({
        name: 'Ash_K',
        classId: 'ace_trainer',
        method: 'pointBuy',
        scores: { str: 15, dex: 14, con: 13, int: 11, wis: 10, cha: 9 },
        skills: ['insight', 'pokemon_handling'],
        improvements: {},
        starter: 'charmander',
      });
    });

    it('forgets the skills when the class changes, and the scores when the method changes', () => {
      const s = state(options({ scoreMethods: ['pointBuy', 'standardArray'] }));
      s.setClass('ace_trainer');
      s.toggleSkill('insight');
      s.setClass('ace_trainer');
      expect(s.skills()).toEqual(['insight']);
      s.setClass('hiker');
      expect(s.skills()).toEqual([]);
      s.setScore('str', 12);
      s.setMethod('standardArray');
      expect(s.scores().str).toBeNull();
      s.setMethod('pointBuy');
      expect(s.scores().str).toBe(8);
    });

    it('rolls six scores of 3 to 18 for the roll method', () => {
      const s = state(options({ scoreMethods: ['roll4d6DropLowest'] }));
      s.rollAll(() => 6);
      expect(s.scores()).toEqual({ str: 18, dex: 18, con: 18, int: 18, wis: 18, cha: 18 });
      s.rollAll();
      expect(Object.values(s.scores()).every((v) => v !== null && v >= 3 && v <= 18)).toBeTrue();
      expect(s.isValid('scores')).toBeTrue();
    });

    it('does not send a starter the game assigns', () => {
      const s = state(options({ starters: { ...options().starters, method: 'assign' } }));
      s.starter.set('charmander');
      s.setClass('ace_trainer');
      expect(s.choices().starter).toBeNull();
    });
  });
});
