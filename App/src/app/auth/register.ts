import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Router, RouterLink } from '@angular/router';
import { problemText, toProblem } from '../core/api-error';
import { AuthService } from '../core/auth.service';
import { NAME_MAX, PASSWORD_MAX, PASSWORD_MIN, emailMessage, nameMessage, passwordMessage } from './field-errors';

@Component({
  selector: 'app-register',
  imports: [ReactiveFormsModule, RouterLink, MatCardModule, MatFormFieldModule, MatInputModule, MatButtonModule, MatProgressSpinnerModule],
  templateUrl: './register.html',
  styleUrl: './auth-form.scss',
})
export class Register {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly form = inject(FormBuilder).nonNullable.group({
    displayName: ['', [Validators.required, Validators.maxLength(NAME_MAX)]],
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(PASSWORD_MIN), Validators.maxLength(PASSWORD_MAX)]],
  });
  protected readonly busy = signal(false);
  protected readonly failure = signal<string | null>(null);

  protected readonly emailMessage = emailMessage;
  protected readonly passwordMessage = passwordMessage;
  protected readonly nameMessage = nameMessage;

  protected submit(): void {
    this.failure.set(null);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.busy.set(true);
    const { displayName, email, password } = this.form.getRawValue();
    this.auth.register(email.trim(), password, displayName.trim()).subscribe({
      next: () => void this.router.navigateByUrl('/'),
      error: (e) => {
        this.busy.set(false);
        this.failure.set(problemText(toProblem(e)));
      },
    });
  }
}
