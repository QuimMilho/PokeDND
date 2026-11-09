import { Component, DestroyRef, LOCALE_ID, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatChipSelectionChange, MatChipsModule } from '@angular/material/chips';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';
import { problemText, toProblem } from '../core/api-error';
import { ExportApi, ExportJob, ExportRequest, isFinished, pdfName, saveBlob } from './export-api';
import { ReferenceApi } from './reference-api';
import { EMPTY_FILTER, matches, normaliseQuery, selectCards, toggled, withValue } from './species-filter';
import { CardEntry, GENERATIONS, SpeciesSummary, TYPES, cardsOf, typeColour, typeName } from './species';

/** The most printed cards (cards times copies) in one export (`cards.export.maxCardsPerJob` in the API). The API has the last word. */
export const MAX_PRINTED = 1500;

type SizeChoice = '63x88' | '70x120' | '44x63' | 'custom';

/**
 * The export screen (chapter 9.6, phase 27): choose cards by generation, by type or by hand, choose the print options, start the job,
 * watch it, download the PDF.
 */
@Component({
  selector: 'app-card-export',
  imports: [
    FormsModule,
    RouterLink,
    MatButtonModule,
    MatButtonToggleModule,
    MatChipsModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressBarModule,
    MatSlideToggleModule,
  ],
  templateUrl: './export.html',
  styleUrl: './export.scss',
})
export class CardExport {
  private readonly reference = inject(ReferenceApi);
  private readonly exports = inject(ExportApi);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly generations = GENERATIONS;
  protected readonly types = TYPES;
  protected readonly typeName = typeName;
  protected readonly typeColour = typeColour;
  protected readonly maxPrinted = MAX_PRINTED;

  // --- what to include
  protected readonly selectedGenerations = signal<ReadonlySet<number>>(new Set());
  protected readonly selectedTypes = signal<ReadonlySet<string>>(new Set());
  protected readonly primaryTypeOnly = signal(false);
  protected readonly includeForms = signal(true);
  /** Cards chosen by hand, by key. */
  protected readonly byHand = signal<ReadonlySet<string>>(new Set());
  protected readonly search = signal('');

  // --- options
  protected readonly size = signal<SizeChoice>('63x88');
  protected readonly customWidth = signal(63);
  protected readonly customHeight = signal(88);
  protected readonly paper = signal<'A4' | 'LETTER'>('A4');
  protected readonly bleed = signal(3);
  protected readonly gap = signal(2);
  protected readonly backs = signal(false);
  protected readonly language = signal<'en' | 'pt'>(inject(LOCALE_ID).startsWith('pt') ? 'pt' : 'en');
  protected readonly colour = signal<'full' | 'ink-saving'>('full');
  protected readonly copies = signal(1);

  // --- the list of all cards (to count them and to pick by hand)
  protected readonly allSpecies = signal<SpeciesSummary[] | null>(null);
  protected readonly listFailure = signal<string | null>(null);

  // --- the job
  protected readonly starting = signal(false);
  protected readonly job = signal<ExportJob | null>(null);
  protected readonly failure = signal<string | null>(null);
  protected readonly downloading = signal(false);
  private watching?: Subscription;

  /** The cards the selection gives, when the list is loaded (the API counts again when the job starts). */
  protected readonly selection = computed<CardEntry[] | null>(() => {
    const all = this.allSpecies();
    if (!all) return null;
    return selectCards(
      all,
      { generations: this.selectedGenerations(), types: this.selectedTypes(), primaryTypeOnly: this.primaryTypeOnly() },
      this.includeForms(),
      this.byHand(),
    );
  });
  protected readonly printed = computed(() => (this.selection()?.length ?? 0) * this.copies());
  protected readonly nothingChosen = computed(
    () => this.selectedGenerations().size === 0 && this.selectedTypes().size === 0 && this.byHand().size === 0,
  );

  /** Cards that match the search box, to add by hand. */
  protected readonly found = computed<CardEntry[]>(() => {
    const all = this.allSpecies();
    const q = normaliseQuery(this.search());
    if (!all || q.length < 2) return [];
    return all
      .filter((s) => matches(s, { ...EMPTY_FILTER, query: q }))
      .flatMap((s) => cardsOf(s, true))
      .slice(0, 30);
  });
  protected readonly chosenCards = computed<CardEntry[]>(() => {
    const keys = this.byHand();
    return (this.allSpecies() ?? []).flatMap((s) => cardsOf(s, true)).filter((c) => keys.has(c.key));
  });

  constructor() {
    this.reference
      .allSpecies(EMPTY_FILTER)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (all) => this.allSpecies.set(all),
        error: (e) => this.listFailure.set(problemText(toProblem(e))),
      });

    // "Export this card" on the card page comes here with ?card=0006_charizard
    const preset = this.route.snapshot.queryParamMap.getAll('card');
    if (preset.length) this.byHand.set(new Set(preset));

    this.destroyRef.onDestroy(() => this.watching?.unsubscribe());
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

  protected add(card: CardEntry): void {
    this.byHand.update((s) => new Set(s).add(card.key));
  }

  protected remove(key: string): void {
    this.byHand.update((s) => toggled(s, key));
  }

  protected cardLabel(card: CardEntry): string {
    const species = this.allSpecies()?.find((s) => s.id === card.species.id);
    const name = species?.name ?? card.species.id;
    return card.base ? name : `${name} (${card.form.slug})`;
  }

  protected selectAllGenerations(): void {
    this.selectedGenerations.set(new Set(GENERATIONS.map((g) => g.number)));
  }

  /** The request as the API wants it. */
  protected request(): ExportRequest {
    const sizeText = this.size() === 'custom' ? `${this.customWidth()}x${this.customHeight()}` : this.size();
    const request: ExportRequest = {
      includeForms: this.includeForms(),
      backs: this.backs(),
      size: sizeText,
      paper: this.paper(),
      language: this.language(),
      colour: this.colour(),
      copies: this.copies(),
      bleedMm: this.bleed(),
      gapMm: this.gap(),
    };
    if (this.selectedGenerations().size) request.generations = [...this.selectedGenerations()].sort((a, b) => a - b);
    if (this.selectedTypes().size) {
      request.types = [...this.selectedTypes()].sort();
      request.primaryTypeOnly = this.primaryTypeOnly();
    }
    if (this.byHand().size) request.cards = [...this.byHand()].sort();
    return request;
  }

  protected start(): void {
    if (this.starting()) return;
    this.failure.set(null);
    this.job.set(null);
    this.starting.set(true);
    this.exports.start(this.request()).subscribe({
      next: (job) => {
        this.starting.set(false);
        this.job.set(job);
        this.watch(job);
      },
      error: (e) => {
        this.starting.set(false);
        this.failure.set(problemText(toProblem(e)));
      },
    });
  }

  private watch(job: ExportJob): void {
    this.watching?.unsubscribe();
    this.watching = this.exports.watch(job.id).subscribe({
      next: (latest) => this.job.set(latest),
      error: (e) => this.failure.set(problemText(toProblem(e))),
    });
  }

  protected running(): boolean {
    const job = this.job();
    return job !== null && !isFinished(job);
  }

  protected percent(): number {
    const job = this.job();
    return job && job.total > 0 ? Math.round((100 * job.progress) / job.total) : 0;
  }

  protected download(): void {
    const job = this.job();
    if (!job || this.downloading()) return;
    this.downloading.set(true);
    this.exports.pdf(job).subscribe({
      next: (blob) => {
        this.downloading.set(false);
        saveBlob(blob, pdfName(job));
      },
      error: (e) => {
        this.downloading.set(false);
        this.failure.set(problemText(toProblem(e)));
      },
    });
  }
}
