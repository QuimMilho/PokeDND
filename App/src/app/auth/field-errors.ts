import { AbstractControl } from '@angular/forms';

/** Longest and shortest password the API accepts (`AuthService` in the API). */
export const PASSWORD_MIN = 10;
export const PASSWORD_MAX = 128;
export const NAME_MAX = 40;

/**
 * The message under a form field, in the language of this build, or `null` when the field is fine. Kept in plain functions (not in
 * templates) so that the unit tests can check both languages.
 */
export function emailMessage(c: AbstractControl): string | null {
  if (c.hasError('required')) {
    return $localize`:@@fieldEmailRequired:Enter your e-mail address.`;
  }
  if (c.hasError('email')) {
    return $localize`:@@fieldEmailInvalid:This does not look like an e-mail address.`;
  }
  return null;
}

export function passwordMessage(c: AbstractControl): string | null {
  if (c.hasError('required')) {
    return $localize`:@@fieldPasswordRequired:Enter a password.`;
  }
  if (c.hasError('minlength')) {
    return $localize`:@@fieldPasswordShort:The password needs at least ${PASSWORD_MIN}:min: characters.`;
  }
  if (c.hasError('maxlength')) {
    return $localize`:@@fieldPasswordLong:The password can have at most ${PASSWORD_MAX}:max: characters.`;
  }
  return null;
}

export function nameMessage(c: AbstractControl): string | null {
  if (c.hasError('required')) {
    return $localize`:@@fieldNameRequired:Enter a display name.`;
  }
  if (c.hasError('maxlength')) {
    return $localize`:@@fieldNameLong:The display name can have at most ${NAME_MAX}:max: characters.`;
  }
  return null;
}
