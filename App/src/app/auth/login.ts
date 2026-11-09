import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { oauthText, problemText, toProblem } from '../core/api-error';
import { API_BASE } from '../core/api-config';
import { AuthService } from '../core/auth.service';
import { emailMessage, passwordMessage } from './field-errors';
import { safeReturnUrl } from './return-url';

@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule, RouterLink, MatCardModule, MatFormFieldModule, MatInputModule, MatButtonModule, MatProgressSpinnerModule],
  templateUrl: './login.html',
  styleUrl: './auth-form.scss',
})
export class Login {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  protected readonly form = inject(FormBuilder).nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    // On login only "not empty" is checked: the server decides whether the password is right.
    password: ['', [Validators.required]],
  });
  protected readonly busy = signal(false);
  protected readonly failure = signal<string | null>(
    oauthText(this.route.snapshot.queryParamMap.get('oauth'), this.route.snapshot.queryParamMap.get('code')),
  );

  /** The "sign in with" buttons of this site (Discord, when it is set up). */
  protected readonly providers = toSignal(this.auth.providers(), { initialValue: [] as string[] });
  protected readonly discordUrl = `${inject(API_BASE)}/auth/oauth/discord/start`;

  protected readonly emailMessage = emailMessage;
  protected readonly passwordMessage = passwordMessage;

  protected submit(): void {
    this.failure.set(null);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.busy.set(true);
    const { email, password } = this.form.getRawValue();
    this.auth.login(email.trim(), password).subscribe({
      next: () => void this.router.navigateByUrl(safeReturnUrl(this.route.snapshot.queryParamMap.get('returnUrl'))),
      error: (e) => {
        this.busy.set(false);
        this.failure.set(problemText(toProblem(e)));
      },
    });
  }
}
