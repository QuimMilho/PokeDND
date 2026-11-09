import { HttpErrorResponse } from '@angular/common/http';

/** The body the API sends with every error (`ApiError` in the API: code, message, details). */
export interface ApiErrorBody {
  code: string;
  message: string;
  details?: Record<string, unknown>;
}

/** What the app knows about a failed call. */
export interface ApiProblem {
  /** The API's error code, or `NETWORK` when the API could not be reached, or `UNKNOWN`. */
  code: string;
  /** The HTTP status (0 when there was no answer). */
  status: number;
  /** The API's own message (always English). Screens prefer {@link problemText}. */
  message: string;
  details: Record<string, unknown>;
}

export function toProblem(error: unknown): ApiProblem {
  if (error instanceof HttpErrorResponse) {
    const body = error.error as Partial<ApiErrorBody> | null;
    if (error.status === 0) {
      return { code: 'NETWORK', status: 0, message: 'The server could not be reached.', details: {} };
    }
    if (body && typeof body === 'object' && typeof body.code === 'string') {
      return { code: body.code, status: error.status, message: body.message ?? error.message, details: body.details ?? {} };
    }
    return { code: 'UNKNOWN', status: error.status, message: error.message, details: {} };
  }
  return { code: 'UNKNOWN', status: 0, message: error instanceof Error ? error.message : String(error), details: {} };
}

/**
 * The text to show a person, in the language of this build. Known codes have their own translated sentence; anything else
 * falls back to a general sentence (the API's English message is never shown as it is).
 */
export function problemText(problem: ApiProblem): string {
  switch (problem.code) {
    case 'NETWORK':
      return $localize`:@@errNetwork:The server could not be reached. Check your connection and try again.`;
    case 'INVALID_CREDENTIALS':
      return $localize`:@@errInvalidCredentials:The e-mail address or the password is wrong.`;
    case 'EMAIL_NOT_VERIFIED':
      return $localize`:@@errEmailNotVerified:Verify your e-mail address first.`;
    case 'EMAIL_TAKEN':
      return $localize`:@@errEmailTaken:There is already an account with this e-mail address.`;
    case 'WEAK_PASSWORD':
      return $localize`:@@errWeakPassword:The password must have 10 to 128 characters.`;
    case 'INVALID_EMAIL':
      return $localize`:@@errInvalidEmail:This does not look like an e-mail address.`;
    case 'INVALID_NAME':
      return $localize`:@@errInvalidName:The display name can have at most 40 characters.`;
    case 'RATE_LIMITED':
    case 'TOO_MANY_REQUESTS':
      return $localize`:@@errRateLimited:Too many attempts. Wait a little and try again.`;
    case 'EMPTY_SELECTION':
      return $localize`:@@errEmptySelection:No card matches this selection. Choose a generation, a type or some cards.`;
    case 'EXPORT_TOO_LARGE':
      return $localize`:@@errExportTooLarge:This export has too many cards (${problem.details['printed']}:printed:, the limit is ${problem.details['limit']}:limit:). Choose fewer cards or fewer copies.`;
    case 'INVALID_EXPORT_OPTIONS':
      return $localize`:@@errExportOptions:These options do not fit together (card size, paper, copies). Check them and try again.`;
    case 'REFERENCE_DATA_MISSING':
      return $localize`:@@errNoData:The card data is not loaded on the server yet. Ask the administrator.`;
    case 'INVALID_THEME':
    case 'INVALID_LOCALE':
      return $localize`:@@errPreference:That choice is not available.`;
    case 'INVALID_CODE':
      return $localize`:@@errInvalidCode:This code is not right, or it was used already.`;
    case 'CODE_EXPIRED':
      return $localize`:@@errCodeExpired:This code has expired. Ask for a new one.`;
    case 'MC_ALREADY_LINKED':
      return $localize`:@@errMcLinked:This Minecraft account is linked to an account already.`;
    case 'INVALID_MINECRAFT_ACCOUNT':
      return $localize`:@@errMcInvalid:This is not a valid Minecraft account.`;
    case 'LAST_LOGIN_METHOD':
      return $localize`:@@errLastLogin:This is the only way you can log in. Set a password first (use "forgot password"), then unlink Discord.`;
    case 'DISCORD_ALREADY_LINKED':
      return $localize`:@@errDiscordLinked:This Discord account is linked to an account already, or another Discord account is linked to yours.`;
    case 'ALREADY_MEMBER':
      return $localize`:@@errAlreadyMember:You are in this game already.`;
    case 'KICKED':
      return $localize`:@@errKicked:The admin removed you from this game.`;
    case 'INVALID_JOIN_CODE':
      return $localize`:@@errJoinCode:This code is not right, or the lobby is closed.`;
    case 'INVALID_ROLE':
      return $localize`:@@errJoinRole:Join as a player or as a spectator.`;
    case 'INVALID_TRANSITION':
      return $localize`:@@errTransition:The game cannot do that in its present state.`;
    case 'SERVER_HAS_ACTIVE_GAME':
      return $localize`:@@errServerBusy:This server already has an active game. Pause or end it first.`;
    case 'NOT_PENDING':
      return $localize`:@@errNotPending:This request was answered already.`;
    case 'GAME_ARCHIVED':
      return $localize`:@@errArchived:This game is archived.`;
    case 'INVALID_SETTING':
      return $localize`:@@errSetting:This value is not allowed for this setting.`;
    case 'NOTHING_TO_UNDO':
      return $localize`:@@errNothingToUndo:There is no settings change to take back.`;
    case 'TRAINER_EXISTS':
      return $localize`:@@errTrainerExists:You have a trainer in this game already. Change or remove it instead.`;
    case 'GAME_STARTED':
      return $localize`:@@errGameStarted:The game has started, so only the admin can change this trainer now.`;
    case 'TRAINER_PROGRESSED':
      return $localize`:@@errTrainerProgressed:This trainer has played already, so its sheet can no longer be remade.`;
    case 'INVALID_STATE':
      return $localize`:@@errInvalidState:Trainers cannot be made or changed in a game that is over.`;
    case 'NO_MINECRAFT_ACCOUNT':
      return $localize`:@@errNoMinecraft:This game uses your Minecraft name as the trainer name. Link a Minecraft account first (on the account page).`;
    case 'INVALID_TRAINER':
      return $localize`:@@srvInvalid:The trainer does not follow the rules of this game. Check each step.`;
    case 'TRAINER_RULES_INVALID':
      return $localize`:@@errTrainerRules:The trainer settings of this game are not valid. Ask the admin.`;
    case 'SKIN_TOO_LARGE':
      return $localize`:@@errSkinTooLarge:This skin file is too big.`;
    case 'SKIN_NOT_PNG':
      return $localize`:@@skinNotPng:The skin must be a PNG picture.`;
    case 'SKIN_WRONG_SIZE':
      return $localize`:@@errSkinWrongSize:The skin must be 64×64 pixels.`;
    case 'SKIN_EMPTY':
    case 'SKIN_UNREADABLE':
      return $localize`:@@errSkinUnreadable:This skin file could not be read as a PNG picture.`;
    case 'INVALID_MODEL':
      return $localize`:@@errSkinModel:Choose classic or slim arms.`;
    case 'SKIN_UPLOAD_OFF':
      return $localize`:@@errSkinUploadOff:This game does not allow uploaded skins.`;
    case 'SKIN_COPY_OFF':
      return $localize`:@@errSkinCopyOff:This game does not allow copying a skin from a Minecraft name.`;
    case 'INVALID_MINECRAFT_NAME':
      return $localize`:@@skinBadName:A Minecraft name has 3 to 16 letters, digits or underscores.`;
    case 'MINECRAFT_USER_NOT_FOUND':
      return $localize`:@@errMcUserNotFound:There is no Minecraft account with that name.`;
    case 'MINECRAFT_NO_SKIN':
      return $localize`:@@errMcNoSkin:That account has no custom skin.`;
    case 'MOJANG_UNAVAILABLE':
      return $localize`:@@errMojang:Mojang could not be reached. Try again later, or upload a skin.`;
    case 'FORBIDDEN':
      return $localize`:@@errForbidden:You are not allowed to do that.`;
    case 'NOT_FOUND':
      return $localize`:@@errNotFound:This does not exist, or you cannot see it.`;
    default:
      return $localize`:@@errUnknown:Something went wrong. Try again.`;
  }
}

/**
 * The sentence for what Discord sign-in or linking did, from the `oauth` and `code` values that the API puts in the address when it
 * sends the browser back. Null when the address says nothing about it.
 */
export function oauthText(oauth: string | null, code: string | null): string | null {
  if (oauth === 'linked') {
    return $localize`:@@oauthLinked:Discord is linked to your account.`;
  }
  if (oauth !== 'error') {
    return null;
  }
  switch (code) {
    case 'OAUTH_DENIED':
      return $localize`:@@oauthDenied:You did not allow PokeDnD to use your Discord account.`;
    case 'DISCORD_ALREADY_LINKED':
      return $localize`:@@oauthAlreadyLinked:This Discord account belongs to another PokeDnD account.`;
    case 'DISCORD_EMAIL_UNVERIFIED':
      return $localize`:@@oauthEmailUnverified:Discord has no verified e-mail address for this account. Verify it at Discord and try again.`;
    case 'INVALID_STATE':
      return $localize`:@@oauthState:The sign-in took too long or came from another browser. Try again.`;
    default:
      return $localize`:@@oauthFailed:Signing in with Discord did not work. Try again.`;
  }
}
