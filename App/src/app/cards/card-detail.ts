import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { catchError, distinctUntilChanged, forkJoin, map, of, switchMap } from 'rxjs';
import { problemText, toProblem } from '../core/api-error';
import { CardPreview } from './card-preview';
import { MoveInfo, ReferenceApi, SpeciesDetail } from './reference-api';
import { CardEntry, artUrl, cardsOf } from './species';

/** One species with its card preview and the four signature moves (chapter 9.3 and 9.9, phase 26). */
@Component({
  selector: 'app-card-detail',
  imports: [RouterLink, MatButtonModule, MatChipsModule, MatProgressSpinnerModule, CardPreview],
  templateUrl: './card-detail.html',
  styleUrl: './card-detail.scss',
})
export class CardDetail {
  private readonly api = inject(ReferenceApi);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly species = signal<SpeciesDetail | null>(null);
  protected readonly loading = signal(true);
  protected readonly failure = signal<string | null>(null);
  protected readonly notFound = signal(false);

  /** The cards of this species: the base form and its printable alternate forms. */
  protected readonly cards = computed<CardEntry[]>(() => {
    const s = this.species();
    return s ? cardsOf(s, true) : [];
  });
  protected readonly chosenForm = signal<string | null>(null);
  protected readonly formId = computed(() => this.chosenForm() ?? this.cards()[0]?.form.id ?? '');
  protected readonly card = computed(() => this.cards().find((c) => c.form.id === this.formId()));

  protected readonly moves = signal<MoveInfo[]>([]);

  protected readonly art = computed(() => {
    const s = this.species();
    const form = this.card()?.form;
    return s ? artUrl(s, form) : '';
  });

  constructor() {
    this.route.paramMap
      .pipe(
        map((p) => p.get('id') ?? ''),
        distinctUntilChanged(),
        switchMap((id) => {
          this.loading.set(true);
          this.failure.set(null);
          this.notFound.set(false);
          this.chosenForm.set(null);
          return this.api.oneSpecies(id).pipe(
            catchError((e) => {
              const problem = toProblem(e);
              this.notFound.set(problem.status === 404);
              this.failure.set(problem.status === 404 ? null : problemText(problem));
              return of(null);
            }),
          );
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((species) => {
        this.species.set(species);
        this.loading.set(false);
      });

    // The moves of the shown form (one call each; a move that cannot be read is left out).
    toObservable(computed(() => ({ species: this.species(), formId: this.formId() })))
      .pipe(
        switchMap(({ species, formId }) => {
          const ids = species?.derived?.signature?.[formId] ?? [];
          if (!ids.length) return of([] as MoveInfo[]);
          return forkJoin(ids.map((id) => this.api.move(id).pipe(catchError(() => of(null))))).pipe(
            map((all) => all.filter((m): m is MoveInfo => m !== null)),
          );
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((moves) => this.moves.set(moves));
  }

  protected choose(formId: string): void {
    this.chosenForm.set(formId);
  }

  /** The label of a form button: its text ("Alola"), or "Normal" for the base form. */
  protected formLabel(card: CardEntry): string {
    const detail = this.species()?.forms.find((f) => f.id === card.form.id);
    return card.base ? $localize`:@@formBase:Normal` : detail?.text || card.form.slug;
  }
}
