import { Game } from '../app/games/games-api';
import { Trainer, TrainerClass, TrainerOptions } from '../app/trainers/trainers-api';

/** What the API sends for a default game (`GET /games/{id}/trainer-options`). */
export function options(extra: Partial<TrainerOptions> = {}): TrainerOptions {
  return {
    nameMode: 'perGame',
    nameMinLength: 3,
    nameMaxLength: 16,
    scoreMethods: ['pointBuy'],
    pointBuyBudget: 27,
    pointBuyCosts: { '8': 0, '9': 1, '10': 2, '11': 3, '12': 4, '13': 5, '14': 7, '15': 9 },
    standardArray: [15, 14, 13, 12, 10, 8],
    startLevel: 1,
    improvementLevels: [4, 8, 12, 16, 19],
    skills: [
      { id: 'athletics', name: 'Athletics', ability: 'str' },
      { id: 'insight', name: 'Insight', ability: 'wis' },
      { id: 'persuasion', name: 'Persuasion', ability: 'cha' },
      { id: 'pokemon_handling', name: 'Pokémon Handling', ability: 'wis' },
      { id: 'survival', name: 'Survival', ability: 'wis' },
      { id: 'intimidation', name: 'Intimidation', ability: 'cha' },
    ],
    starters: {
      method: 'pick',
      level: 5,
      species: [
        { id: 'bulbasaur', name: 'Bulbasaur', dex: 1, types: ['grass', 'poison'] },
        { id: 'charmander', name: 'Charmander', dex: 4, types: ['fire'] },
        { id: 'squirtle', name: 'Squirtle', dex: 7, types: ['water'] },
      ],
    },
    skin: { allowUpload: true, allowCopyFromUsername: true, maxBytes: 65536, size: 64 },
    hasTrainer: false,
    ...extra,
  };
}

/** Two of the nine classes, enough for the screens. */
export function classes(): TrainerClass[] {
  return [
    {
      id: 'ace_trainer',
      name: 'Ace Trainer',
      description: 'The battle all-rounder who gets the best from any Pokémon.',
      hitDie: 8,
      primaryAbility: 'cha',
      saves: ['dex', 'cha'],
      skillChoices: { pick: 2, from: ['pokemon_handling', 'persuasion', 'insight', 'athletics', 'intimidation'] },
      acBonus: 0,
      startingMoney: 0,
      startingItems: {},
      features: [
        { level: 1, id: 'commanding_presence', name: 'Commanding Presence', text: 'Your Pokémon hit 2 percentage points more often.', effects: [{ key: 'accuracy.bonusPp', value: 2, params: {} }] },
      ],
    },
    {
      id: 'hiker',
      name: 'Hiker',
      description: 'Tough mountain traveller; takes the hits.',
      hitDie: 10,
      primaryAbility: 'str',
      saves: ['str', 'con'],
      skillChoices: { pick: 2, from: ['athletics', 'survival', 'intimidation'] },
      acBonus: 0,
      startingMoney: 0,
      startingItems: {},
      features: [{ level: 1, id: 'tough_as_rock', name: 'Tough as Rock', text: 'You get 1 extra HP per level.', effects: [{ key: 'hp.perLevelBonus', value: 1, params: {} }] }],
    },
  ];
}

export function game(extra: Partial<Game> = {}): Game {
  return {
    id: 'g1',
    serverId: 's1',
    name: 'Kanto Weekend',
    state: 'lobby',
    lobbyOpen: true,
    ownerId: 'u0',
    myRoles: ['player'],
    myMembership: 'accepted',
    ruleSet: 'builtin:defaults',
    createdAt: '2026-11-09T10:00:00Z',
    ...extra,
  };
}

/** A level 1 Ace Trainer as the API sends it. */
export function trainer(extra: Partial<Trainer> = {}): Trainer {
  return {
    id: 't1',
    gameId: 'g1',
    userId: 'u1',
    ownerName: 'Anna',
    name: 'Ash_K',
    classId: 'ace_trainer',
    className: 'Ace Trainer',
    level: 1,
    xp: 0,
    xpForNextLevel: 50,
    abilityScores: { str: 15, dex: 14, con: 13, int: 11, wis: 10, cha: 9 },
    skills: ['pokemon_handling', 'insight'],
    hp: 9,
    hpMax: 9,
    money: 3000,
    items: { poke_ball: 5, potion: 3 },
    scoreMethod: 'pointBuy',
    starter: { species: 'charmander', name: 'Charmander', level: 5 },
    sheet: {
      proficiencyBonus: 2,
      armorClass: 12,
      modifiers: { str: 2, dex: 2, con: 1, int: 0, wis: 0, cha: -1 },
      savingThrows: { str: 2, dex: 4, con: 1, int: 0, wis: 0, cha: 1 },
      skillBonuses: { athletics: 2, insight: 2, persuasion: -1, pokemon_handling: 2, survival: 0, intimidation: -1 },
      proficient: ['insight', 'pokemon_handling'],
      handlingModifier: 2,
      features: classes()[0].features,
      effects: { 'accuracy.bonusPp': 2 },
      narrative: [],
    },
    ...extra,
  };
}
