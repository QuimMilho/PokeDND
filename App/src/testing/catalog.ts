/** What is wrong between the English and the Portuguese catalogue (`messages.xlf` and `messages.pt.xlf`). All lists are empty when they match. */
export interface CatalogDifferences {
  /** Texts that exist in English but not in Portuguese. */
  missingInPortuguese: string[];
  /** Texts that exist in Portuguese but not in English (left over from a text that was removed or renamed). */
  missingInEnglish: string[];
  /** Portuguese entries with an empty translation. */
  untranslated: string[];
  /** Entries whose placeholders (`{{name}}` in the template) differ between the two languages. */
  placeholderMismatch: string[];
}

interface Unit {
  sourcePlaceholders: string[];
  targetPlaceholders: string[];
  target: string;
}

function placeholders(parent: Element | undefined): string[] {
  return Array.from(parent?.getElementsByTagName('ph') ?? [])
    .map((p) => p.getAttribute('equiv') ?? '')
    .sort();
}

function readUnits(xlf: string): Map<string, Unit> {
  const doc = new DOMParser().parseFromString(xlf, 'application/xml');
  const out = new Map<string, Unit>();
  for (const unit of Array.from(doc.getElementsByTagName('unit'))) {
    const source = unit.getElementsByTagName('source')[0];
    const target = unit.getElementsByTagName('target')[0];
    out.set(unit.getAttribute('id') ?? '', {
      sourcePlaceholders: placeholders(source),
      targetPlaceholders: placeholders(target),
      target: target?.textContent?.trim() ?? '',
    });
  }
  return out;
}

/** Compares the two catalogues: the app must be complete in both languages (chapter 11.7). */
export function compareCatalogs(englishXlf: string, portugueseXlf: string): CatalogDifferences {
  const en = readUnits(englishXlf);
  const pt = readUnits(portugueseXlf);
  const result: CatalogDifferences = { missingInPortuguese: [], missingInEnglish: [], untranslated: [], placeholderMismatch: [] };
  for (const [id, unit] of en) {
    const other = pt.get(id);
    if (!other) {
      result.missingInPortuguese.push(id);
      continue;
    }
    if (!other.target) {
      result.untranslated.push(id);
    }
    if (JSON.stringify(unit.sourcePlaceholders) !== JSON.stringify(other.targetPlaceholders)) {
      result.placeholderMismatch.push(id);
    }
  }
  for (const id of pt.keys()) {
    if (!en.has(id)) {
      result.missingInEnglish.push(id);
    }
  }
  return result;
}
