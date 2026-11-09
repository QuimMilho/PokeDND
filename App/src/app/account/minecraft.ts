import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { problemText, toProblem } from '../core/api-error';
import { AccountApi, LinkCode, McLink } from './account-api';

/**
 * The Minecraft accounts of this account (chapter 3.2). A Minecraft account is linked with a short code, in either direction: the
 * player types the code that `/pokednd link` printed in Minecraft here, or asks for a code here and types it in Minecraft.
 */
@Component({
  selector: 'app-minecraft',
  imports: [ReactiveFormsModule, MatCardModule, MatFormFieldModule, MatInputModule, MatButtonModule, DatePipe],
  template: `
    <mat-card appearance="outlined">
      <mat-card-header>
        <mat-card-title i18n="@@minecraftTitle">Minecraft accounts</mat-card-title>
        <mat-card-subtitle i18n="@@minecraftSubtitle">Link your Minecraft account so the game knows who you are.</mat-card-subtitle>
      </mat-card-header>
      <mat-card-content class="stack">
        @if (accounts().length) {
          <ul class="list" data-testid="minecraft-accounts">
            @for (a of accounts(); track a.mcUuid) {
              <li [attr.data-testid]="'mc-' + a.mcName">
                <span>
                  <strong>{{ a.mcName }}</strong><br />
                  <span class="muted" i18n="@@minecraftLinkedOn">Linked {{ a.linkedAt | date: 'mediumDate' }}</span>
                </span>
                <button mat-stroked-button type="button" (click)="unlink(a)" [attr.data-testid]="'unlink-' + a.mcName" i18n="@@minecraftUnlink">Unlink</button>
              </li>
            }
          </ul>
        } @else {
          <p class="muted" data-testid="no-minecraft" i18n="@@minecraftNone">No Minecraft account is linked yet.</p>
        }

        <form [formGroup]="form" (ngSubmit)="link()" class="row" novalidate>
          <mat-form-field appearance="outline">
            <mat-label i18n="@@minecraftCodeField">Code from Minecraft</mat-label>
            <input matInput formControlName="code" autocomplete="off" autocapitalize="characters" maxlength="12" data-testid="link-code" />
            <mat-hint i18n="@@minecraftCodeHint">Type /pokednd link in Minecraft, then enter the code here.</mat-hint>
          </mat-form-field>
          <button mat-flat-button type="submit" data-testid="link" i18n="@@minecraftLink">Link</button>
        </form>

        <div>
          <button mat-stroked-button type="button" (click)="newCode()" data-testid="new-code" i18n="@@minecraftNewCode">Show me a code to type in Minecraft</button>
          @if (issued(); as c) {
            <p data-testid="issued">
              <span class="code" data-testid="issued-code">{{ c.code }}</span><br />
              <span class="muted" i18n="@@minecraftCodeUntil">Valid until {{ c.expiresAt | date: 'shortTime' }}. Enter this code in Minecraft with the /pokednd link command.</span>
            </p>
          }
        </div>

        @if (linked(); as name) {
          <p role="status" data-testid="linked" i18n="@@minecraftLinkedNow">Linked: {{ name }}.</p>
        }
        @if (failure(); as text) {
          <p class="error" role="alert" data-testid="failure">{{ text }}</p>
        }
      </mat-card-content>
    </mat-card>
  `,
  styleUrl: './cards.scss',
})
export class Minecraft {
  private readonly api = inject(AccountApi);

  protected readonly form = inject(FormBuilder).nonNullable.group({ code: ['', [Validators.required, Validators.pattern(/\S/)]] });
  protected readonly accounts = signal<McLink[]>([]);
  protected readonly issued = signal<LinkCode | null>(null);
  protected readonly linked = signal<string | null>(null);
  protected readonly failure = signal<string | null>(null);

  constructor() {
    this.load();
  }

  private load(): void {
    this.api.minecraftAccounts().subscribe({
      next: (list) => this.accounts.set(list),
      error: (e) => this.failure.set(problemText(toProblem(e))),
    });
  }

  protected link(): void {
    this.failure.set(null);
    this.linked.set(null);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.failure.set($localize`:@@errCodeRequired:Enter the code that Minecraft showed.`);
      return;
    }
    this.api.linkMinecraft(this.form.getRawValue().code.trim()).subscribe({
      next: (account) => {
        this.accounts.update((list) => [...list.filter((a) => a.mcUuid !== account.mcUuid), account]);
        this.linked.set(account.mcName);
        this.form.reset();
      },
      error: (e) => this.failure.set(problemText(toProblem(e))),
    });
  }

  protected newCode(): void {
    this.failure.set(null);
    this.api.newLinkCode().subscribe({
      next: (code) => this.issued.set(code),
      error: (e) => this.failure.set(problemText(toProblem(e))),
    });
  }

  protected unlink(account: McLink): void {
    this.failure.set(null);
    this.api.unlinkMinecraft(account.mcUuid).subscribe({
      next: () => this.accounts.update((list) => list.filter((a) => a.mcUuid !== account.mcUuid)),
      error: (e) => this.failure.set(problemText(toProblem(e))),
    });
  }
}
