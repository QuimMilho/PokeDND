import { Component, computed, input, output, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatRadioModule } from '@angular/material/radio';
import { Problem, SkinChoice } from './wizard-model';

const PNG_SIGNATURE = [0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a];

/**
 * What is wrong with a skin file, judged from its first bytes (chapter 3.6): it must be a PNG of 64×64 pixels and not too big. The
 * API checks the same things again and then cleans the picture; this only saves a round trip.
 */
export function skinFileProblem(head: Uint8Array, size: number, maxBytes: number, pixels = 64): Problem | null {
  if (size > maxBytes) {
    return { code: 'SKIN_TOO_LARGE', text: $localize`:@@skinTooLarge:This file is too big (${size}:size: bytes, the limit is ${maxBytes}:max: bytes).` };
  }
  if (head.length < 24 || PNG_SIGNATURE.some((b, i) => head[i] !== b)) {
    return { code: 'SKIN_NOT_PNG', text: $localize`:@@skinNotPng:The skin must be a PNG picture.` };
  }
  const view = new DataView(head.buffer, head.byteOffset, head.byteLength);
  const width = view.getUint32(16);
  const height = view.getUint32(20);
  if (width !== pixels || height !== pixels) {
    return { code: 'SKIN_WRONG_SIZE', text: $localize`:@@skinWrongSize:The skin must be ${pixels}:px:×${pixels}:px2: pixels, not ${width}:w:×${height}:h:.` };
  }
  return null;
}

/** A Minecraft username: 3 to 16 letters, digits or underscores. */
export function isMinecraftName(name: string): boolean {
  return /^[A-Za-z0-9_]{3,16}$/.test(name);
}

/**
 * Choosing a skin (chapter 3.6): none, a 64×64 PNG from the computer (classic or slim arms), or the skin of a Minecraft account by
 * its name. It only reports the choice; whoever uses it sends it to the API.
 */
@Component({
  selector: 'app-skin-picker',
  imports: [MatRadioModule, MatFormFieldModule, MatInputModule, MatButtonModule],
  template: `
    <fieldset class="picker">
      <legend i18n="@@skinLegend">Skin</legend>
      <mat-radio-group [value]="mode()" (change)="setMode($event.value)" aria-labelledby="skin-help">
        <mat-radio-button value="none" data-testid="skin-none" i18n="@@skinNone">No skin (the default)</mat-radio-button>
        @if (allowUpload()) {
          <mat-radio-button value="upload" data-testid="skin-mode-upload" i18n="@@skinUpload">Upload a PNG</mat-radio-button>
        }
        @if (allowCopy()) {
          <mat-radio-button value="username" data-testid="skin-mode-username" i18n="@@skinCopy">Copy from a Minecraft name</mat-radio-button>
        }
      </mat-radio-group>
      <p id="skin-help" class="muted" i18n="@@skinHelp">A skin is a 64×64 PNG. It is checked and cleaned by the server.</p>

      @if (mode() === 'upload') {
        <label class="file">
          <span i18n="@@skinFile">PNG file</span>
          <input type="file" accept="image/png" (change)="onFile($event)" data-testid="skin-file" />
        </label>
        <mat-radio-group [value]="model()" (change)="setModel($event.value)" aria-label="Arms" i18n-aria-label="@@skinArmsLabel" class="arms">
          <mat-radio-button value="classic" data-testid="skin-classic" i18n="@@skinClassic">Classic arms (Steve)</mat-radio-button>
          <mat-radio-button value="slim" data-testid="skin-slim" i18n="@@skinSlim">Slim arms (Alex)</mat-radio-button>
        </mat-radio-group>
      }
      @if (mode() === 'username') {
        <mat-form-field appearance="outline">
          <mat-label i18n="@@skinUsername">Minecraft name</mat-label>
          <input matInput maxlength="16" autocomplete="off" [value]="username()" (input)="setUsername($any($event.target).value)" data-testid="skin-username" />
        </mat-form-field>
      }
      @if (problem(); as p) {
        <p class="error" role="alert" data-testid="skin-problem">{{ p.text }}</p>
      }
      @if (previewName(); as n) {
        <p class="muted" data-testid="skin-chosen">{{ n }}</p>
      }
    </fieldset>
  `,
  styles: `
    .picker {
      border: 1px solid var(--mat-sys-outline-variant);
      border-radius: 8px;
      padding: 8px 12px 12px;
      display: flex;
      flex-direction: column;
      gap: 8px;
      min-width: 0;
    }
    legend {
      padding: 0 4px;
      font: var(--mat-sys-title-small);
    }
    p {
      margin: 0;
    }
    .file {
      display: flex;
      flex-direction: column;
      gap: 4px;
    }
    .arms {
      display: flex;
      flex-direction: column;
    }
  `,
})
export class SkinPicker {
  readonly allowUpload = input(true);
  readonly allowCopy = input(true);
  readonly maxBytes = input(65536);
  /** The choice made so far, to show it again when the picker is drawn again. */
  readonly initial = input<SkinChoice>({ kind: 'none' });
  readonly changed = output<SkinChoice>();

  protected readonly mode = signal<'none' | 'upload' | 'username'>('none');
  protected readonly model = signal<'classic' | 'slim'>('classic');
  protected readonly username = signal('');
  protected readonly problem = signal<Problem | null>(null);
  private readonly file = signal<File | null>(null);

  protected readonly previewName = computed(() => {
    const f = this.file();
    return this.mode() === 'upload' && f && !this.problem() ? f.name : null;
  });

  ngOnInit(): void {
    const start = this.initial();
    if (start.kind === 'upload') {
      this.mode.set('upload');
      this.file.set(start.file);
      this.model.set(start.model);
    } else if (start.kind === 'username') {
      this.mode.set('username');
      this.username.set(start.username);
    }
  }

  protected setMode(mode: 'none' | 'upload' | 'username'): void {
    this.mode.set(mode);
    this.problem.set(null);
    this.emit();
  }

  protected setModel(model: 'classic' | 'slim'): void {
    this.model.set(model);
    this.emit();
  }

  protected setUsername(name: string): void {
    this.username.set(name.trim());
    this.emit();
  }

  protected async onFile(event: Event): Promise<void> {
    const file = (event.target as HTMLInputElement).files?.[0] ?? null;
    this.file.set(null);
    this.problem.set(null);
    if (file) {
      const head = new Uint8Array(await file.slice(0, 24).arrayBuffer());
      const problem = skinFileProblem(head, file.size, this.maxBytes());
      this.problem.set(problem);
      this.file.set(problem ? null : file);
    }
    this.emit();
  }

  private emit(): void {
    switch (this.mode()) {
      case 'upload': {
        const file = this.file();
        this.changed.emit(file ? { kind: 'upload', file, model: this.model() } : { kind: 'none' });
        break;
      }
      case 'username': {
        const name = this.username();
        if (name && !isMinecraftName(name)) {
          this.problem.set({ code: 'INVALID_MINECRAFT_NAME', text: $localize`:@@skinBadName:A Minecraft name has 3 to 16 letters, digits or underscores.` });
          this.changed.emit({ kind: 'none' });
        } else {
          this.problem.set(null);
          this.changed.emit(name ? { kind: 'username', username: name } : { kind: 'none' });
        }
        break;
      }
      default:
        this.changed.emit({ kind: 'none' });
    }
  }
}
