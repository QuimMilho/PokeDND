import { Game, GameCommand, GameStateId } from './games-api';

/** What a game admin can ask for in a state. `archive` is a separate call (`DELETE /games/{id}`). */
export interface Action {
  id: 'open-lobby' | 'start' | 'pause' | 'resume' | 'end' | 'archive';
  command: GameCommand | 'archive';
  /** Needs a second tap ("Are you sure?"): the step cannot be taken back. */
  destructive: boolean;
}

/**
 * The buttons of the game page for a person who runs the game. They follow the diagram of chapter 3.4:
 * Draft opens the lobby; the lobby starts the game; a running game pauses, ends, or reopens the lobby for late joiners;
 * a paused game resumes or ends; an ended game is archived. A draft can also be discarded (archived).
 */
export function actionsFor(state: GameStateId): Action[] {
  switch (state) {
    case 'draft':
      return [
        { id: 'open-lobby', command: 'lobby/open', destructive: false },
        { id: 'archive', command: 'archive', destructive: true },
      ];
    case 'lobby':
      // "start the game" and "close the lobby" are the same step (chapter 3.4); one button does both
      return [{ id: 'start', command: 'start', destructive: false }];
    case 'running':
      return [
        { id: 'pause', command: 'pause', destructive: false },
        { id: 'open-lobby', command: 'lobby/open', destructive: false },
        { id: 'end', command: 'end', destructive: true },
      ];
    case 'paused':
      return [
        { id: 'resume', command: 'resume', destructive: false },
        { id: 'end', command: 'end', destructive: true },
      ];
    case 'ended':
      return [{ id: 'archive', command: 'archive', destructive: true }];
    default:
      return [];
  }
}

/** The words on a button. */
export function actionLabel(id: Action['id'], state: GameStateId): string {
  switch (id) {
    case 'open-lobby':
      return state === 'running' ? $localize`:@@actionReopenLobby:Reopen the lobby (late joiners)` : $localize`:@@actionOpenLobby:Open the lobby`;
    case 'start':
      return $localize`:@@actionStart:Start the game`;
    case 'pause':
      return $localize`:@@actionPause:Pause`;
    case 'resume':
      return $localize`:@@actionResume:Resume`;
    case 'end':
      return $localize`:@@actionEnd:End the game`;
    default:
      return state === 'draft' ? $localize`:@@actionDiscard:Discard this game` : $localize`:@@actionArchive:Archive`;
  }
}

/** The name of a state, in the language of the build. */
export function stateLabel(state: GameStateId): string {
  switch (state) {
    case 'draft':
      return $localize`:@@stateDraft:Draft`;
    case 'lobby':
      return $localize`:@@stateLobby:Lobby open`;
    case 'running':
      return $localize`:@@stateRunning:Running`;
    case 'paused':
      return $localize`:@@statePaused:Paused`;
    case 'ended':
      return $localize`:@@stateEnded:Ended`;
    default:
      return $localize`:@@stateArchived:Archived`;
  }
}

/** Does this person run the game? Only they get the buttons, the join code and the settings. */
export function runsGame(game: Pick<Game, 'myRoles'>): boolean {
  return game.myRoles.includes('game_admin') || game.myRoles.includes('site_admin');
}

/** The sentence that says where a person stands in a game they asked to join. */
export function membershipLabel(status: string | undefined): string | null {
  switch (status) {
    case 'requested':
      return $localize`:@@membershipRequested:Waiting for the admin to accept you`;
    case 'rejected':
      return $localize`:@@membershipRejected:The admin did not accept your request`;
    case 'kicked':
      return $localize`:@@membershipKicked:The admin removed you`;
    default:
      return null;
  }
}
