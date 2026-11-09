import { Component, computed, input, output, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { Parsed, parseInput, sameValue, showValue, toInputText, tokens } from './setting-model';
import { Setting } from './settings-api';

/** A change the person made to one setting. */
export interface SettingEdit {
  key: string;
  value: unknown;
}

/** The words that say where a value comes from (chapter 12.2). */
export function sourceLabel(source: Setting['source']): string {
  switch (source) {
    case 'game':
      return $localize`:@@sourceGame:Changed in this game`;
    case 'ruleSet':
      return $localize`:@@sourceRuleSet:From the rule set`;
    default:
      return $localize`:@@sourceDefault:Default`;
  }
}

/**
 * One line of the settings screen: the key, what it does, a control that fits the kind of value, the default marker (where the value
 * comes from) and the "reset to default" button.
 */
@Component({
  selector: 'app-setting-row',
  imports: [MatSlideToggleModule, MatCheckboxModule, MatButtonModule],
  template: `
    <div class="row" [class.changed]="setting().overridden" [attr.data-key]="setting().key" [attr.data-source]="setting().source">
      <div class="info">
        <code class="key">{{ setting().key }}</code>
        <span class="marker" [attr.data-testid]="'source-' + setting().key">{{ source() }}</span>
        <p class="description">{{ setting().description }}</p>
        <p class="default muted" [attr.data-testid]="'default-' + setting().key">
          <ng-container i18n="@@settingDefault">Default:</ng-container> {{ defaultText() }}
        </p>
      </div>
      <div class="control">
        @switch (setting().type) {
          @case ('bool') {
            <mat-slide-toggle
              [checked]="setting().value === true"
              (change)="commit($event.checked)"
              [attr.aria-label]="setting().key"
              [attr.data-testid]="'input-' + setting().key"
            />
          }
          @case ('enum') {
            <select [attr.aria-label]="setting().key" (change)="commit($any($event.target).value)" [attr.data-testid]="'input-' + setting().key">
              @for (option of setting().allowed ?? []; track option) {
                <option [value]="option" [selected]="option === setting().value">{{ option }}</option>
              }
            </select>
          }
          @case ('csv') {
            @if (setting().allowed; as allowed) {
              <div class="checks" role="group" [attr.aria-label]="setting().key" [attr.data-testid]="'input-' + setting().key">
                @for (option of allowed; track option) {
                  <mat-checkbox [checked]="chosen().includes(option)" (change)="toggle(option, $event.checked)" [attr.data-testid]="'option-' + setting().key + '-' + option">{{ option }}</mat-checkbox>
                }
              </div>
            } @else {
              <input type="text" [attr.aria-label]="setting().key" [value]="text()" (change)="commitText($any($event.target).value)" [attr.data-testid]="'input-' + setting().key" />
            }
          }
          @case ('int') {
            <input type="number" step="1" [attr.min]="setting().min" [attr.max]="setting().max" [attr.aria-label]="setting().key" [value]="text()" (change)="commitText($any($event.target).value)" [attr.data-testid]="'input-' + setting().key" />
          }
          @case ('decimal') {
            <input type="number" step="any" [attr.min]="setting().min" [attr.max]="setting().max" [attr.aria-label]="setting().key" [value]="text()" (change)="commitText($any($event.target).value)" [attr.data-testid]="'input-' + setting().key" />
          }
          @case ('json') {
            <textarea rows="3" [attr.aria-label]="setting().key" [value]="text()" (change)="commitText($any($event.target).value)" [attr.data-testid]="'input-' + setting().key"></textarea>
          }
          @case ('data') {
            <textarea rows="3" [attr.aria-label]="setting().key" [value]="text()" (change)="commitText($any($event.target).value)" [attr.data-testid]="'input-' + setting().key"></textarea>
          }
          @default {
            <input type="text" [attr.aria-label]="setting().key" [value]="text()" (change)="commitText($any($event.target).value)" [attr.data-testid]="'input-' + setting().key" />
          }
        }
        @if (setting().source === 'game') {
          <button mat-button type="button" (click)="reset.emit(setting().key)" [attr.data-testid]="'reset-' + setting().key" i18n="@@settingReset">Reset to default</button>
        }
      </div>
      @if (shownError(); as message) {
        <p class="error" role="alert" [attr.data-testid]="'error-' + setting().key">{{ message }}</p>
      }
    </div>
  `,
  styleUrl: './setting-row.scss',
})
export class SettingRow {
  readonly setting = input.required<Setting>();
  /** A problem the API reported for this setting. */
  readonly problem = input<string | null>(null);

  readonly save = output<SettingEdit>();
  readonly reset = output<string>();

  /** What is wrong with what the person typed, before anything was sent. */
  private readonly typingError = signal<string | null>(null);

  protected readonly shownError = computed(() => this.typingError() ?? this.problem());
  protected readonly source = computed(() => sourceLabel(this.setting().source));
  protected readonly text = computed(() => toInputText(this.setting()));
  protected readonly chosen = computed(() => tokens(this.setting().value));
  protected readonly defaultText = computed(() => {
    const s = this.setting();
    return s.defaultValue === undefined || s.defaultValue === null ? $localize`:@@settingBuiltInData:built-in data` : showValue(s.defaultValue);
  });

  protected commit(value: unknown): void {
    this.typingError.set(null);
    if (!sameValue(value, this.setting().value)) {
      this.save.emit({ key: this.setting().key, value });
    }
  }

  protected commitText(raw: string): void {
    const parsed: Parsed = parseInput(this.setting(), raw);
    if (!parsed.ok) {
      this.typingError.set(parsed.error);
      return;
    }
    this.commit(parsed.value);
  }

  /** A box of a list setting was ticked or cleared: the value is the ticked boxes, in the order of the registry. */
  protected toggle(option: string, on: boolean): void {
    const now = new Set(this.chosen());
    if (on) {
      now.add(option);
    } else {
      now.delete(option);
    }
    this.commit((this.setting().allowed ?? []).filter((o) => now.has(o)).join(','));
  }
}
