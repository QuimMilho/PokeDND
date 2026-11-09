import { HttpContextToken, HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, switchMap, throwError } from 'rxjs';
import { API_BASE } from './api-config';
import { AuthService } from './auth.service';

/** Set on the one retry after a refresh, so that a second 401 is an answer and not a reason to try again. */
const RETRIED = new HttpContextToken<boolean>(() => false);

/** The calls that sign in or out themselves: no bearer token, and a 401 there is just an answer. */
const AUTH_CALL = /\/auth\/(register|login|refresh|logout)$/;

/**
 * For calls to the API: adds the access token, and when the API answers 401 asks for a new token once (with the refresh cookie) and
 * repeats the call once. If the refresh fails too, the person is signed out and sent to the login screen.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const base = inject(API_BASE);
  const auth = inject(AuthService);
  if (!req.url.startsWith(base) || AUTH_CALL.test(req.url.split('?')[0])) {
    return next(req);
  }

  const withToken = (r: HttpRequest<unknown>, token: string | null) =>
    token ? r.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : r;

  return next(withToken(req, auth.accessToken())).pipe(
    catchError((error: unknown) => {
      if (!(error instanceof HttpErrorResponse) || error.status !== 401 || req.context.get(RETRIED)) {
        return throwError(() => error);
      }
      return auth.refresh().pipe(
        catchError(() => {
          auth.expire();
          return throwError(() => error);
        }),
        switchMap((token) => next(withToken(req.clone({ context: req.context.set(RETRIED, true) }), token))),
      );
    }),
  );
};
