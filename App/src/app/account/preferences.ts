import { Component, LOCALE_ID, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { Router } from '@angular/router';
import { problemText, toProblem } from '../core/api-error';
import { AuthService } from '../core/auth.service';
import { NAVIGATE, languageUrl } from '../core/browser';
import { THEMES, Theme, ThemeService } from '../core/theme';

/** The words for a theme, in the language of the build. */
export function themeLabel(theme: Theme): string {
  switch (theme) {
    case 'light':
      return $localize`:@@themeLight:Light`;
    case 'dark':
      return $localize`:@@themeDark:Dark`;
    default:
      return $localize`:@@themeSystem:Same as the device`;
  }
}

/** Display name, language and theme (chapters 11.7 and 11.8). */
@Component({
  selector: 'app-preferences',
  imports: [ReactiveFormsModule, MatCardModule, MatFormFieldModule, MatInputModule, MatButtonModule, MatButtonToggleModule],
  template: `
    <mat-card appearance="outlined">
      <mat-card-header>
        <mat-card-title i18n="@@accountProfileTitle">Profile and preferences</mat-card-title>
        <mat-card-subtitle data-testid="email">{{ auth.user()?.email }}</mat-card-subtitle>
      </mat-card-header>
      <mat-card-content class="stack">
        <form [formGroup]="form" (ngSubmit)="saveName()" class="row" novalidate>
          <mat-form-field appearance="outline">
            <mat-label i18n="@@fieldDisplayName">Display name</mat-label>
            <input matInput formControlName="displayName" maxlength="40" autocomplete="nickname" data-testid="display-name" />
            @if (form.controls.displayName.touched && form.controls.displayName.invalid) {
              <mat-error i18n="@@fieldDisplayNameRequired">Enter a name of up to 40 characters.</mat-error>
            }
          </mat-form-field>
          <button mat-stroked-button type="submit" data-testid="save-name" i18n="@@save">Save</button>
        </form>

        <div>
          <p class="label" id="language-label" i18n="@@accountLanguage">Language</p>
          <mat-button-toggle-group aria-labelledby="language-label" [value]="language" (change)="chooseLanguage($event.value)" data-testid="language">
            <mat-button-toggle value="en" data-testid="language-en">English</mat-button-toggle>
            <mat-button-toggle value="pt" data-testid="language-pt">Português</mat-button-toggle>
          </mat-button-toggle-group>
        </div>

        <div>
          <p class="label" id="theme-label" i18n="@@accountTheme">Theme</p>
          <mat-button-toggle-group aria-labelledby="theme-label" [value]="theme.theme()" (change)="theme.choose($event.value)" data-testid="theme">
            @for (t of themes; track t) {
              <mat-button-toggle [value]="t" [attr.data-testid]="'theme-' + t">{{ label(t) }}</mat-button-toggle>
            }
          </mat-button-toggle-group>
        </div>

        @if (saved()) {
          <p role="status" data-testid="saved" i18n="@@saved">Saved.</p>
        }
        @if (failure(); as text) {
          <p class="error" role="alert" data-testid="failure">{{ text }}</p>
        }
      </mat-card-content>
    </mat-card>
  `,
  styleUrl: './cards.scss',
})
export class Preferences {
  protected readonly auth = inject(AuthService);
  protected readonly theme = inject(ThemeService);
  private readonly navigate = inject(NAVIGATE);
  private readonly router = inject(Router);

  protected readonly language = inject(LOCALE_ID).slice(0, 2);
  protected readonly themes = THEMES;
  protected readonly label = themeLabel;

  protected readonly form = inject(FormBuilder).nonNullable.group({
    displayName: [this.auth.user()?.displayName ?? '', [Validators.required, Validators.maxLength(40), Validators.pattern(/\S/)]],
  });
  protected readonly saved = signal(false);
  protected readonly failure = signal<string | null>(null);

  protected saveName(): void {
    this.saved.set(false);
    this.failure.set(null);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.auth.updateProfile({ displayName: this.form.getRawValue().displayName.trim() }).subscribe({
      next: () => this.saved.set(true),
      error: (e) => this.failure.set(problemText(toProblem(e))),
    });
  }

  /** The language is the build the person is on: save the choice on the account, then open the same page in the other build. */
  protected chooseLanguage(language: 'en' | 'pt'): void {
    if (language === this.language) {
      return;
    }
    this.failure.set(null);
    this.auth.updateProfile({ locale: language }).subscribe({
      next: () => this.navigate(languageUrl(language, this.router.url)),
      error: (e) => this.failure.set(problemText(toProblem(e))),
    });
  }
}
