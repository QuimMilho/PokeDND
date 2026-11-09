import { Viewer, activeNavId, navigationFor } from './navigation';

const ids = (viewer: Viewer) => navigationFor(viewer).map((i) => i.id);
const person: Viewer = { signedIn: true, serverAdmin: false, siteAdmin: false };

describe('Navigation by role', () => {
  it('shows nothing before sign-in', () => {
    expect(ids({ signedIn: false, serverAdmin: false, siteAdmin: false })).toEqual([]);
  });

  it('gives a player the games, the cards and the account', () => {
    expect(ids(person)).toEqual(['games', 'cards', 'export', 'account']);
  });

  it('adds "New game" for a server admin and for the site admin, and for nobody else', () => {
    expect(ids({ ...person, serverAdmin: true })).toEqual(['games', 'new-game', 'cards', 'export', 'account']);
    expect(ids({ ...person, siteAdmin: true })).toEqual(['games', 'new-game', 'cards', 'export', 'account']);
    expect(ids(person)).not.toContain('new-game');
  });

  it('never shows "New game" to someone who is not signed in, whatever their roles say', () => {
    expect(ids({ signedIn: false, serverAdmin: true, siteAdmin: true })).toEqual([]);
  });

  it('has routes that exist and words in the language of the build', () => {
    const items = navigationFor({ ...person, siteAdmin: true });
    expect(items.map((i) => i.route)).toEqual(['/games', '/games/new', '/cards', '/cards/export', '/account']);
    expect(items.map((i) => i.label())).toEqual(['Games', 'New game', 'Cards', 'Export', 'Account']);
  });
});

describe('The highlighted menu entry', () => {
  const items = navigationFor({ ...person, serverAdmin: true });

  it('is the entry whose route the address is, or is inside of', () => {
    expect(activeNavId('/games', items)).toBe('games');
    expect(activeNavId('/games/5d1f/lobby', items)).toBe('games');
    expect(activeNavId('/cards/jobs', items)).toBe('cards');
    expect(activeNavId('/account?oauth=linked', items)).toBe('account');
  });

  it('prefers the longest route: /games/new lights "New game" only', () => {
    expect(activeNavId('/games/new', items)).toBe('new-game');
    expect(activeNavId('/cards/export', items)).toBe('export');
  });

  it('is none for the home page and for words that only start the same way', () => {
    expect(activeNavId('/', items)).toBeNull();
    expect(activeNavId('/gamesfoo', items)).toBeNull();
  });
});
