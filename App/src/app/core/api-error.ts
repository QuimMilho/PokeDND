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
    default:
      return $localize`:@@errUnknown:Something went wrong. Try again.`;
  }
}
