import { Component, computed, input } from '@angular/core';
import { MoveInfo, SpeciesDetail } from './reference-api';
import { typeColour, typeName } from './species';

interface Line {
  label: string;
  value: number;
}

/**
 * How a species card will look (chapter 9.9: an app-native layout, not a copy of the print design): frame in the colour of the
 * primary type, art, name, stage, HP, the four signature moves, ability and weaknesses.
 */
@Component({
  selector: 'app-card-preview',
  template: `
    <article class="card" [style.--card-colour]="colour()" data-testid="card-preview">
      <header>
        <h2 data-testid="card-name">{{ title() }}</h2>
        <span class="hp"><span i18n="@@cardHp">HP</span> {{ species().baseStats['hp'] }}</span>
      </header>
      <p class="sub">
        <span class="stage">{{ stage() }}</span>
        <span class="dex">#{{ species().dex }}</span>
      </p>
      <div class="art">
        <img [src]="art()" [alt]="title()" width="300" height="300" />
      </div>
      <p class="types">
        @for (type of types(); track type) {
          <span class="type" [style.background]="colourOf(type)">{{ nameOf(type) }}</span>
        }
      </p>
      <ul class="stats" aria-label="Stats" i18n-aria-label="@@cardStatsLabel">
        @for (line of stats(); track line.label) {
          <li>
            <span class="label">{{ line.label }}</span>
            <strong>{{ line.value }}</strong>
          </li>
        }
      </ul>
      <section class="moves" aria-label="Moves" i18n-aria-label="@@cardMovesLabel">
        @for (move of moves(); track move.id) {
          <div class="move" data-testid="card-move">
            <span class="dot" [style.background]="colourOf(move.type)"></span>
            <span class="move-name">{{ move.name }}</span>
            <span class="move-power">{{ move.power ?? '—' }}</span>
          </div>
        } @empty {
          <p class="none" i18n="@@cardNoMoves">No signature moves are chosen for this card yet.</p>
        }
      </section>
      @if (abilities().length) {
        <p class="line"><strong i18n="@@cardAbility">Ability:</strong> {{ abilities().join(', ') }}</p>
      }
      @if (weaknesses().length) {
        <p class="line">
          <strong i18n="@@cardWeakness">Weak to:</strong>
          @for (w of weaknesses(); track w.type) {
            <span class="weak">{{ nameOf(w.type) }} ×{{ w.times }}</span>
          }
        </p>
      }
    </article>
  `,
  styles: `
    :host {
      display: block;
    }
    .card {
      --card-colour: #888;
      box-sizing: border-box;
      width: 100%;
      max-width: 360px;
      margin: 0 auto;
      padding: 14px;
      border: 8px solid var(--card-colour);
      border-radius: 18px;
      background: color-mix(in srgb, var(--card-colour) 14%, var(--mat-sys-surface));
      color: var(--mat-sys-on-surface);
      display: flex;
      flex-direction: column;
      gap: 8px;
    }
    header {
      display: flex;
      justify-content: space-between;
      align-items: baseline;
      gap: 8px;
    }
    h2 {
      margin: 0;
      font: var(--mat-sys-title-large);
    }
    .hp {
      font-weight: 700;
      white-space: nowrap;
    }
    .sub {
      margin: 0;
      display: flex;
      justify-content: space-between;
      font: var(--mat-sys-label-medium);
      opacity: 0.8;
    }
    .art {
      background: color-mix(in srgb, var(--card-colour) 30%, white);
      border-radius: 10px;
      aspect-ratio: 1;
      display: flex;
      align-items: center;
      justify-content: center;
    }
    .art img {
      max-width: 100%;
      height: auto;
      object-fit: contain;
    }
    .types {
      margin: 0;
      display: flex;
      gap: 6px;
    }
    .type {
      color: #fff;
      text-shadow: 0 1px 2px rgb(0 0 0 / 55%);
      border-radius: 12px;
      padding: 2px 10px;
      font: var(--mat-sys-label-medium);
    }
    .stats {
      list-style: none;
      margin: 0;
      padding: 0;
      display: grid;
      grid-template-columns: repeat(5, 1fr);
      text-align: center;
      font: var(--mat-sys-label-medium);
    }
    .stats .label {
      display: block;
      opacity: 0.75;
    }
    .moves {
      display: flex;
      flex-direction: column;
      gap: 4px;
    }
    .move {
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 4px 8px;
      border-radius: 8px;
      background: color-mix(in srgb, var(--card-colour) 18%, var(--mat-sys-surface));
    }
    .dot {
      width: 12px;
      height: 12px;
      border-radius: 50%;
      flex: none;
    }
    .move-name {
      flex: 1;
    }
    .move-power {
      font-weight: 700;
    }
    .line {
      margin: 0;
      font: var(--mat-sys-body-small);
    }
    .weak {
      margin-left: 6px;
    }
    .none {
      margin: 0;
      font: var(--mat-sys-body-small);
    }
  `,
})
export class CardPreview {
  readonly species = input.required<SpeciesDetail>();
  readonly formId = input.required<string>();
  readonly moves = input<MoveInfo[]>([]);
  readonly art = input.required<string>();

  private readonly form = computed(() => this.species().forms.find((f) => f.id === this.formId()));

  protected readonly types = computed(() => this.form()?.types ?? this.species().types);
  protected readonly colour = computed(() => typeColour(this.types()[0]));
  protected readonly colourOf = typeColour;
  protected readonly nameOf = typeName;

  protected readonly title = computed(() => {
    const text = this.form()?.text;
    return text ? `${this.species().name} (${text})` : this.species().name;
  });

  protected readonly stage = computed(() => {
    switch (this.species().derived?.stage) {
      case 'STAGE_1':
        return $localize`:@@stageOne:Stage 1`;
      case 'STAGE_2':
        return $localize`:@@stageTwo:Stage 2`;
      default:
        return $localize`:@@stageBasic:Basic`;
    }
  });

  protected readonly stats = computed<Line[]>(() => {
    const s = this.species().baseStats;
    return [
      { label: $localize`:@@statAttack:Atk`, value: s['attack'] },
      { label: $localize`:@@statDefense:Def`, value: s['defense'] },
      { label: $localize`:@@statSpAttack:SpA`, value: s['specialAttack'] },
      { label: $localize`:@@statSpDefense:SpD`, value: s['specialDefense'] },
      { label: $localize`:@@statSpeed:Spe`, value: s['speed'] },
    ];
  });

  protected readonly abilities = computed(() => {
    const f = this.form();
    const names = f?.abilities ?? this.species().abilities ?? [];
    const hidden = f?.hiddenAbility ?? this.species().hiddenAbility;
    return [...names, ...(hidden ? [hidden] : [])].map(prettyId);
  });

  protected readonly weaknesses = computed(() => {
    const w = this.form()?.weaknesses ?? this.species().derived?.weaknesses ?? {};
    return Object.entries(w)
      .map(([type, times]) => ({ type, times }))
      .sort((a, b) => b.times - a.times || a.type.localeCompare(b.type));
  });
}

/** `lightningrod` → `Lightningrod`: ability ids are not split into words, so only the first letter changes. */
function prettyId(id: string): string {
  return id.charAt(0).toUpperCase() + id.slice(1);
}
