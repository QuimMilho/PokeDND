import { actionLabel, actionsFor, membershipLabel, runsGame, stateLabel } from './game-state';
import { GameStateId } from './games-api';

const ids = (state: GameStateId) => actionsFor(state).map((a) => a.id);

describe('Game state buttons (chapter 3.4)', () => {
  it('follows the diagram, state by state', () => {
    expect(ids('draft')).toEqual(['open-lobby', 'archive']);
    expect(ids('lobby')).toEqual(['start']);
    expect(ids('running')).toEqual(['pause', 'open-lobby', 'end']);
    expect(ids('paused')).toEqual(['resume', 'end']);
    expect(ids('ended')).toEqual(['archive']);
    expect(ids('archived')).toEqual([]);
  });

  it('asks twice before the steps that cannot be taken back', () => {
    const destructive = (['draft', 'lobby', 'running', 'paused', 'ended'] as GameStateId[]).flatMap((s) => actionsFor(s).filter((a) => a.destructive).map((a) => `${s}:${a.id}`));
    expect(destructive).toEqual(['draft:archive', 'running:end', 'paused:end', 'ended:archive']);
  });

  it('uses the commands of the API', () => {
    const commands = new Set((['draft', 'lobby', 'running', 'paused', 'ended'] as GameStateId[]).flatMap((s) => actionsFor(s).map((a) => a.command)));
    expect(Array.from(commands).sort()).toEqual(['archive', 'end', 'lobby/open', 'pause', 'resume', 'start']);
  });

  it('has words for every state and every button', () => {
    for (const s of ['draft', 'lobby', 'running', 'paused', 'ended', 'archived'] as GameStateId[]) {
      expect(stateLabel(s)).toBeTruthy();
      for (const a of actionsFor(s)) {
        expect(actionLabel(a.id, s)).toBeTruthy();
      }
    }
    expect(actionLabel('open-lobby', 'draft')).toBe('Open the lobby');
    expect(actionLabel('open-lobby', 'running')).toBe('Reopen the lobby (late joiners)');
    expect(actionLabel('archive', 'draft')).toBe('Discard this game');
    expect(actionLabel('archive', 'ended')).toBe('Archive');
  });

  it('knows who runs the game: game admins and the site admin, not server admins, players or spectators', () => {
    expect(runsGame({ myRoles: ['game_admin'] })).toBeTrue();
    expect(runsGame({ myRoles: ['site_admin'] })).toBeTrue();
    expect(runsGame({ myRoles: ['server_admin'] })).toBeFalse();
    expect(runsGame({ myRoles: ['player'] })).toBeFalse();
    expect(runsGame({ myRoles: [] })).toBeFalse();
  });

  it('explains where a request stands', () => {
    expect(membershipLabel('requested')).toBe('Waiting for the admin to accept you');
    expect(membershipLabel('rejected')).toContain('did not accept');
    expect(membershipLabel('accepted')).toBeNull();
    expect(membershipLabel(undefined)).toBeNull();
  });
});
