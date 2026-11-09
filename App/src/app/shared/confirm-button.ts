import { Component, input, output, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';

/**
 * A button for something that cannot be taken back. The first tap asks "Are you sure?" and the second one does it. (A browser
 * dialog would block automated tests and phones alike, so the question is on the page.)
 */
@Component({
  selector: 'app-confirm-button',
  imports: [MatButtonModule],
  template: `
    @if (asking()) {
      <span class="ask" role="group" [attr.aria-label]="label()">
        <span i18n="@@confirmQuestion">Are you sure?</span>
        <button mat-flat-button type="button" (click)="yes()" [attr.data-testid]="testId() + '-yes'" i18n="@@confirmYes">Yes</button>
        <button mat-stroked-button type="button" (click)="asking.set(false)" [attr.data-testid]="testId() + '-no'" i18n="@@confirmNo">No</button>
      </span>
    } @else {
      <button mat-stroked-button type="button" (click)="asking.set(true)" [disabled]="disabled()" [attr.data-testid]="testId()">{{ label() }}</button>
    }
  `,
  styles: `
    .ask {
      display: inline-flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 8px;
    }
  `,
})
export class ConfirmButton {
  readonly label = input.required<string>();
  readonly testId = input.required<string>();
  readonly disabled = input(false);
  readonly confirmed = output<void>();

  protected readonly asking = signal(false);

  protected yes(): void {
    this.asking.set(false);
    this.confirmed.emit();
  }
}
