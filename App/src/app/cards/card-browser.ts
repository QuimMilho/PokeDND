import { Component, DestroyRef, effect, inject, signal, untracked } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatChipSelectionChange, MatChipsModule } from '@angular/material/chips';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { RouterLink } from '@angular/router';
import { Subscription, debounceTime, distinctUntilChanged, map } from 'rxjs';
import { problemText, toProblem } from '../core/api-error';
import { ReferenceApi } from './reference-api';
import { SpeciesFilter, isFiltered, normaliseQuery, withValue } from './species-filter';
import { GENERATIONS, SpeciesSummary, TYPES, artUrl, typeColour, typeName } from './species';

const PAGE_SIZE = 60;

/** Species with their art, filters for generation and type, and a search by name (chapter 9.9, phase 26). */
@Component({
  selector: 'app-card-browser',
  imports: [
    FormsModule,
    RouterLink,
    MatButtonModule,
    MatChipsModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSlideToggleModule,
  ],
  templateUrl: './card-browser.html',
  styleUrl: './card-browser.scss',
})
export class CardBrowser {
  private readonly api = inject(ReferenceApi);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly generations = GENERATIONS;
  protected readonly types = TYPES;
  protected readonly typeName = typeName;
  protected readonly typeColour = typeColour;

  protected readonly selectedGenerations = signal<ReadonlySet<number>>(new Set());
  protected readonly selectedTypes = signal<ReadonlySet<string>>(new Set());
  protected readonly primaryTypeOnly = signal(false);
  /** What is typed in the search box. */
  protected readonly query = signal('');
  /** The search text a moment after the person stopped typing; this is what the list is filtered by. */
  private readonly settledQuery = signal('');

  protected readonly items = signal<SpeciesSummary[]>([]);
  protected readonly next = signal<string | null>(null);
  protected readonly loading = signal(false);
  protected readonly loadingMore = signal(false);
  protected readonly failure = signal<string | null>(null);

  private pending?: Subscription;

  constructor() {
    toObservable(this.query)
      .pipe(debounceTime(250), map(normaliseQuery), distinctUntilChanged(), takeUntilDestroyed(this.destroyRef))
      .subscribe((q) => this.settledQuery.set(q));

    // A new filter starts the list again from the first page (and drops any request still on its way).
    effect(() => {
      const filter = this.filter();
      untracked(() => this.loadFirstPage(filter));
    });
    this.destroyRef.onDestroy(() => this.pending?.unsubscribe());
  }

  protected filter(): SpeciesFilter {
    return {
      generations: this.selectedGenerations(),
      types: this.selectedTypes(),
      primaryTypeOnly: this.primaryTypeOnly(),
      query: this.settledQuery(),
    };
  }

  protected filtered(): boolean {
    return isFiltered(this.filter());
  }

  private loadFirstPage(filter: SpeciesFilter): void {
    this.pending?.unsubscribe();
    this.loading.set(true);
    this.loadingMore.set(false);
    this.failure.set(null);
    this.pending = this.api.species(filter, { limit: PAGE_SIZE }).subscribe({
      next: (page) => {
        this.loading.set(false);
        this.items.set(page.items);
        this.next.set(page.next);
      },
      error: (e) => {
        this.loading.set(false);
        this.items.set([]);
        this.next.set(null);
        this.failure.set(problemText(toProblem(e)));
      },
    });
  }

  protected loadMore(): void {
    const cursor = this.next();
    if (!cursor || this.loadingMore()) return;
    this.loadingMore.set(true);
    this.pending = this.api.species(this.filter(), { limit: PAGE_SIZE, cursor }).subscribe({
      next: (page) => {
        this.loadingMore.set(false);
        this.items.update((all) => all.concat(page.items));
        this.next.set(page.next);
      },
      error: (e) => {
        this.loadingMore.set(false);
        this.failure.set(problemText(toProblem(e)));
      },
    });
  }

  /**
   * A chip tells us when it is chosen or let go. Only the person's own clicks count: when the screen itself changes a chip (the clear
   * button), the chip says so too, and answering that would undo the change.
   */
  protected chooseGeneration(number: number, event: MatChipSelectionChange): void {
    if (!event.isUserInput) return;
    this.selectedGenerations.update((s) => withValue(s, number, event.selected));
  }

  protected chooseType(type: string, event: MatChipSelectionChange): void {
    if (!event.isUserInput) return;
    this.selectedTypes.update((s) => withValue(s, type, event.selected));
  }

  protected clear(): void {
    this.selectedGenerations.set(new Set());
    this.selectedTypes.set(new Set());
    this.primaryTypeOnly.set(false);
    this.query.set('');
    this.settledQuery.set('');
  }

  protected icon(species: SpeciesSummary): string {
    return artUrl(species, undefined, true);
  }
}
