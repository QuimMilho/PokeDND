import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { catchError, forkJoin, of } from 'rxjs';
import { problemText, toProblem } from '../core/api-error';
import { runsGame } from '../games/game-state';
import { Game, GamesApi, RuleSet } from '../games/games-api';
import { ConfirmButton } from '../shared/confirm-button';
import { Filter, filterGroups, groupSettings, showValue } from './setting-model';
import { SettingEdit, SettingRow } from './setting-row';
import { Applied, Preset, Setting, SettingsApi } from './settings-api';

/**
 * `/games/:id/admin/settings` (chapters 11.4 and 12.1 to 12.3): every setting of Appendix A, in its groups, with the value, where the value
 * comes from and a button to put it back. Presets and saved rule sets are loaded from here, with a preview of what they would change.
 */
@Component({
  selector: 'app-settings-screen',
  imports: [FormsModule, RouterLink, MatCardModule, MatButtonModule, MatCheckboxModule, MatSlideToggleModule, MatExpansionModule, SettingRow, ConfirmButton],
  templateUrl: './settings-screen.html',
  styleUrl: './settings-screen.scss',
})
export class SettingsScreen {
  private readonly games = inject(GamesApi);
  private readonly api = inject(SettingsApi);
  private readonly id = inject(ActivatedRoute).snapshot.paramMap.get('id') ?? '';

  protected readonly game = signal<Game | null>(null);
  protected readonly settings = signal<Setting[]>([]);
  protected readonly ruleSetName = signal('');
  protected readonly loaded = signal(false);
  protected readonly failure = signal<string | null>(null);
  /** Problems the API reported for single settings, by key. */
  protected readonly problems = signal<Record<string, string>>({});

  protected readonly runs = computed(() => {
    const g = this.game();
    return g !== null && runsGame(g);
  });

  // ---- finding things
  protected readonly search = signal('');
  protected readonly changedOnly = signal(false);
  private readonly filter = computed<Filter>(() => ({ text: this.search(), changedOnly: this.changedOnly() }));
  protected readonly filtering = computed(() => this.search().trim() !== '' || this.changedOnly());
  private readonly groups = computed(() => groupSettings(this.settings()));
  protected readonly visible = computed(() => filterGroups(this.groups(), this.filter()));
  protected readonly shownCount = computed(() => this.visible().reduce((n, g) => n + g.settings.length, 0));
  protected readonly changedCount = computed(() => this.settings().filter((s) => s.overridden).length);
  private readonly opened = signal<ReadonlySet<string>>(new Set());

  // ---- presets and rule sets
  protected readonly presetsEnabled = signal(false);
  protected readonly presets = signal<Preset[]>([]);
  protected readonly ruleSets = signal<RuleSet[]>([]);
  /** The preset or rule set that is being previewed. */
  protected readonly candidate = signal<{ id: string; name: string } | null>(null);
  protected readonly preview = signal<Applied | null>(null);
  protected readonly discardOwn = signal(false);
  protected readonly applied = signal<string | null>(null);
  protected readonly saveName = signal('');
  protected readonly saved = signal<string | null>(null);

  protected readonly showValue = showValue;
  protected readonly resetAllLabel = $localize`:@@settingsResetAll:Reset all to default`;

  /** What the two pickers show: the candidate if it is one of theirs, nothing otherwise. */
  protected readonly presetValue = computed(() => {
    const id = this.candidate()?.id ?? '';
    return this.presets().some((p) => p.id === id) ? id : '';
  });
  protected readonly ruleSetValue = computed(() => {
    const id = this.candidate()?.id ?? '';
    return this.ruleSets().some((r) => r.id === id) ? id : '';
  });

  constructor() {
    forkJoin({
      game: this.games.game(this.id),
      settings: this.api.get(this.id),
      presets: this.api.presets(this.id).pipe(catchError(() => of({ enabled: false, presets: [] as Preset[] }))),
      ruleSets: this.games.ruleSets().pipe(catchError(() => of([] as RuleSet[]))),
    }).subscribe({
      next: ({ game, settings, presets, ruleSets }) => {
        this.game.set(game);
        this.settings.set(settings.settings);
        this.ruleSetName.set(settings.ruleSet.name);
        this.presetsEnabled.set(presets.enabled);
        this.presets.set(presets.presets);
        // the built-in ones are the presets, shown in their own list
        this.ruleSets.set(ruleSets.filter((r) => !r.id.startsWith('builtin:')));
        this.loaded.set(true);
      },
      error: (e) => {
        this.loaded.set(true);
        this.failure.set(problemText(toProblem(e)));
      },
    });
  }

  protected presetName(id: string): string {
    return this.presets().find((p) => p.id === id)?.name ?? '';
  }

  protected ruleSetPickName(id: string): string {
    return this.ruleSets().find((r) => r.id === id)?.name ?? '';
  }

  protected countText(): string {
    return $localize`:@@settingsCount:${this.shownCount()}:shown: of ${this.settings().length}:total: settings shown · ${this.changedCount()}:changed: changed`;
  }

  // ------------------------------------------------------------------ groups

  protected isOpen(group: string): boolean {
    return this.filtering() || this.opened().has(group);
  }

  protected setOpen(group: string, open: boolean): void {
    this.opened.update((set) => {
      const next = new Set(set);
      if (open) {
        next.add(group);
      } else {
        next.delete(group);
      }
      return next;
    });
  }

  protected openAll(open: boolean): void {
    this.opened.set(open ? new Set(this.groups().map((g) => g.name)) : new Set());
  }

  // ------------------------------------------------------------------ changing values

  protected save(edit: SettingEdit): void {
    this.problems.update((p) => without(p, edit.key));
    this.applied.set(null);
    this.api.change(this.id, { [edit.key]: edit.value }).subscribe({
      next: (changed) => this.merge(changed),
      error: (e) => this.problem(edit.key, e),
    });
  }

  protected reset(key: string): void {
    this.problems.update((p) => without(p, key));
    this.api.reset(this.id, [key]).subscribe({
      next: (changed) => this.merge(changed),
      error: (e) => this.problem(key, e),
    });
  }

  protected resetAll(): void {
    this.failure.set(null);
    this.problems.set({});
    this.api.reset(this.id, 'all').subscribe({
      next: (changed) => this.merge(changed),
      error: (e) => this.failure.set(problemText(toProblem(e))),
    });
  }

  protected undo(): void {
    this.failure.set(null);
    this.api.undo(this.id).subscribe({
      next: (changed) => this.merge(changed),
      error: (e) => this.failure.set(problemText(toProblem(e))),
    });
  }

  /** Puts the new state of some settings into the list. */
  private merge(changed: Setting[]): void {
    const byKey = new Map(changed.map((s) => [s.key, s]));
    this.settings.update((list) => list.map((s) => byKey.get(s.key) ?? s));
  }

  /** The API's own sentence is English but says why (for example the allowed range), so it is shown after the translated one. */
  private problem(key: string, error: unknown): void {
    const p = toProblem(error);
    const text = p.code === 'INVALID_SETTING' ? `${problemText(p)} ${p.message}` : problemText(p);
    this.problems.update((all) => ({ ...all, [key]: text }));
  }

  // ------------------------------------------------------------------ presets and rule sets

  /** A preset or a rule set was picked: ask what it would change. */
  protected choose(id: string, name: string): void {
    this.applied.set(null);
    this.failure.set(null);
    if (!id) {
      this.candidate.set(null);
      this.preview.set(null);
      return;
    }
    this.candidate.set({ id, name });
    this.api.apply(this.id, id, true, this.discardOwn()).subscribe({
      next: (preview) => this.preview.set(preview),
      error: (e) => {
        this.candidate.set(null);
        this.preview.set(null);
        this.failure.set(problemText(toProblem(e)));
      },
    });
  }

  protected setDiscardOwn(on: boolean): void {
    this.discardOwn.set(on);
    const c = this.candidate();
    if (c) {
      this.choose(c.id, c.name); // the preview depends on it
    }
  }

  protected cancelPreview(): void {
    this.candidate.set(null);
    this.preview.set(null);
  }

  protected apply(): void {
    const c = this.candidate();
    if (!c) {
      return;
    }
    this.failure.set(null);
    this.api.apply(this.id, c.id, false, this.discardOwn()).subscribe({
      next: (result) => {
        this.candidate.set(null);
        this.preview.set(null);
        this.applied.set(result.ruleSet.name);
        this.ruleSetName.set(result.ruleSet.name);
        this.reload();
      },
      error: (e) => this.failure.set(problemText(toProblem(e))),
    });
  }

  private reload(): void {
    this.api.get(this.id).subscribe({
      next: (s) => {
        this.settings.set(s.settings);
        this.ruleSetName.set(s.ruleSet.name);
      },
      error: (e) => this.failure.set(problemText(toProblem(e))),
    });
  }

  protected saveAsRuleSet(): void {
    const name = this.saveName().trim();
    this.saved.set(null);
    if (!name) {
      return;
    }
    this.failure.set(null);
    this.api.saveRuleSet(this.id, name).subscribe({
      next: (rs) => {
        this.saved.set(rs.name);
        this.saveName.set('');
        this.ruleSets.update((list) => [...list, { id: rs.id, scope: 'user', name: rs.name, description: null, editable: true }]);
      },
      error: (e) => this.failure.set(problemText(toProblem(e))),
    });
  }
}

function without(all: Record<string, string>, key: string): Record<string, string> {
  const { [key]: _removed, ...rest } = all;
  return rest;
}
